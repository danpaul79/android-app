package com.example.aicompanion.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "action_items",
    foreignKeys = [
        ForeignKey(
            entity = VoiceNote::class,
            parentColumns = ["id"],
            childColumns = ["voiceNoteId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("voiceNoteId")]
)
data class ActionItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val voiceNoteId: Long,
    val text: String,
    val dueDate: Long? = null,
    val isCompleted: Boolean = false,
    val reminderFired: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
