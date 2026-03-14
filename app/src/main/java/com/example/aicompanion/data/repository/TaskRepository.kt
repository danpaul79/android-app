package com.example.aicompanion.data.repository

import android.content.Context
import androidx.glance.appwidget.updateAll
import com.example.aicompanion.data.local.dao.ActionItemDao
import com.example.aicompanion.data.local.dao.ProjectDao
import com.example.aicompanion.data.local.dao.SourceDao
import com.example.aicompanion.data.local.dao.SyncStateDao
import com.example.aicompanion.data.local.dao.TaskEventDao
import com.example.aicompanion.ui.triage.TriageCategory
import com.example.aicompanion.ui.triage.TriageItem
import com.example.aicompanion.data.local.entity.ActionItem
import com.example.aicompanion.data.local.entity.Priority
import com.example.aicompanion.data.local.entity.Project
import com.example.aicompanion.data.local.entity.TaskEvent
import com.example.aicompanion.data.local.entity.RecurrenceRule
import com.example.aicompanion.data.local.entity.computeNextDueDate
import com.example.aicompanion.data.local.entity.effectivePriority
import com.example.aicompanion.data.local.entity.parsedTags
import com.example.aicompanion.data.local.entity.Source
import com.example.aicompanion.data.export.ExportData
import com.example.aicompanion.network.GeminiClient
import com.example.aicompanion.reminder.MorningPlanStore
import com.example.aicompanion.widget.TodayPlanWidget
import kotlinx.coroutines.flow.Flow
import java.util.Calendar
import java.util.concurrent.atomic.AtomicLong

