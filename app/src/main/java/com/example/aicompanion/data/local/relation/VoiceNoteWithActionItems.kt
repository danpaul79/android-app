package com.example.aicompanion.data.local.relation

import androidx.room.Embedded
import androidx.room.Relation
import com.example.aicompanion.data.local.entity.ActionItem
import com.example.aicompanion.data.local.entity.VoiceNote

data class VoiceNoteWithActionItems(
    @Embedded val voiceNote: VoiceNote,
    @Relation(
        parentColumn = "id",
        entityColumn = "voiceNoteId"
    )
    val actionItems: List<ActionItem>
)
