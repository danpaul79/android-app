package com.example.aicompanion.data.repository

import com.example.aicompanion.data.local.dao.ActionItemDao
import com.example.aicompanion.data.local.dao.VoiceNoteDao
import com.example.aicompanion.data.local.entity.ActionItem
import com.example.aicompanion.data.local.entity.VoiceNote
import com.example.aicompanion.data.local.relation.VoiceNoteWithActionItems
import kotlinx.coroutines.flow.Flow

class CaptureRepository(
    private val voiceNoteDao: VoiceNoteDao,
    private val actionItemDao: ActionItemDao
) {
    fun getAllVoiceNotesWithActionItems(): Flow<List<VoiceNoteWithActionItems>> =
        voiceNoteDao.getAllWithActionItems()

    fun getVoiceNoteById(id: Long): Flow<VoiceNoteWithActionItems?> =
        voiceNoteDao.getByIdWithActionItems(id)

    suspend fun saveVoiceNoteWithActionItems(
        voiceNote: VoiceNote,
        actionItems: List<ActionItem>
    ): Long {
        val noteId = voiceNoteDao.insert(voiceNote)
        val itemsWithNoteId = actionItems.map { it.copy(voiceNoteId = noteId) }
        actionItemDao.insertAll(itemsWithNoteId)
        return noteId
    }

    suspend fun toggleActionItemCompleted(id: Long, completed: Boolean) {
        actionItemDao.setCompleted(id, completed)
    }

    suspend fun deleteVoiceNote(id: Long) {
        voiceNoteDao.deleteById(id)
    }

    suspend fun deleteActionItem(id: Long) {
        actionItemDao.deleteById(id)
    }

    suspend fun updateTopic(voiceNoteId: Long, topic: String) {
        voiceNoteDao.updateTopic(voiceNoteId, topic)
    }

    suspend fun getUpcomingUnfiredItems(windowStart: Long, windowEnd: Long): List<ActionItem> =
        actionItemDao.getUpcomingUnfired(windowStart, windowEnd)

    suspend fun markReminderFired(id: Long) {
        actionItemDao.markReminderFired(id)
    }
}
