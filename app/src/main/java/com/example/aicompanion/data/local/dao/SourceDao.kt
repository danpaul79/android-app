package com.example.aicompanion.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.aicompanion.data.local.entity.Source
import kotlinx.coroutines.flow.Flow

@Dao
interface SourceDao {
    @Insert
    suspend fun insert(source: Source): Long

    @Query("SELECT * FROM sources WHERE id = :id")
    fun getById(id: Long): Flow<Source?>

    @Query("""
        SELECT s.* FROM sources s
        INNER JOIN action_items ai ON ai.sourceId = s.id
        WHERE ai.id = :actionItemId
    """)
    fun getByActionItemId(actionItemId: Long): Flow<Source?>
}
