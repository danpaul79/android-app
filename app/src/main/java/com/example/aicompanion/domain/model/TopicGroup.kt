package com.example.aicompanion.domain.model

import com.example.aicompanion.data.local.relation.VoiceNoteWithActionItems

data class TopicGroup(
    val topic: String,
    val notes: List<VoiceNoteWithActionItems>
)
