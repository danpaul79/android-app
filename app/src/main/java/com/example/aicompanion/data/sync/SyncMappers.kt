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
     * Google Tasks "due" is date-only (e.g., "2026-03-11T00:00:00.000Z").
     * The string encodes a *local* date — we must extract year/month/day from
     * the string directly and build a local-timezone Calendar at noon, rather
     * than parsing as UTC and adjusting hours (which shifts the date for users
     * in negative-UTC-offset timezones).
     */
    fun parseRfc3339ToLocalNoon(dateStr: String): Long {
        // Extract yyyy-MM-dd from the front of the string regardless of format
        val datePart = dateStr.take(10) // "2026-03-11"
        val parts = datePart.split("-")
        if (parts.size == 3) {
            val year = parts[0].toIntOrNull()
            val month = parts[1].toIntOrNull()
            val day = parts[2].toIntOrNull()
            if (year != null && month != null && day != null) {
                return Calendar.getInstance().apply {
                    set(year, month - 1, day, 12, 0, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
            }
        }
        // Fallback: parse UTC and return as-is
        return try {
            rfc3339DateOnly.parse(dateStr)?.time ?: System.currentTimeMillis()
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
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