class TaskRepository(
    private val actionItemDao: ActionItemDao,
    private val projectDao: ProjectDao,
    private val sourceDao: SourceDao,
    private val syncStateDao: SyncStateDao,
    private val geminiClient: GeminiClient = GeminiClient(),
    private val context: Context? = null,
    private val taskEventDao: TaskEventDao? = null
) {
    private val syncVersionCounter = AtomicLong(System.currentTimeMillis())
    private val planStore by lazy { context?.let { MorningPlanStore(it) } }

    private suspend fun refreshWidget() {
        context?.let { TodayPlanWidget().updateAll(it) }
    }

    private fun nextSyncVersion(): Long = syncVersionCounter.incrementAndGet()

    private suspend fun markTaskDirty(id: Long) {
        actionItemDao.updateSyncVersion(id, nextSyncVersion())
    }

    private suspend fun markProjectDirty(id: Long) {
        projectDao.updateSyncVersion(id, nextSyncVersion())
    }

    private suspend fun recordEvent(item: ActionItem, eventType: String, metadata: String? = null) {
        taskEventDao?.insert(TaskEvent(
            taskId = item.id,
            eventType = eventType,
            projectId = item.projectId,
            tags = item.parsedTags().takeIf { it.isNotEmpty() }?.joinToString(","),
            estimatedMinutes = item.estimatedMinutes,
            metadata = metadata
        ))
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
        val item = actionItemDao.getByIdSync(id)
        val completedAt = if (completed) System.currentTimeMillis() else null
        actionItemDao.setCompleted(id, completed, completedAt)
        markTaskDirty(id)
        if (completed) planStore?.removeTaskFromPlan(id)
        refreshWidget()
        if (item != null) {
            val type = if (completed) TaskEvent.TYPE_COMPLETED else TaskEvent.TYPE_UNCOMPLETED
            recordEvent(item, type)
            // Auto-create next instance for recurring tasks
            if (completed && item.recurrenceRule != null) {
                createNextRecurringInstance(item)
            }
        }
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
        val item = ActionItem(
            text = text,
            projectId = projectId,
            dueDate = dueDate,
            priority = priority,
            notes = notes,
            syncVersion = nextSyncVersion()
        )
        val id = actionItemDao.insert(item)
        recordEvent(item.copy(id = id), TaskEvent.TYPE_CREATED)
        return id
    }

    suspend fun updateTask(item: ActionItem) {
        actionItemDao.update(item.copy(updatedAt = System.currentTimeMillis(), syncVersion = nextSyncVersion()))
    }

    suspend fun updateTaskText(id: Long, text: String) {
        actionItemDao.updateText(id, text)
        markTaskDirty(id)
    }

    suspend fun getTasksByIds(ids: List<Long>): List<ActionItem> =
        if (ids.isEmpty()) emptyList() else actionItemDao.getByIds(ids)

    suspend fun setDueDateLocked(id: Long, locked: Boolean) {
        actionItemDao.setDueDateLocked(id, locked)
        markTaskDirty(id)
    }

    suspend fun setDueDate(id: Long, dueDate: Long?, force: Boolean = false) {
        val item = actionItemDao.getByIdSync(id)
        if (!force && item?.dueDateLocked == true) return
        actionItemDao.setDueDate(id, dueDate)
        markTaskDirty(id)
        if (item != null) {
            recordEvent(item, TaskEvent.TYPE_DUE_DATE_CHANGED,
                """{"oldDueDate":${item.dueDate},"newDueDate":$dueDate}""")
        }
        // If rescheduled to a future date, remove from today's plan
        val dayEnd = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_YEAR, 1)
        }.timeInMillis
        if (dueDate != null && dueDate >= dayEnd) {
            planStore?.removeTaskFromPlan(id)
            refreshWidget()
        }
    }

    suspend fun setDropDeadDate(id: Long, dropDeadDate: Long?) {
        actionItemDao.setDropDeadDate(id, dropDeadDate)
        markTaskDirty(id)
    }

    suspend fun setEstimatedMinutes(id: Long, minutes: Int) {
        actionItemDao.setEstimatedMinutes(id, minutes)
        markTaskDirty(id)
    }

    // --- Recurrence ---

    suspend fun setRecurrence(id: Long, rule: String?, interval: Int = 1) {
        actionItemDao.setRecurrence(id, rule, interval)
        markTaskDirty(id)
    }

    /**
     * Creates the next instance of a recurring task. Called after completing a recurring task.
     * The new task copies the original's text, project, notes, priority, effort, and recurrence rule.
     * Due date is advanced by the recurrence interval.
     */
    suspend fun createNextRecurringInstance(completedItem: ActionItem): Long {
        val rule = RecurrenceRule.fromString(completedItem.recurrenceRule) ?: return -1
        val nextDueDate = computeNextDueDate(completedItem.dueDate, rule, completedItem.recurrenceInterval)

        val nextItem = ActionItem(
            text = completedItem.text,
            projectId = completedItem.projectId,
            notes = completedItem.notes,
            dueDate = nextDueDate,
            priority = completedItem.priority,
            estimatedMinutes = completedItem.estimatedMinutes,
            recurrenceRule = completedItem.recurrenceRule,
            recurrenceInterval = completedItem.recurrenceInterval,
            syncVersion = nextSyncVersion()
        )
        val id = actionItemDao.insert(nextItem)
        recordEvent(nextItem.copy(id = id), TaskEvent.TYPE_CREATED,
            """{"recurringFrom":${completedItem.id}}""")
        return id
    }

    // --- AI Enrichment ---

    data class EnrichmentProgress(
        val processed: Int,
        val total: Int,
        val enriched: Int,
        val log: List<String> = emptyList()
    )

    /**
     * Runs AI enrichment on tasks that are missing effort estimates or context tags.
     * Processes in batches of 10 to stay within API limits.
     * Non-destructive: only sets estimatedMinutes if currently 0; only appends tags not already present.
     * Reports progress via the [onProgress] callback.
     */
    suspend fun enrichUnenrichedTasks(onProgress: (EnrichmentProgress) -> Unit): EnrichmentProgress {
        val allActive = actionItemDao.getAllActiveItemTexts()
        val needsEnrichment = allActive.filter { task -> task.estimatedMinutes == 0 }

        val total = needsEnrichment.size
        var processed = 0
        var enriched = 0
        val log = mutableListOf<String>()

        needsEnrichment.chunked(10).forEach { batch ->
            val pairs = batch.map { it.id to it.text }
            val result = geminiClient.enrichTasks(pairs)
            result.onSuccess { enrichments ->
                enrichments.forEach { enrichment ->
                    val task = batch.find { it.id == enrichment.id } ?: return@forEach
                    val changes = mutableListOf<String>()
                    // Set effort if unestimated
                    if (task.estimatedMinutes == 0 && enrichment.estimatedMinutes > 0) {
                        actionItemDao.setEstimatedMinutes(task.id, enrichment.estimatedMinutes)
                        val mins = enrichment.estimatedMinutes
                        changes.add(if (mins >= 60) "${mins / 60}h${if (mins % 60 > 0) "${mins % 60}m" else ""}" else "${mins}m")
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
                            changes.add(tagStr)
                        }
                    }
                    val taskTitle = task.text.take(40) + if (task.text.length > 40) "…" else ""
                    log.add("$taskTitle: ${if (changes.isEmpty()) "no changes" else changes.joinToString(", ")}")
                    if (changes.isNotEmpty()) enriched++
                }
            }
            processed += batch.size
            onProgress(EnrichmentProgress(processed, total, enriched, log.toList()))
        }

        return EnrichmentProgress(processed, total, enriched, log)
    }

    suspend fun countUnenrichedTasks(): Int {
        val allActive = actionItemDao.getAllActiveItemTexts()
        // Only count tasks missing an effort estimate — AI always sets a non-zero estimate
        // for every task it processes, so this correctly reflects truly un-enriched tasks.
        // Tags are a best-effort bonus; not every task has a meaningful context tag.
        return allActive.count { task -> task.estimatedMinutes == 0 }
    }

    suspend fun countDueTodayAndOverdue(dayEnd: Long): Int {
        return actionItemDao.countDueTodayAndOverdue(dayEnd)
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

    suspend fun getWaitingForItems(limit: Int = 5): List<ActionItem> =
        actionItemDao.getWaitingForItems(limit)

    suspend fun removeTagFromNotes(id: Long, tag: String) {
        val item = actionItemDao.getByIdSync(id) ?: return
        val cleaned = item.notes?.replace(Regex("#$tag\\b", RegexOption.IGNORE_CASE), "")?.trim()
        actionItemDao.update(item.copy(notes = cleaned?.ifBlank { null }, updatedAt = System.currentTimeMillis()))
        markTaskDirty(id)
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
        val now = System.currentTimeMillis()
        val dayStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val dayEnd = dayStart + 24L * 60 * 60 * 1000

        val urgentThreshold = now + 14L * 24 * 60 * 60 * 1000
        val allTasks = actionItemDao.getActiveItemsForScheduling(urgentThreshold)

        fun isEligible(task: ActionItem): Boolean {
            val notes = task.notes ?: ""
            if (notes.contains("#waiting-for", ignoreCase = true)) return false
            if (contextTag != null) {
                val hasTags = Regex("#[\\w-]+").containsMatchIn(notes)
                val hasContextTag = notes.contains("#$contextTag", ignoreCase = true)
                if (hasTags && !hasContextTag) return false
            }
            return true
        }

        // Bucket 1: overdue + today tasks (dueDate < dayEnd), or tasks with a
        //   drop-dead date within 7 days regardless of due date
        // Bucket 2: undated tasks (dueDate == null)
        // Bucket 3: future tasks (due after today) — only pulled in if capacity remains
        val bucket1 = allTasks.filter { isEligible(it) &&
            ((it.dueDate != null && it.dueDate < dayEnd) ||
             (it.dropDeadDate != null && it.dropDeadDate < now + 7L * 24 * 60 * 60 * 1000))
        }.sortedWith(compareByDescending<ActionItem> { it.effectivePriority().ordinal }
            .thenBy { it.dropDeadDate ?: Long.MAX_VALUE }
            .thenBy { it.dueDate ?: Long.MAX_VALUE })

        val bucket2 = allTasks.filter { isEligible(it) && it.dueDate == null && it.dropDeadDate == null }
            .sortedWith(compareByDescending<ActionItem> { it.effectivePriority().ordinal }
                .thenBy { if (it.estimatedMinutes > 0) it.estimatedMinutes else 30 })

        val bucket3 = allTasks.filter { isEligible(it) &&
            it.dueDate != null && it.dueDate >= dayEnd &&
            !it.dueDateLocked &&  // Don't pull in locked future-date tasks
            (it.dropDeadDate == null || it.dropDeadDate >= now + 7L * 24 * 60 * 60 * 1000)
        }.sortedWith(compareByDescending<ActionItem> { it.effectivePriority().ordinal }
            .thenBy { it.dueDate ?: Long.MAX_VALUE })

        val result = mutableListOf<ActionItem>()
        var remaining = capacityMinutes

        // Always include overdue + today tasks — they're due now
        for (task in bucket1) {
            val estimate = if (task.estimatedMinutes > 0) task.estimatedMinutes else 30
            result.add(task)
            remaining -= estimate
        }

        // Only pull undated/future tasks if there's remaining capacity
        if (remaining > 0) {
            for (task in bucket2 + bucket3) {
                val estimate = if (task.estimatedMinutes > 0) task.estimatedMinutes else 30
                if (estimate <= remaining) {
                    result.add(task)
                    remaining -= estimate
                }
                if (remaining <= 0) break
            }
        }
        return result
    }

    fun getUndatedCount(): Flow<Int> = actionItemDao.getUndatedCount()

    fun getUndatedItems(): Flow<List<ActionItem>> = actionItemDao.getUndatedItemsFlow()

    suspend fun trashTask(id: Long) {
        val item = actionItemDao.getByIdSync(id)
        actionItemDao.trashItem(id)
        markTaskDirty(id)
        planStore?.removeTaskFromPlan(id)
        refreshWidget()
        if (item != null) recordEvent(item, TaskEvent.TYPE_TRASHED)
    }

    suspend fun restoreTask(id: Long) {
        val item = actionItemDao.getByIdSync(id)
        actionItemDao.restoreItem(id)
        markTaskDirty(id)
        if (item != null) recordEvent(item, TaskEvent.TYPE_RESTORED)
    }

    suspend fun deleteTask(id: Long) = actionItemDao.deleteById(id)

    // --- Task Events ---

    suspend fun getTaskEvents(taskId: Long): List<TaskEvent> =
        taskEventDao?.getEventsForTask(taskId) ?: emptyList()

    suspend fun getDueDateChangeCount(taskId: Long): Int =
        taskEventDao?.countEventsForTask(taskId, TaskEvent.TYPE_DUE_DATE_CHANGED) ?: 0

    // --- Triage ---

    suspend fun getTriageCandidates(): List<TriageItem> {
        val now = System.currentTimeMillis()
        val sevenDaysAgo = now - 7L * 24 * 60 * 60 * 1000
        val recentlyTriaged = taskEventDao?.getRecentlyTriagedTaskIds(sevenDaysAgo)?.toSet() ?: emptySet()
        val seen = mutableSetOf<Long>()
        seen.addAll(recentlyTriaged)
        val items = mutableListOf<TriageItem>()

        // 1. Stale tasks (untouched 7+ days)
        val staleTasks = getStaleItems(staleDaysThreshold = 7, limit = 5)
        for (task in staleTasks) {
            if (seen.add(task.id)) {
                val daysAgo = ((now - task.updatedAt) / (24 * 60 * 60 * 1000)).toInt()
                items.add(TriageItem(task, "Untouched for $daysAgo days", TriageCategory.STALE))
            }
        }

        // 2. Frequently rescheduled (3+ due date changes)
        val rescheduledIds = taskEventDao?.getFrequentlyRescheduledTaskIds(3) ?: emptyList()
        if (rescheduledIds.isNotEmpty()) {
            val tasks = actionItemDao.getByIds(rescheduledIds.map { it.taskId })
                .filter { !it.isCompleted && !it.isTrashed }
            for (task in tasks) {
                if (seen.add(task.id)) {
                    val count = rescheduledIds.first { it.taskId == task.id }.cnt
                    items.add(TriageItem(task, "Rescheduled $count times", TriageCategory.RESCHEDULED))
                }
            }
        }

        // 3. Large undated tasks (60+ min effort, no dates)
        val largeTasks = actionItemDao.getLargeUndatedItems(minEffort = 60, limit = 5)
        for (task in largeTasks) {
            if (seen.add(task.id)) {
                val mins = task.estimatedMinutes
                val label = if (mins >= 60) "${mins / 60}h${if (mins % 60 > 0) "${mins % 60}m" else ""}" else "${mins}m"
                items.add(TriageItem(task, "Large task ($label) with no due date", TriageCategory.LARGE_UNDATED))
            }
        }

        // 4. Waiting-for tasks
        val waitingTasks = getWaitingForItems(limit = 5)
        for (task in waitingTasks) {
            if (seen.add(task.id)) {
                items.add(TriageItem(task, "Still blocked?", TriageCategory.WAITING_FOR))
            }
        }

        return items.take(10)
    }

    suspend fun getAllTriageCandidates(): List<TriageItem> {
        val now = System.currentTimeMillis()
        val dayStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val seen = mutableSetOf<Long>()
        val items = mutableListOf<TriageItem>()

        val dateFmt = java.text.SimpleDateFormat("MMM d", java.util.Locale.getDefault())

        // 1. Overdue tasks
        val overdueTasks = actionItemDao.getOverdueItemsSync(dayStart)
        for (task in overdueTasks) {
            if (seen.add(task.id)) {
                val dueDate = task.dueDate ?: task.dropDeadDate ?: now
                val daysOverdue = ((now - dueDate) / (24 * 60 * 60 * 1000)).toInt()
                val label = if (daysOverdue <= 0) "Due today" else "Overdue by $daysOverdue day${if (daysOverdue != 1) "s" else ""}"
                items.add(TriageItem(task, label, TriageCategory.OVERDUE))
            }
        }

        // 2. Undated tasks (no due date, no drop-dead date)
        val undatedTasks = actionItemDao.getUndatedItems()
        for (task in undatedTasks) {
            if (seen.add(task.id)) {
                val daysAgo = ((now - task.createdAt) / (24 * 60 * 60 * 1000)).toInt()
                val label = if (daysAgo > 0) "No date (created $daysAgo days ago)" else "No date"
                items.add(TriageItem(task, label, TriageCategory.UNDATED))
            }
        }

        return items
    }

    suspend fun triageTask(id: Long) {
        val item = actionItemDao.getByIdSync(id) ?: return
        actionItemDao.update(item.copy(updatedAt = System.currentTimeMillis(), syncVersion = nextSyncVersion()))
        recordEvent(item, TaskEvent.TYPE_TRIAGED)
    }

    suspend fun snoozeTask(id: Long) {
        val item = actionItemDao.getByIdSync(id) ?: return
        actionItemDao.update(item.copy(updatedAt = System.currentTimeMillis(), syncVersion = nextSyncVersion()))
        recordEvent(item, TaskEvent.TYPE_SNOOZED)
    }

    suspend fun addTagToNotes(id: Long, tag: String) {
        val item = actionItemDao.getByIdSync(id) ?: return
        val existingNotes = item.notes.orEmpty()
        if (existingNotes.contains("#$tag", ignoreCase = true)) return
        val tagStr = "#$tag"
        val updatedNotes = if (existingNotes.isBlank()) tagStr else "$existingNotes $tagStr"
        actionItemDao.update(item.copy(notes = updatedNotes, updatedAt = System.currentTimeMillis(), syncVersion = nextSyncVersion()))
        markTaskDirty(id)
    }

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

    suspend fun getAllNonTrashedTasks(): List<ActionItem> = actionItemDao.getAllNonTrashed()

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
