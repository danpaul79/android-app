package com.example.aicompanion.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "voice_notes")
data class VoiceNote(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val rawTranscript: String,
    val topic: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
