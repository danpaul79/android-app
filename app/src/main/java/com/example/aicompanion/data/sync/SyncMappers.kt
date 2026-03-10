package com.example.aicompanion.data.sync

import com.example.aicompanion.data.local.entity.ActionItem
import com.example.aicompanion.data.local.entity.Project
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * Converts between local Room entities and Google Tasks API models.
 */
object SyncMappers {

    private val rfc3339DateOnly = SimpleDateFormat("yyyy-MM-dd'T'00:00:00.000'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    private val rfc3339Full = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    // --- ActionItem <-> GoogleTask ---

    fun ActionItem.toGoogleTask() = GoogleTask(
        id = googleTaskId,
        title = text,
        notes = notes,
        due = dueDate?.let { rfc3339DateOnly.format(it) },
        status = if (isCompleted) "completed" else "needsAction",
        completed = completedAt?.let { rfc3339Full.format(it) }
    )

    fun GoogleTask.toActionItem(
        projectId: Long?,
        googleTaskListId: String,
        existingItem: ActionItem? = null
    ): ActionItem {
        val now = System.currentTimeMillis()
        val isComplete = status == "completed"
        return ActionItem(
            id = existingItem?.id ?: 0,
            projectId = projectId,
            sourceId = existingItem?.sourceId,
            text = title,
            notes = notes,
            dueDate = due?.let { parseRfc3339ToLocalNoon(it) },
            priority = existingItem?.priority ?: com.example.aicompanion.data.local.entity.Priority.NONE,
            isCompleted = isComplete,
            completedAt = if (isComplete) {
                completed?.let { parseRfc3339(it) } ?: now
            } else null,
            reminderFired = existingItem?.reminderFired ?: false,
            isTrashed = deleted,
            createdAt = existingItem?.createdAt ?: now,
            updatedAt = now,
            googleTaskId = id,
            googleTaskListId = googleTaskListId,
            syncVersion = 0  // Pulled from remote, not dirty
        )
    }

    fun Project.toGoogleTaskList() = GoogleTaskList(
        id = googleTaskListId ?: "",
        title = name
    )

    fun GoogleTaskList.toProject(existingProject: Project? = null): Project {
        return Project(
            id = existingProject?.id ?: 0,
            name = title,
            color = existingProject?.color ?: Project.DEFAULT_COLOR,
            icon = existingProject?.icon ?: "folder",
            sortOrder = existingProject?.sortOrder ?: 0,
            isArchived = existingProject?.isArchived ?: false,
            isTrashed = false,
            createdAt = existingProject?.createdAt ?: System.currentTimeMillis(),
            googleTaskListId = id,
            syncVersion = 0  // Pulled from remote, not dirty
        )
    }

    // --- Date helpers ---

    /**
     * Parses an RFC 3339 date string and converts to local noon millis.
     * Google Tasks "due" is date-only (e.g., "2024-03-15T00:00:00.000Z").
     * We convert to local noon to match the existing utcPickerToLocalNoon() pattern.
     */
    fun parseRfc3339ToLocalNoon(dateStr: String): Long {
        val utcDate = try {
            rfc3339DateOnly.parse(dateStr)
        } catch (e: Exception) {
            try { rfc3339Full.parse(dateStr) } catch (e2: Exception) { null }
        } ?: return System.currentTimeMillis()

        // Convert UTC midnight to local noon
        val cal = Calendar.getInstance().apply {
            time = utcDate
            // The parsed date is UTC midnight — set to local noon of that date
            set(Calendar.HOUR_OF_DAY, 12)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    fun parseRfc3339(dateStr: String): Long? {
        return try {
            rfc3339Full.parse(dateStr)?.time
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Parses the "updated" timestamp from Google Tasks for comparison.
     * Returns epoch millis, or 0 if parsing fails.
     */
    fun parseUpdatedTimestamp(updated: String?): Long {
        if (updated.isNullOrEmpty()) return 0
        return try {
            rfc3339Full.parse(updated)?.time ?: 0
        } catch (e: Exception) {
            0
        }
    }
}
