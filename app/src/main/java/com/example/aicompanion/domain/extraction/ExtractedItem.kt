package com.example.aicompanion.domain.extraction

data class ExtractedItem(
    val text: String,
    val dueDate: Long? = null,
    val topic: String? = null
)
