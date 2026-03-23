package com.example.aicompanion.data.sync

import android.util.Log
import com.example.aicompanion.data.local.dao.ActionItemDao
import com.example.aicompanion.data.local.dao.ProjectDao
import com.example.aicompanion.data.local.dao.SyncStateDao
import com.example.aicompanion.data.local.entity.ActionItem
import com.example.aicompanion.data.local.entity.Project
import com.example.aicompanion.data.local.entity.SyncState
import com.example.aicompanion.data.sync.SyncMappers.parseUpdatedTimestamp
import com.example.aicompanion.data.sync.SyncMappers.toActionItem
import com.example.aicompanion.data.sync.SyncMappers.toGoogleTask
import com.example.aicompanion.data.sync.SyncMappers.toGoogleTaskList
import com.example.aicompanion.data.sync.SyncMappers.toProject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

sealed class SyncStatus {
    data object Idle : SyncStatus()
    data object Syncing : SyncStatus()
    data class Success(val timestamp: Long) : SyncStatus()
    data class Error(val message: String, val timestamp: Long) : SyncStatus()
}

data class SyncStats(
    val listsCreated: Int = 0,
    val listsUpdated: Int = 0,
    val tasksPulled: Int = 0,
    val tasksPushed: Int = 0,
    val conflicts: Int = 0,
    val errors: Int = 0
)

