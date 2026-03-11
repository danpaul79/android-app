package com.example.aicompanion.data.repository

import com.example.aicompanion.data.local.dao.ActionItemDao
import com.example.aicompanion.data.local.dao.ProjectDao
import com.example.aicompanion.data.local.dao.SourceDao
import com.example.aicompanion.data.local.dao.SyncStateDao
import com.example.aicompanion.data.local.entity.ActionItem
import com.example.aicompanion.data.local.entity.Priority
import com.example.aicompanion.data.local.entity.Project
import com.example.aicompanion.data.local.entity.Source
import com.example.aicompanion.data.export.ExportData
import com.example.aicompanion.network.GeminiClient
import kotlinx.coroutines.flow.Flow
import java.util.Calendar
import java.util.concurrent.atomic.AtomicLong

class TaskRepository(
    private val actionItemDao: ActionItemDao,
    private val projectDao: ProjectDao,
    private val sourceDao: SourceDao,
    private val syncStateDao: SyncStateDao,
    private val geminiClient: GeminiClient = GeminiClient()
) {
    private val syncVersionCounter = AtomicLong(System.currentTimeMillis())

    private fun nextSyncVersion(): Long = syncVersionCounter.incrementAndGet()

    private suspend fun markTaskDirty(id: Long) {
        actionItemDao.updateSyncVersion(id, nextSyncVersion())
    }

    private suspend fun markProjectDirty(id: Long) {
        projectDao.updateSyncVersion(id, nextSyncVersion())
    }
    // --- Projects ---

    fun getAllProjects(): Flow<List<Project>> = projectDao.getAll()

    fun getProjectById(id: Long): Flow<Project?> = projectDao.getById(id)

    suspend fun createProject(name: String, color: Int = Project.DEFAULT_COLOR, icon: String = "folder"): Long {
        val id = projectDao.insert(Project(name = name, color = color, icon = icon, syncVersion = nextSyncVersion()))
        return id
    }

    suspend fun updateProject(project: Project) {
        projectDao.update(project)
        markProjectDirty(project.id)
    }

    suspend fun archiveProject(id: Long) {
        projectDao.archive(id)
        markProjectDirty(id)
    }

    suspend fun trashProject(id: Long) {
        projectDao.trashById(id)
        markProjectDirty(id)
        actionItemDao.trashByProjectId(id)
        actionItemDao.updateSyncVersionByProjectId(id, nextSyncVersion())
    }

    suspend fun restoreProject(id: Long) {
        projectDao.restoreById(id)
        markProjectDirty(id)
        actionItemDao.restoreByProjectId(id)
    }

    suspend fun deleteProject(id: Long) = projectDao.deleteById(id)

    suspend fun getAllProjectNames(): List<String> = projectDao.getAllProjectNames()

    fun getTrashedProjects(): Flow<List<Project>> = projectDao.getTrashed()

    // --- Action Items ---

    fun getItemById(id: Long): Flow<ActionItem?> = actionItemDao.getById(id)

    fun getInboxItems(): Flow<List<ActionItem>> = actionItemDao.getInboxItems()

    fun getInboxCount(): Flow<Int> = actionItemDao.getInboxCount()

    fun getActiveItemsByProject(projectId: Long): Flow<List<ActionItem>> =
        actionItemDao.getActiveByProjectId(projectId)

    fun getAllItemsByProject(projectId: Long): Flow<List<ActionItem>> =
        actionItemDao.getAllByProjectId(projectId)

    fun getActiveCountByProject(projectId: Long): Flow<Int> =
        actionItemDao.getActiveCountByProjectId(projectId)

    fun getOverdueItems(): Flow<List<ActionItem>> {
        val dayStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        return actionItemDao.getOverdueItems(dayStart)
    }

    fun getTodayItems(): Flow<List<ActionItem>> {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val dayStart = cal.timeInMillis
        cal.add(Calendar.DAY_OF_YEAR, 1)
        val dayEnd = cal.timeInMillis
        return actionItemDao.getTodayItems(dayStart, dayEnd)
    }

    fun getUpcomingItems(): Flow<List<ActionItem>> {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_YEAR, 1)
        }
        val start = cal.timeInMillis
        cal.add(Calendar.DAY_OF_YEAR, 7)
        val end = cal.timeInMillis
        return actionItemDao.getUpcomingItems(start, end)
    }

    fun getTrashedTasks(): Flow<List<ActionItem>> = actionItemDao.getTrashedItems()

    suspend fun toggleCompleted(id: Long, completed: Boolean) {
        val completedAt = if (completed) System.currentTimeMillis() else null
        actionItemDao.setCompleted(id, completed, completedAt)
        markTaskDirty(id)
    }

    suspend fun assignToProject(itemId: Long, projectId: Long?) {
        actionItemDao.assignToProject(itemId, projectId)
        markTaskDirty(itemId)
    }

    suspend fun createTask(
        text: String,
        projectId: Long? = null,
        dueDate: Long? = null,
        priority: Priority = Priority.NONE,
        notes: String? = null
    ): Long {
        return actionItemDao.insert(
            ActionItem(
                text = text,
                projectId = projectId,
                dueDate = dueDate,
                priority = priority,
                notes = notes,
                syncVersion = nextSyncVersion()
            )
        )
    }

    suspend fun updateTask(item: ActionItem) {
        actionItemDao.update(item.copy(updatedAt = System.currentTimeMillis(), syncVersion = nextSyncVersion()))
    }

    suspend fun updateTaskText(id: Long, text: String) {
        actionItemDao.updateText(id, text)
        markTaskDirty(id)
    }

    suspend fun setDueDate(id: Long, dueDate: Long?) {
        actionItemDao.setDueDate(id, dueDate)
        markTaskDirty(id)
    }

    suspend fun setDropDeadDate(id: Long, dropDeadDate: Long?) {
        actionItemDao.setDropDeadDate(id, dropDeadDate)
        markTaskDirty(id)
    }

    suspend fun setEstimatedMinutes(id: Long, minutes: Int) {
        actionItemDao.setEstimatedMinutes(id, minutes)
        markTaskDirty(id)
    }

    // --- AI Enrichment ---

    data class EnrichmentProgress(val processed: Int, val total: Int, val enriched: Int)

    /**
     * Runs AI enrichment on tasks that are missing effort estimates or context tags.
     * Processes in batches of 10 to stay within API limits.
     * Non-destructive: only sets estimatedMinutes if currently 0; only appends tags not already present.
     * Reports progress via the [onProgress] callback.
     */
    suspend fun enrichUnenrichedTasks(onProgress: (EnrichmentProgress) -> Unit): EnrichmentProgress {
        val allActive = actionItemDao.getAllActiveItemTexts()
        val needsEnrichment = allActive.filter { task ->
            task.estimatedMinutes == 0 || !task.notes.orEmpty().contains("#")
        }

        val total = needsEnrichment.size
        var processed = 0
        var enriched = 0

        needsEnrichment.chunked(10).forEach { batch ->
            val pairs = batch.map { it.id to it.text }
            val result = geminiClient.enrichTasks(pairs)
            result.onSuccess { enrichments ->
                enrichments.forEach { enrichment ->
                    val task = batch.find { it.id == enrichment.id } ?: return@forEach
                    // Set effort if unestimated
                    if (task.estimatedMinutes == 0 && enrichment.estimatedMinutes > 0) {
                        actionItemDao.setEstimatedMinutes(task.id, enrichment.estimatedMinutes)
                    }
                    // Append new tags to notes (only tags not already present)
                    if (enrichment.tags.isNotEmpty()) {
                        val existingNotes = task.notes.orEmpty()
                        val existingTags = Regex("#(\\w+(-\\w+)*)").findAll(existingNotes)
                            .map { it.groupValues[1].lowercase() }.toSet()
                        val newTags = enrichment.tags.filter { it.lowercase() !in existingTags }
                        if (newTags.isNotEmpty()) {
                            val tagStr = newTags.joinToString(" ") { "#$it" }
                            val updatedNotes = if (existingNotes.isBlank()) tagStr
                            else "$existingNotes\n$tagStr"
                            actionItemDao.update(task.copy(
                                notes = updatedNotes,
                                updatedAt = System.currentTimeMillis(),
                                syncVersion = nextSyncVersion()
                            ))
                        }
                    }
                    enriched++
                }
            }
            processed += batch.size
            onProgress(EnrichmentProgress(processed, total, enriched))
        }

        return EnrichmentProgress(processed, total, enriched)
    }

    suspend fun countUnenrichedTasks(): Int {
        val allActive = actionItemDao.getAllActiveItemTexts()
        return allActive.count { task ->
            task.estimatedMinutes == 0 || !task.notes.orEmpty().contains("#")
        }
    }

    // --- Scheduling ---

    suspend fun getActiveItemsForScheduling(): List<ActionItem> {
        // Drop-dead dates only sort to the top if within 14 days; further out = ranked by priority like normal
        val urgentThreshold = System.currentTimeMillis() + 14L * 24 * 60 * 60 * 1000
        return actionItemDao.getActiveItemsForScheduling(urgentThreshold)
    }

    suspend fun getStaleItems(staleDaysThreshold: Int = 14, limit: Int = 3): List<ActionItem> {
        val threshold = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -staleDaysThreshold)
        }.timeInMillis
        return actionItemDao.getStaleItems(threshold, limit)
    }

    /**
     * Given a capacity in minutes, returns a prioritized list of tasks that fit.
     * Hard drop-dead dates take precedence. #waiting-for tasks are excluded.
     * Unestimated tasks are treated as 30 min.
     *
     * @param contextTag if non-null, only tasks with this #tag are included plus untagged tasks
     *                   (untagged tasks are always eligible — they have no context restriction).
     */
    suspend fun pickTasksForCapacity(capacityMinutes: Int, contextTag: String? = null): List<ActionItem> {
        val urgentThreshold = System.currentTimeMillis() + 14L * 24 * 60 * 60 * 1000
        val allTasks = actionItemDao.getActiveItemsForScheduling(urgentThreshold)
        val result = mutableListOf<ActionItem>()
        var remaining = capacityMinutes

        for (task in allTasks) {
            val notes = task.notes ?: ""
            // Skip waiting-for tasks
            if (notes.contains("#waiting-for", ignoreCase = true)) continue
            // Context filter: if a tag is requested, only include tasks that have that tag
            // OR tasks that have no tags at all (untagged tasks are context-agnostic)
            if (contextTag != null) {
                val hasTags = Regex("#[\\w-]+").containsMatchIn(notes)
                val hasContextTag = notes.contains("#$contextTag", ignoreCase = true)
                if (hasTags && !hasContextTag) continue
            }
            val estimate = if (task.estimatedMinutes > 0) task.estimatedMinutes else 30
            if (estimate <= remaining) {
                result.add(task)
                remaining -= estimate
            }
            if (remaining <= 0) break
        }
        return result
    }

    suspend fun trashTask(id: Long) {
        actionItemDao.trashItem(id)
        markTaskDirty(id)
    }

    suspend fun restoreTask(id: Long) {
        actionItemDao.restoreItem(id)
        markTaskDirty(id)
    }

    suspend fun deleteTask(id: Long) = actionItemDao.deleteById(id)

    // --- Sources ---

    suspend fun saveFromSource(source: Source, items: List<ActionItem>): Long {
        val sourceId = sourceDao.insert(source)
        val itemsWithSource = items.map { it.copy(sourceId = sourceId) }
        actionItemDao.insertAll(itemsWithSource)
        return sourceId
    }

    fun getSourceForItem(actionItemId: Long): Flow<Source?> =
        sourceDao.getByActionItemId(actionItemId)

    // --- Reminders ---

    suspend fun getUpcomingUnfiredItems(windowStart: Long, windowEnd: Long): List<ActionItem> =
        actionItemDao.getUpcomingUnfired(windowStart, windowEnd)

    suspend fun markReminderFired(id: Long) = actionItemDao.markReminderFired(id)

    // --- Completed ---

    fun getRecentlyCompleted(): Flow<List<ActionItem>> {
        val sevenDaysAgo = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -7)
        }.timeInMillis
        return actionItemDao.getRecentlyCompleted(sevenDaysAgo)
    }

    // --- Search ---

    fun searchItems(query: String): Flow<List<ActionItem>> = actionItemDao.searchItems(query)

    // --- AI helpers ---

    suspend fun getAllActiveItemTexts(): List<ActionItem> = actionItemDao.getAllActiveItemTexts()

    suspend fun getAllProjectNamesWithIds(): List<Pair<String, Long>> =
        projectDao.getAllProjectNamesWithIds().map { it.name to it.id }

    // --- Export / Import ---

    suspend fun exportData(): ExportData {
        val projects = projectDao.getAllNonTrashed()
        val tasks = actionItemDao.getAllNonTrashed()
        return ExportData(projects = projects, tasks = tasks)
    }

    suspend fun importData(data: ExportData): ImportResult {
        // Map old project IDs to new ones
        val projectIdMap = mutableMapOf<Long, Long>()
        var projectsImported = 0
        var tasksImported = 0
        var projectsSkipped = 0

        // Get existing project names to avoid duplicates
        val existingNames = projectDao.getAllProjectNames().map { it.lowercase() }.toSet()

        for (project in data.projects) {
            if (project.name.lowercase() in existingNames) {
                // Find existing project ID by name to map tasks correctly
                // We'll skip inserting but still need the mapping
                projectsSkipped++
                continue
            }
            val newId = projectDao.insert(project.copy(id = 0))
            projectIdMap[project.id] = newId
            projectsImported++
        }

        // For skipped projects, resolve name -> existing ID
        val existingProjects = projectDao.getAllNonTrashed()
        val nameToExistingId = existingProjects.associate { it.name.lowercase() to it.id }
        for (project in data.projects) {
            if (project.id !in projectIdMap) {
                nameToExistingId[project.name.lowercase()]?.let { existingId ->
                    projectIdMap[project.id] = existingId
                }
            }
        }

        for (task in data.tasks) {
            val remappedProjectId = task.projectId?.let { projectIdMap[it] }
            actionItemDao.insert(
                task.copy(
                    id = 0,
                    sourceId = null,
                    projectId = remappedProjectId
                )
            )
            tasksImported++
        }

        return ImportResult(projectsImported, projectsSkipped, tasksImported)
    }
}

data class ImportResult(
    val projectsImported: Int,
    val projectsSkipped: Int,
    val tasksImported: Int
)
