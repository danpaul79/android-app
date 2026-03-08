package com.example.aicompanion.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

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
    val priority: Priority = Priority.NONE,
    val isCompleted: Boolean = false,
    val completedAt: Long? = null,
    val reminderFired: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
