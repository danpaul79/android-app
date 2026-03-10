package com.example.aicompanion.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.example.aicompanion.data.local.entity.SyncState

@Dao
interface SyncStateDao {
    @Query("SELECT * FROM sync_state WHERE id = 1")
    suspend fun get(): SyncState?

    @Upsert
    suspend fun upsert(state: SyncState)
}
