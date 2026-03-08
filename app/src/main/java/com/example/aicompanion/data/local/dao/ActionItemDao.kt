package com.example.aicompanion.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.aicompanion.data.local.entity.ActionItem
import kotlinx.coroutines.flow.Flow

@Dao
interface ActionItemDao {
    @Insert
    suspend fun insert(item: ActionItem): Long

    @Insert
    suspend fun insertAll(items: List<ActionItem>)

    @Update
    suspend fun update(item: ActionItem)

    @Query("SELECT * FROM action_items WHERE id = :id")
    fun getById(id: Long): Flow<ActionItem?>

    @Query("SELECT * FROM action_items WHERE projectId IS NULL AND isCompleted = 0 AND isTrashed = 0 ORDER BY createdAt DESC")
    fun getInboxItems(): Flow<List<ActionItem>>

    @Query("SELECT COUNT(*) FROM action_items WHERE projectId IS NULL AND isCompleted = 0 AND isTrashed = 0")
    fun getInboxCount(): Flow<Int>

    @Query("SELECT * FROM action_items WHERE projectId = :projectId AND isCompleted = 0 AND isTrashed = 0 ORDER BY priority DESC, dueDate ASC, createdAt DESC")
    fun getActiveByProjectId(projectId: Long): Flow<List<ActionItem>>

    @Query("SELECT * FROM action_items WHERE projectId = :projectId AND isTrashed = 0 ORDER BY isCompleted ASC, priority DESC, dueDate ASC, createdAt DESC")
    fun getAllByProjectId(projectId: Long): Flow<List<ActionItem>>

    @Query("SELECT COUNT(*) FROM action_items WHERE projectId = :projectId AND isCompleted = 0 AND isTrashed = 0")
    fun getActiveCountByProjectId(projectId: Long): Flow<Int>

    @Query("""
        SELECT * FROM action_items
        WHERE isCompleted = 0
        AND isTrashed = 0
        AND dueDate IS NOT NULL
        AND dueDate < :now
        ORDER BY dueDate ASC
    """)
    fun getOverdueItems(now: Long): Flow<List<ActionItem>>

    @Query("""
        SELECT * FROM action_items
        WHERE isCompleted = 0
        AND isTrashed = 0
        AND dueDate IS NOT NULL
        AND dueDate >= :dayStart
        AND dueDate < :dayEnd
        ORDER BY dueDate ASC
    """)
    fun getTodayItems(dayStart: Long, dayEnd: Long): Flow<List<ActionItem>>

    @Query("""
        SELECT * FROM action_items
        WHERE isCompleted = 0
        AND isTrashed = 0
        AND dueDate IS NOT NULL
        AND dueDate >= :start
        AND dueDate < :end
        ORDER BY dueDate ASC
    """)
    fun getUpcomingItems(start: Long, end: Long): Flow<List<ActionItem>>

    @Query("SELECT * FROM action_items WHERE isTrashed = 1 ORDER BY updatedAt DESC")
    fun getTrashedItems(): Flow<List<ActionItem>>

    @Query("UPDATE action_items SET isCompleted = :completed, completedAt = :completedAt, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setCompleted(id: Long, completed: Boolean, completedAt: Long?, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE action_items SET projectId = :projectId, updatedAt = :updatedAt WHERE id = :id")
    suspend fun assignToProject(id: Long, projectId: Long?, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE action_items SET dueDate = :dueDate, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setDueDate(id: Long, dueDate: Long?, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE action_items SET text = :text, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateText(id: Long, text: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE action_items SET isTrashed = 1, updatedAt = :updatedAt WHERE id = :id")
    suspend fun trashItem(id: Long, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE action_items SET isTrashed = 0, updatedAt = :updatedAt WHERE id = :id")
    suspend fun restoreItem(id: Long, updatedAt: Long = System.currentTimeMillis())

    @Query("""
        SELECT * FROM action_items
        WHERE dueDate IS NOT NULL
        AND dueDate <= :windowEnd
        AND dueDate >= :windowStart
        AND isCompleted = 0
        AND isTrashed = 0
        AND reminderFired = 0
    """)
    suspend fun getUpcomingUnfired(windowStart: Long, windowEnd: Long): List<ActionItem>

    @Query("UPDATE action_items SET reminderFired = 1 WHERE id = :id")
    suspend fun markReminderFired(id: Long)

    @Query("DELETE FROM action_items WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("""
        SELECT * FROM action_items
        WHERE isCompleted = 1
        AND isTrashed = 0
        AND completedAt IS NOT NULL
        AND completedAt >= :since
        ORDER BY completedAt DESC
        LIMIT 20
    """)
    fun getRecentlyCompleted(since: Long): Flow<List<ActionItem>>

    @Query("SELECT * FROM action_items WHERE isCompleted = 0 AND isTrashed = 0 ORDER BY text")
    suspend fun getAllActiveItemTexts(): List<ActionItem>

    @Query("""
        SELECT * FROM action_items
        WHERE isTrashed = 0
        AND (text LIKE '%' || :query || '%' OR notes LIKE '%' || :query || '%')
        ORDER BY isCompleted ASC, updatedAt DESC
        LIMIT 50
    """)
    fun searchItems(query: String): Flow<List<ActionItem>>
}
