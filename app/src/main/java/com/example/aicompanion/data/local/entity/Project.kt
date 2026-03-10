package com.example.aicompanion.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "projects")
data class Project(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val color: Int = DEFAULT_COLOR,
    val icon: String = "folder",
    val sortOrder: Int = 0,
    val isArchived: Boolean = false,
    val isTrashed: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val googleTaskListId: String? = null,
    val syncVersion: Long = 0
) {
    companion object {
        const val DEFAULT_COLOR = 0xFF6200EE.toInt()
    }
}
