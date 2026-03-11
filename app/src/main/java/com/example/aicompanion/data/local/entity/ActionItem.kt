package com.example.aicompanion.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Returns the effective priority, auto-escalating based on drop-dead date proximity:
 * - Drop-dead within 1 day → URGENT (regardless of effort)
 * - Drop-dead within 3 days AND effort >= 60 min → URGENT
 * - Drop-dead within 7 days → at least HIGH
 * The stored priority is the floor; dynamic priority can only raise it, never lower.
 */
fun ActionItem.effectivePriority(): Priority {
    val ddd = dropDeadDate ?: return priority
    val now = System.currentTimeMillis()
    val daysUntil = (ddd - now) / (1000L * 60 * 60 * 24)
    val effort = if (estimatedMinutes > 0) estimatedMinutes else 30
    val dynamic = when {
        daysUntil <= 1 -> Priority.URGENT
        daysUntil <= 3 && effort >= 60 -> Priority.URGENT
        daysUntil <= 7 -> Priority.HIGH
        else -> priority
    }
    return if (dynamic.ordinal > priority.ordinal) dynamic else priority
}

/**
 * Returns #hashtags parsed from notes, lower-cased, without the # prefix.
 * e.g. notes "Call re: contract #phone-call #waiting-for" → ["phone-call", "waiting-for"]
 */
fun ActionItem.parsedTags(): List<String> =
    Regex("#([\\w-]+)").findAll(notes ?: "").map { it.groupValues[1].lowercase() }.toList()

@Entity(
    tableName = "action_items",
    foreignKeys = [
        ForeignKey(
            entity = Project::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = Source::class,
            parentColumns = ["id"],
            childColumns = ["sourceId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("projectId"), Index("sourceId")]
)
data class ActionItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val projectId: Long? = null,
    val sourceId: Long? = null,
    val text: String,
    val notes: String? = null,
    val dueDate: Long? = null,
    val dropDeadDate: Long? = null,
    val estimatedMinutes: Int = 0,
    val priority: Priority = Priority.NONE,
    val isCompleted: Boolean = false,
    val completedAt: Long? = null,
    val reminderFired: Boolean = false,
    val isTrashed: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val googleTaskId: String? = null,
    val googleTaskListId: String? = null,
    val syncVersion: Long = 0
)