class SyncEngine(
    private val apiClient: GoogleTasksApiClient,
    private val actionItemDao: ActionItemDao,
    private val projectDao: ProjectDao,
    private val syncStateDao: SyncStateDao,
    private val tokenManager: TokenManager
) {
    companion object {
        private const val TAG = "SyncEngine"
        private const val INBOX_LIST_TITLE = "AI Companion Inbox"
        private const val MIN_SYNC_INTERVAL_MS = 60_000L  // 60 seconds debounce
    }

    private val mutex = Mutex()
    private val _status = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
    val status: StateFlow<SyncStatus> = _status.asStateFlow()

    private var lastSyncAttempt = 0L

    private val rfc3339Full = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    /**
     * Main sync entry point. Debounced and mutex-guarded.
     * Returns true if sync completed, false if skipped or failed.
     */
    suspend fun sync(): Boolean {
        // Debounce
        val now = System.currentTimeMillis()
        if (now - lastSyncAttempt < MIN_SYNC_INTERVAL_MS) {
            Log.d(TAG, "Sync skipped — too soon since last attempt")
            return false
        }

        // Check if sync is enabled
        val state = syncStateDao.get() ?: SyncState()
        if (!state.syncEnabled) {
            Log.d(TAG, "Sync skipped — not enabled")
            return false
        }

        // Restore account from DB if not set in memory (e.g. after app restart)
        if (tokenManager.getAccount() == null) {
            val savedEmail = state.googleAccountEmail
            if (savedEmail != null) {
                tokenManager.setAccount(savedEmail)
                Log.i(TAG, "Restored sync account from DB: $savedEmail")
            } else {
                Log.d(TAG, "Sync skipped — no account")
                return false
            }
        }

        return mutex.withLock {
            lastSyncAttempt = System.currentTimeMillis()
            _status.value = SyncStatus.Syncing
            try {
                val stats = doSync(state)
                val finishTime = System.currentTimeMillis()
                _status.value = SyncStatus.Success(finishTime)
                Log.i(TAG, "Sync completed: $stats")
                true
            } catch (e: GoogleTasksApiException) {
                val msg = "API error ${e.code}: ${e.body.take(200)}"
                Log.e(TAG, msg, e)
                _status.value = SyncStatus.Error(msg, System.currentTimeMillis())
                false
            } catch (e: Exception) {
                val msg = e.message ?: "Unknown sync error"
                Log.e(TAG, msg, e)
                _status.value = SyncStatus.Error(msg, System.currentTimeMillis())
                false
            }
        }
    }

    private suspend fun doSync(state: SyncState): SyncStats {
        var stats = SyncStats()
        val lastSync = state.lastSyncTimestamp
        val lastVersion = state.lastSyncedVersion

        // Compute updatedMin for incremental pull
        val updatedMin = lastSync?.let { rfc3339Full.format(Date(it)) }

        // === PULL PHASE ===

        // 1. Pull task lists
        val remoteLists = apiClient.listTaskLists()
        val remoteListIds = remoteLists.map { it.id }.toSet()

        // Ensure inbox list exists
        var inboxListId = state.inboxTaskListId
        val existingInboxList = remoteLists.find { it.title == INBOX_LIST_TITLE }
        if (existingInboxList != null) {
            inboxListId = existingInboxList.id
        } else if (inboxListId == null) {
            // Create inbox list
            val created = apiClient.createTaskList(INBOX_LIST_TITLE)
            inboxListId = created.id
            stats = stats.copy(listsCreated = stats.listsCreated + 1)
        }

        // Match remote lists to local projects
        for (remoteList in remoteLists) {
            if (remoteList.id == inboxListId) continue  // Skip inbox list for project matching

            val localProject = projectDao.getByGoogleTaskListId(remoteList.id)
            if (localProject != null) {
                // Update local project name if remote changed
                if (localProject.name != remoteList.title) {
                    projectDao.update(localProject.copy(name = remoteList.title))
                    stats = stats.copy(listsUpdated = stats.listsUpdated + 1)
                }
            } else {
                // New remote list — create local project
                val newProject = remoteList.toProject()
                projectDao.insert(newProject)
                stats = stats.copy(listsCreated = stats.listsCreated + 1)
            }
        }

        // Check for locally synced projects that were deleted remotely
        val syncedProjects = projectDao.getSyncedProjects()
        for (project in syncedProjects) {
            val remoteId = project.googleTaskListId ?: continue
            if (remoteId !in remoteListIds && remoteId != inboxListId) {
                // Deleted remotely — trash locally
                projectDao.trashById(project.id)
                actionItemDao.trashByProjectId(project.id)
            }
        }

        // 2. Pull tasks for each list
        val allListIds = buildList {
            inboxListId?.let { add(it to null as Long?) }  // Inbox → projectId null
            for (remoteList in remoteLists) {
                if (remoteList.id == inboxListId) continue
                val localProject = projectDao.getByGoogleTaskListId(remoteList.id)
                if (localProject != null) {
                    add(remoteList.id to localProject.id)
                }
            }
        }

        for ((listId, projectId) in allListIds) {
            val remoteTasks = apiClient.listTasks(
                taskListId = listId,
                updatedMin = updatedMin,
                showDeleted = true,
                showCompleted = true,
                showHidden = true
            )

            for (remoteTask in remoteTasks) {
                val remoteTaskId = remoteTask.id ?: continue
                val localItem = actionItemDao.getByGoogleTaskId(remoteTaskId)

                if (localItem != null) {
                    // Existing item — check for conflict
                    if (localItem.syncVersion > lastVersion) {
                        // Both sides changed — conflict
                        val remoteUpdated = parseUpdatedTimestamp(remoteTask.updated)
                        if (remoteUpdated > localItem.updatedAt) {
                            // Remote wins
                            val updated = remoteTask.toActionItem(projectId, listId, localItem)
                            actionItemDao.update(updated)
                            stats = stats.copy(conflicts = stats.conflicts + 1, tasksPulled = stats.tasksPulled + 1)
                        } else {
                            // Local wins — will be pushed in push phase
                            stats = stats.copy(conflicts = stats.conflicts + 1)
                        }
                    } else {
                        // Only remote changed — update local
                        if (remoteTask.deleted) {
                            actionItemDao.trashItem(localItem.id)
                        } else {
                            val updated = remoteTask.toActionItem(projectId, listId, localItem)
                            actionItemDao.update(updated)
                        }
                        stats = stats.copy(tasksPulled = stats.tasksPulled + 1)
                    }
                } else if (!remoteTask.deleted) {
                    // New remote task — create locally
                    val newItem = remoteTask.toActionItem(projectId, listId)
                    actionItemDao.insert(newItem)
                    stats = stats.copy(tasksPulled = stats.tasksPulled + 1)
                }
            }
        }

        // === PUSH PHASE ===

        // 3. Push dirty projects
        val dirtyProjects = projectDao.getDirtyProjects(lastVersion)
        for (project in dirtyProjects) {
            if (project.isArchived) continue  // Skip archived projects

            try {
                if (project.isTrashed && project.googleTaskListId != null) {
                    // Deleted locally — delete remote
                    apiClient.deleteTaskList(project.googleTaskListId)
                } else if (project.googleTaskListId != null) {
                    // Update remote
                    apiClient.updateTaskList(project.googleTaskListId, project.name)
                    stats = stats.copy(listsUpdated = stats.listsUpdated + 1)
                } else if (!project.isTrashed) {
                    // New project — create remote
                    val created = apiClient.createTaskList(project.name)
                    projectDao.updateGoogleTaskListId(project.id, created.id)
                    stats = stats.copy(listsCreated = stats.listsCreated + 1)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error pushing project ${project.id}: ${e.message}")
                stats = stats.copy(errors = stats.errors + 1)
            }
        }

        // 4. Push dirty tasks
        val dirtyTasks = actionItemDao.getDirtyItems(lastVersion)
        for (task in dirtyTasks) {
            try {
                val targetListId = resolveTaskListId(task, inboxListId)
                    ?: continue  // Can't determine list, skip

                if (task.isTrashed && task.googleTaskId != null && task.googleTaskListId != null) {
                    // Deleted locally — delete remote
                    apiClient.deleteTask(task.googleTaskListId, task.googleTaskId)
                } else if (task.googleTaskId != null && task.googleTaskListId != null) {
                    if (task.googleTaskListId != targetListId) {
                        // Project changed — move: delete from old list, create in new
                        apiClient.deleteTask(task.googleTaskListId, task.googleTaskId)
                        val created = apiClient.createTask(targetListId, task.toGoogleTask())
                        actionItemDao.updateGoogleIds(task.id, created.id!!, targetListId)
                    } else {
                        // Same list — update
                        apiClient.updateTask(targetListId, task.googleTaskId, task.toGoogleTask())
                    }
                    stats = stats.copy(tasksPushed = stats.tasksPushed + 1)
                } else if (!task.isTrashed) {
                    // New task — create remote
                    val created = apiClient.createTask(targetListId, task.toGoogleTask())
                    actionItemDao.updateGoogleIds(task.id, created.id!!, targetListId)
                    stats = stats.copy(tasksPushed = stats.tasksPushed + 1)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error pushing task ${task.id}: ${e.message}")
                stats = stats.copy(errors = stats.errors + 1)
            }
        }

        // === UPDATE SYNC STATE ===
        val newVersion = System.currentTimeMillis()
        syncStateDao.upsert(
            state.copy(
                lastSyncTimestamp = System.currentTimeMillis(),
                lastSyncedVersion = newVersion,
                inboxTaskListId = inboxListId
            )
        )

        return stats
    }

    /**
     * Determines the Google Tasks list ID for a given action item.
     * Inbox tasks go to the inbox list; project tasks go to the project's list.
     */
    private suspend fun resolveTaskListId(task: ActionItem, inboxListId: String?): String? {
        if (task.projectId == null) {
            return inboxListId
        }
        return projectDao.getByIdSync(task.projectId)?.googleTaskListId
    }

    /**
     * Performs the initial sync merge when sync is first enabled.
     * Matches projects by name, creates unmatched items on both sides.
     */
    suspend fun initialSync() {
        mutex.withLock {
            _status.value = SyncStatus.Syncing
            try {
                doInitialSync()
                _status.value = SyncStatus.Success(System.currentTimeMillis())
            } catch (e: Exception) {
                Log.e(TAG, "Initial sync failed: ${e.message}", e)
                _status.value = SyncStatus.Error(
                    e.message ?: "Initial sync failed",
                    System.currentTimeMillis()
                )
                throw e
            }
        }
    }

    private suspend fun doInitialSync() {
        val state = syncStateDao.get() ?: SyncState()

        // 1. Pull remote task lists
        val remoteLists = apiClient.listTaskLists()

        // 2. Ensure inbox list exists
        var inboxListId = remoteLists.find { it.title == INBOX_LIST_TITLE }?.id
        if (inboxListId == null) {
            val created = apiClient.createTaskList(INBOX_LIST_TITLE)
            inboxListId = created.id
        }

        // 3. Match local projects to remote lists by name (case-insensitive)
        val localProjects = projectDao.getAllNonTrashed()
        val remoteListsByName = remoteLists
            .filter { it.id != inboxListId }
            .associateBy { it.title.lowercase() }
        val matchedRemoteIds = mutableSetOf<String>()

        for (project in localProjects) {
            val matchingRemote = remoteListsByName[project.name.lowercase()]
            if (matchingRemote != null) {
                // Match found — link them
                projectDao.updateGoogleTaskListId(project.id, matchingRemote.id)
                matchedRemoteIds.add(matchingRemote.id)
            } else {
                // No remote match — push local project
                val created = apiClient.createTaskList(project.name)
                projectDao.updateGoogleTaskListId(project.id, created.id)
            }
        }

        // 4. Create local projects for unmatched remote lists
        for (remoteList in remoteLists) {
            if (remoteList.id == inboxListId) continue
            if (remoteList.id in matchedRemoteIds) continue
            val newProject = remoteList.toProject()
            projectDao.insert(newProject)
        }

        // 5. Sync tasks for each list
        // Refresh projects after creates
        val allProjects = projectDao.getAllNonTrashed()
        val projectByListId = allProjects.filter { it.googleTaskListId != null }
            .associateBy { it.googleTaskListId!! }

        // Build list of (listId, projectId?) pairs
        val listMappings = buildList {
            add(inboxListId to null as Long?)
            for ((listId, project) in projectByListId) {
                add(listId to project.id)
            }
        }

        for ((listId, projectId) in listMappings) {
            // Pull remote tasks
            val remoteTasks = apiClient.listTasks(
                taskListId = listId,
                showDeleted = false,
                showCompleted = true,
                showHidden = true
            )

            // Get local tasks for this project/inbox
            val localTasks = if (projectId != null) {
                actionItemDao.getAllNonTrashed().filter { it.projectId == projectId }
            } else {
                actionItemDao.getAllNonTrashed().filter { it.projectId == null }
            }

            val remoteByTitle = remoteTasks.associateBy { it.title.lowercase().trim() }
            val matchedRemoteTaskIds = mutableSetOf<String>()

            // Match local tasks to remote by title
            for (localTask in localTasks) {
                val matchingRemote = remoteByTitle[localTask.text.lowercase().trim()]
                if (matchingRemote?.id != null) {
                    // Link them
                    actionItemDao.updateGoogleIds(localTask.id, matchingRemote.id, listId)
                    matchedRemoteTaskIds.add(matchingRemote.id)
                } else {
                    // Push local task to remote
                    val created = apiClient.createTask(listId, localTask.toGoogleTask())
                    if (created.id != null) {
                        actionItemDao.updateGoogleIds(localTask.id, created.id, listId)
                    }
                }
            }

            // Create local tasks for unmatched remote tasks
            for (remoteTask in remoteTasks) {
                val remoteId = remoteTask.id ?: continue
                if (remoteId in matchedRemoteTaskIds) continue
                val newItem = remoteTask.toActionItem(projectId, listId)
                actionItemDao.insert(newItem)
            }
        }

        // 6. Save sync state
        val now = System.currentTimeMillis()
        syncStateDao.upsert(
            SyncState(
                lastSyncTimestamp = now,
                lastSyncedVersion = now,
                inboxTaskListId = inboxListId,
                syncEnabled = true,
                googleAccountEmail = tokenManager.getAccount()
            )
        )
    }
}
