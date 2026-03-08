package com.example.aicompanion.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.aicompanion.data.local.entity.ActionItem

@Dao
interface ActionItemDao {
    @Insert
    suspend fun insertAll(items: List<ActionItem>)

    @Query("UPDATE action_items SET isCompleted = :completed WHERE id = :id")
    suspend fun setCompleted(id: Long, completed: Boolean)

    @Query("""
        SELECT * FROM action_items
        WHERE dueDate IS NOT NULL
        AND dueDate <= :windowEnd
        AND dueDate >= :windowStart
        AND isCompleted = 0
        AND reminderFired = 0
    """)
    suspend fun getUpcomingUnfired(windowStart: Long, windowEnd: Long): List<ActionItem>

    @Query("UPDATE action_items SET reminderFired = 1 WHERE id = :id")
    suspend fun markReminderFired(id: Long)

    @Query("DELETE FROM action_items WHERE id = :id")
    suspend fun deleteById(id: Long)
}
