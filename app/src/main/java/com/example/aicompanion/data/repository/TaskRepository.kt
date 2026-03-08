package com.example.aicompanion.data.repository

import com.example.aicompanion.data.local.dao.ActionItemDao
import com.example.aicompanion.data.local.dao.ProjectDao
import com.example.aicompanion.data.local.dao.SourceDao
import com.example.aicompanion.data.local.entity.ActionItem
import com.example.aicompanion.data.local.entity.Priority
import com.example.aicompanion.data.local.entity.Project
import com.example.aicompanion.data.local.entity.Source
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

    suspend fun deleteProject(id: Long) = projectDao.deleteById(id)

    suspend fun getAllProjectNames(): List<String> = projectDao.getAllProjectNames()

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

    fun getOverdueItems(): Flow<List<ActionItem>> =
        actionItemDao.getOverdueItems(System.currentTimeMillis())

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

    // --- AI helpers ---

    suspend fun getAllActiveItemTexts(): List<ActionItem> = actionItemDao.getAllActiveItemTexts()
}
