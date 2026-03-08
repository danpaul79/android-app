package com.example.aicompanion.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class SourceType { VOICE_NOTE, EMAIL, CHAT, SMS, MANUAL }

@Entity(tableName = "sources")
data class Source(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val type: SourceType,
    val rawContent: String,
    val sourceRef: String? = null,
    val processedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)
