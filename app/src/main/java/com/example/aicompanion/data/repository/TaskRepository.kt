package com.example.aicompanion.data.repository

import com.example.aicompanion.data.local.dao.ActionItemDao
import com.example.aicompanion.data.local.dao.ProjectDao
import com.example.aicompanion.data.local.dao.SourceDao
import com.example.aicompanion.data.local.entity.ActionItem
import com.example.aicompanion.data.local.entity.Priority
import com.example.aicompanion.data.local.entity.Project
import com.example.aicompanion.data.local.entity.Source
import com.example.aicompanion.data.export.ExportData
import kotlinx.coroutines.flow.Flow
import java.util.Calendar

class TaskRepository(
    private val actionItemDao: ActionItemDao,
    private val projectDao: ProjectDao,
    private val sourceDao: SourceDao
) {
    // --- Projects ---

    fun getAllProjects(): Flow<List<Project>> = projectDao.getAll()

    fun getProjectById(id: Long): Flow<Project?> = projectDao.getById(id)

    suspend fun createProject(name: String, color: Int = Project.DEFAULT_COLOR, icon: String = "folder"): Long =
        projectDao.insert(Project(name = name, color = color, icon = icon))

    suspend fun updateProject(project: Project) = projectDao.update(project)

    suspend fun archiveProject(id: Long) = projectDao.archive(id)

    suspend fun trashProject(id: Long) = projectDao.trashById(id)

    suspend fun restoreProject(id: Long) = projectDao.restoreById(id)

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
    }

    suspend fun assignToProject(itemId: Long, projectId: Long?) {
        actionItemDao.assignToProject(itemId, projectId)
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
                notes = notes
            )
        )
    }

    suspend fun updateTask(item: ActionItem) {
        actionItemDao.update(item.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun updateTaskText(id: Long, text: String) = actionItemDao.updateText(id, text)

    suspend fun setDueDate(id: Long, dueDate: Long?) = actionItemDao.setDueDate(id, dueDate)

    suspend fun trashTask(id: Long) = actionItemDao.trashItem(id)

    suspend fun restoreTask(id: Long) = actionItemDao.restoreItem(id)

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
