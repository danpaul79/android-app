package com.example.aicompanion.domain.extraction

import com.example.aicompanion.data.local.entity.Priority

data class ExtractedItem(
    val text: String,
    val dueDate: Long? = null,
    val priority: Priority = Priority.NONE,
    val suggestedProject: String? = null,
    val isDuplicate: Boolean = false,
    val duplicateOf: String? = null
)
