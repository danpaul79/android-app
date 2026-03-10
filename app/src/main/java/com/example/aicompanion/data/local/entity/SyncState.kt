package com.example.aicompanion.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sync_state")
data class SyncState(
    @PrimaryKey
    val id: Int = 1,
    val lastSyncTimestamp: Long? = null,
    val lastSyncedVersion: Long = 0,
    val inboxTaskListId: String? = null,
    val syncEnabled: Boolean = false,
    val googleAccountEmail: String? = null
)
