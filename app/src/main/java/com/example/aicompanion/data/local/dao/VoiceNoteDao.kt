package com.example.aicompanion.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.example.aicompanion.data.local.entity.VoiceNote
import com.example.aicompanion.data.local.relation.VoiceNoteWithActionItems
import kotlinx.coroutines.flow.Flow

@Dao
interface VoiceNoteDao {
    @Insert
    suspend fun insert(voiceNote: VoiceNote): Long

    @Transaction
    @Query("SELECT * FROM voice_notes ORDER BY createdAt DESC")
    fun getAllWithActionItems(): Flow<List<VoiceNoteWithActionItems>>

    @Transaction
    @Query("SELECT * FROM voice_notes WHERE id = :id")
    fun getByIdWithActionItems(id: Long): Flow<VoiceNoteWithActionItems?>

    @Query("SELECT DISTINCT topic FROM voice_notes WHERE topic IS NOT NULL ORDER BY topic")
    fun getAllTopics(): Flow<List<String>>

    @Query("DELETE FROM voice_notes WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE voice_notes SET topic = :topic WHERE id = :id")
    suspend fun updateTopic(id: Long, topic: String)
}
