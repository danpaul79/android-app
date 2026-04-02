package com.example.aicompanion.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.aicompanion.data.local.entity.TaskEvent

@Dao
interface TaskEventDao {
    @Insert
    suspend fun insert(event: TaskEvent): Long

    @Query("SELECT * FROM task_events WHERE taskId = :taskId ORDER BY timestamp DESC")
    suspend fun getEventsForTask(taskId: Long): List<TaskEvent>

    @Query("SELECT * FROM task_events WHERE eventType = :eventType ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getEventsByType(eventType: String, limit: Int = 100): List<TaskEvent>

    @Query("SELECT COUNT(*) FROM task_events WHERE taskId = :taskId AND eventType = :eventType")
    suspend fun countEventsForTask(taskId: Long, eventType: String): Int

    @Query("""
        SELECT taskId, COUNT(*) as cnt FROM task_events
        WHERE eventType = 'DUE_DATE_CHANGED'
        GROUP BY taskId
        HAVING cnt >= :minCount
    """)
    suspend fun getFrequentlyRescheduledTaskIds(minCount: Int = 3): List<TaskIdCount>

    @Query("""
        SELECT DISTINCT taskId FROM task_events
        WHERE eventType IN ('TRIAGED', 'SNOOZED')
        AND timestamp > :since
    """)
    suspend fun getRecentlyTriagedTaskIds(since: Long): List<Long>

    @Query("DELETE FROM task_events WHERE timestamp < :before")
    suspend fun deleteOlderThan(before: Long)

    @Query("""
        SELECT DISTINCT DATE(timestamp / 1000, 'unixepoch', 'localtime') as day
        FROM task_events
        WHERE eventType = 'COMPLETED'
        ORDER BY day DESC
    """)
    suspend fun getCompletionDays(): List<String>

    @Query("""
        SELECT COUNT(*) FROM task_events
        WHERE eventType = 'COMPLETED'
        AND timestamp >= :since
    """)
    suspend fun countCompletionsSince(since: Long): Int

    @Query("""
        SELECT COUNT(*) FROM task_events
        WHERE eventType = 'COMPLETED'
        AND timestamp >= :weekStart AND timestamp < :weekEnd
    """)
    suspend fun countCompletionsInRange(weekStart: Long, weekEnd: Long): Int
}

data class TaskIdCount(val taskId: Long, val cnt: Int)
