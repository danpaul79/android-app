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

    @Query("SELECT * FROM action_items WHERE id = :id")
    suspend fun getByIdSync(id: Long): ActionItem?

    @Query("SELECT * FROM action_items WHERE projectId IS NULL AND isCompleted = 0 AND isTrashed = 0 ORDER BY createdAt DESC")
    fun getInboxItems(): Flow<List<ActionItem>>

    @Query("SELECT COUNT(*) FROM action_items WHERE projectId IS NULL AND isCompleted = 0 AND isTrashed = 0")
    fun getInboxCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM action_items WHERE isCompleted = 0 AND isTrashed = 0 AND dueDate IS NULL AND dropDeadDate IS NULL")
    fun getUndatedCount(): Flow<Int>

    @Query("""
        SELECT * FROM action_items
        WHERE isCompleted = 0
        AND isTrashed = 0
        AND dueDate IS NULL
        AND dropDeadDate IS NULL
        ORDER BY priority DESC, createdAt DESC
    """)
    fun getUndatedItemsFlow(): Flow<List<ActionItem>>

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
        AND (
            (dueDate IS NOT NULL AND dueDate < :now)
            OR (dropDeadDate IS NOT NULL AND dropDeadDate < :now AND (dueDate IS NULL OR dueDate >= :now))
        )
        ORDER BY COALESCE(dueDate, dropDeadDate) ASC
    """)
    fun getOverdueItems(now: Long): Flow<List<ActionItem>>

    @Query("""
        SELECT * FROM action_items
        WHERE isCompleted = 0
        AND isTrashed = 0
        AND (
            (dueDate IS NOT NULL AND dueDate >= :dayStart AND dueDate < :dayEnd)
            OR (dropDeadDate IS NOT NULL AND dropDeadDate >= :dayStart AND dropDeadDate < :dayEnd AND dueDate IS NULL)
        )
        ORDER BY
            CASE WHEN todaySortOrder IS NULL THEN 1 ELSE 0 END ASC,
            todaySortOrder ASC,
            COALESCE(dueDate, dropDeadDate) ASC
    """)
    fun getTodayItems(dayStart: Long, dayEnd: Long): Flow<List<ActionItem>>

    @Query("""
        SELECT * FROM action_items
        WHERE isCompleted = 0
        AND isTrashed = 0
        AND (
            (dueDate IS NOT NULL AND dueDate >= :start AND dueDate < :end)
            OR (dropDeadDate IS NOT NULL AND dropDeadDate >= :start AND dropDeadDate < :end AND dueDate IS NULL)
        )
        ORDER BY COALESCE(dueDate, dropDeadDate) ASC
    """)
    fun getUpcomingItems(start: Long, end: Long): Flow<List<ActionItem>>

    @Query("""
        SELECT * FROM action_items
        WHERE isCompleted = 0
        AND isTrashed = 0
        AND (
            (dueDate IS NOT NULL AND dueDate >= :start)
            OR (dropDeadDate IS NOT NULL AND dropDeadDate >= :start AND dueDate IS NULL)
        )
        ORDER BY COALESCE(dueDate, dropDeadDate) ASC
    """)
    fun getFutureItems(start: Long): Flow<List<ActionItem>>

    @Query("SELECT * FROM action_items WHERE isTrashed = 1 ORDER BY updatedAt DESC")
    fun getTrashedItems(): Flow<List<ActionItem>>

    @Query("UPDATE action_items SET isCompleted = :completed, completedAt = :completedAt, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setCompleted(id: Long, completed: Boolean, completedAt: Long?, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE action_items SET projectId = :projectId, updatedAt = :updatedAt WHERE id = :id")
    suspend fun assignToProject(id: Long, projectId: Long?, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE action_items SET dueDate = :dueDate, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setDueDate(id: Long, dueDate: Long?, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE action_items SET dueDateLocked = :locked, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setDueDateLocked(id: Long, locked: Boolean, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE action_items SET text = :text, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateText(id: Long, text: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE action_items SET isTrashed = 1, updatedAt = :updatedAt WHERE id = :id")
    suspend fun trashItem(id: Long, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE action_items SET isTrashed = 1, updatedAt = :updatedAt WHERE projectId = :projectId")
    suspend fun trashByProjectId(projectId: Long, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE action_items SET isTrashed = 0, updatedAt = :updatedAt WHERE id = :id")
    suspend fun restoreItem(id: Long, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE action_items SET isTrashed = 0, updatedAt = :updatedAt WHERE projectId = :projectId")
    suspend fun restoreByProjectId(projectId: Long, updatedAt: Long = System.currentTimeMillis())

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

    @Query("SELECT * FROM action_items WHERE isTrashed = 0 ORDER BY createdAt")
    suspend fun getAllNonTrashed(): List<ActionItem>

    @Query("""
        SELECT * FROM action_items
        WHERE isTrashed = 0
        AND (text LIKE '%' || :query || '%' OR notes LIKE '%' || :query || '%')
        ORDER BY isCompleted ASC, updatedAt DESC
        LIMIT 50
    """)
    fun searchItems(query: String): Flow<List<ActionItem>>

    // --- Scheduling queries ---

    @Query("""
        SELECT * FROM action_items
        WHERE isCompleted = 0
        AND isTrashed = 0
        ORDER BY
            CASE WHEN dropDeadDate IS NOT NULL AND dropDeadDate <= :urgentThreshold THEN dropDeadDate ELSE 9999999999999 END ASC,
            priority DESC,
            CASE WHEN estimatedMinutes > 0 THEN estimatedMinutes ELSE 30 END ASC,
            createdAt ASC
    """)
    suspend fun getActiveItemsForScheduling(urgentThreshold: Long): List<ActionItem>

    @Query("UPDATE action_items SET estimatedMinutes = :minutes, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setEstimatedMinutes(id: Long, minutes: Int, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE action_items SET dropDeadDate = :dropDeadDate, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setDropDeadDate(id: Long, dropDeadDate: Long?, updatedAt: Long = System.currentTimeMillis())

    @Query("""
        SELECT * FROM action_items
        WHERE isCompleted = 0
        AND isTrashed = 0
        AND updatedAt < :staleThreshold
        AND (notes IS NULL OR notes NOT LIKE '%#waiting-for%')
        ORDER BY RANDOM()
        LIMIT :limit
    """)
    suspend fun getStaleItems(staleThreshold: Long, limit: Int = 3): List<ActionItem>

    @Query("""
        SELECT * FROM action_items
        WHERE isCompleted = 0
        AND isTrashed = 0
        AND notes LIKE '%#waiting-for%'
        ORDER BY updatedAt ASC
        LIMIT :limit
    """)
    suspend fun getWaitingForItems(limit: Int = 5): List<ActionItem>

    @Query("""
        SELECT * FROM action_items
        WHERE isCompleted = 0
        AND isTrashed = 0
        AND estimatedMinutes >= :minEffort
        AND dueDate IS NULL
        AND dropDeadDate IS NULL
        ORDER BY estimatedMinutes DESC
        LIMIT :limit
    """)
    suspend fun getLargeUndatedItems(minEffort: Int = 60, limit: Int = 5): List<ActionItem>

    @Query("""
        SELECT * FROM action_items
        WHERE isCompleted = 0
        AND isTrashed = 0
        AND (
            (dueDate IS NOT NULL AND dueDate < :now)
            OR (dropDeadDate IS NOT NULL AND dropDeadDate < :now AND (dueDate IS NULL OR dueDate >= :now))
        )
        ORDER BY COALESCE(dueDate, dropDeadDate) ASC
    """)
    suspend fun getOverdueItemsSync(now: Long): List<ActionItem>

    @Query("""
        SELECT * FROM action_items
        WHERE isCompleted = 0
        AND isTrashed = 0
        AND dueDate IS NULL
        AND dropDeadDate IS NULL
        ORDER BY updatedAt ASC
    """)
    suspend fun getUndatedItems(): List<ActionItem>

    @Query("UPDATE action_items SET todaySortOrder = :sortOrder, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setTodaySortOrder(id: Long, sortOrder: Int?, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE action_items SET recurrenceRule = :rule, recurrenceInterval = :interval, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setRecurrence(id: Long, rule: String?, interval: Int, updatedAt: Long = System.currentTimeMillis())

    @Query("""
        SELECT COUNT(*) FROM action_items
        WHERE isCompleted = 0 AND isTrashed = 0
        AND (
            (dueDate IS NOT NULL AND dueDate < :dayEnd)
            OR (dropDeadDate IS NOT NULL AND dropDeadDate < :dayEnd AND dueDate IS NULL)
        )
    """)
    suspend fun countDueTodayAndOverdue(dayEnd: Long): Int

    @Query("""
        SELECT * FROM action_items
        WHERE isCompleted = 0 AND isTrashed = 0
        AND (
            (dueDate IS NOT NULL AND dueDate < :dayEnd)
            OR (dropDeadDate IS NOT NULL AND dropDeadDate < :dayEnd AND dueDate IS NULL)
        )
        ORDER BY COALESCE(dueDate, dropDeadDate) ASC
    """)
    suspend fun getDueTodayAndOverdue(dayEnd: Long): List<ActionItem>

    // --- Sync queries ---

    @Query("SELECT * FROM action_items WHERE syncVersion > :sinceVersion")
    suspend fun getDirtyItems(sinceVersion: Long): List<ActionItem>

    @Query("SELECT * FROM action_items WHERE googleTaskId = :googleTaskId LIMIT 1")
    suspend fun getByGoogleTaskId(googleTaskId: String): ActionItem?

    @Query("UPDATE action_items SET googleTaskId = :googleTaskId, googleTaskListId = :googleTaskListId WHERE id = :id")
    suspend fun updateGoogleIds(id: Long, googleTaskId: String, googleTaskListId: String)

    @Query("UPDATE action_items SET syncVersion = :syncVersion WHERE id = :id")
    suspend fun updateSyncVersion(id: Long, syncVersion: Long)

    @Query("UPDATE action_items SET syncVersion = :syncVersion WHERE projectId = :projectId")
    suspend fun updateSyncVersionByProjectId(projectId: Long, syncVersion: Long)

    @Query("SELECT * FROM action_items WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<Long>): List<ActionItem>
}
