package com.example.aicompanion.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Records lifecycle events for tasks — completions, trashes, due date changes, etc.
 * Used to learn patterns: how often tasks are rescheduled, time-of-day completion
 * tendencies, which tags/projects get done vs rot, estimate accuracy, etc.
 */
@Entity(
    tableName = "task_events",
    indices = [Index("taskId"), Index("eventType")]
)
data class TaskEvent(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val taskId: Long,
    val eventType: String,
    val timestamp: Long = System.currentTimeMillis(),
    val projectId: Long? = null,
    val tags: String? = null,          // comma-separated tag snapshot, e.g. "computer,quick"
    val estimatedMinutes: Int = 0,
    val metadata: String? = null       // optional JSON, e.g. {"oldDueDate":..., "newDueDate":...}
) {
    companion object {
        const val TYPE_COMPLETED = "COMPLETED"
        const val TYPE_UNCOMPLETED = "UNCOMPLETED"
        const val TYPE_TRASHED = "TRASHED"
        const val TYPE_RESTORED = "RESTORED"
        const val TYPE_DUE_DATE_CHANGED = "DUE_DATE_CHANGED"
        const val TYPE_CREATED = "CREATED"
        const val TYPE_TRIAGED = "TRIAGED"
        const val TYPE_SNOOZED = "SNOOZED"
    }
}
