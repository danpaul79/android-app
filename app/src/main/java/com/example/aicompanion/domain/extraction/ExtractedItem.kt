package com.example.aicompanion.domain.extraction

import com.example.aicompanion.data.local.entity.Priority

data class ExtractedItem(
    val text: String,
    val dueDate: Long? = null,
    val dropDeadDate: Long? = null,
    val priority: Priority = Priority.NONE,
    val suggestedProject: String? = null,
    val estimatedMinutes: Int = 0,
    val suggestedTags: List<String> = emptyList(),
    val isDuplicate: Boolean = false,
    val duplicateOf: String? = null
)
