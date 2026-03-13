package com.example.aicompanion.reminder

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Persists the most recent morning plan so the Dashboard can display it as a dismissible card,
 * and Settings can show a history of past plans.
 */
class MorningPlanStore(context: Context) {
    private val prefs = context.getSharedPreferences("morning_plans", Context.MODE_PRIVATE)

    data class PlanEntry(
        val timestamp: Long,
        val capacityMinutes: Int,
        val tasks: List<PlanTask>
    ) {
        val dateLabel: String get() =
            SimpleDateFormat("EEE MMM d, h:mm a", Locale.getDefault()).format(Date(timestamp))
        val capacityLabel: String get() = when {
            capacityMinutes < 60 -> "${capacityMinutes}m"
            capacityMinutes % 60 == 0 -> "${capacityMinutes / 60}h"
            else -> "${capacityMinutes / 60}h ${capacityMinutes % 60}m"
        }
    }

    data class PlanTask(val id: Long, val text: String, val estimatedMinutes: Int)

    /** Save a new plan. Keeps the last 7 entries. Clears any prior dismissal so the
     *  Dashboard card reappears immediately when the user regenerates their plan. */
    fun savePlan(capacityMinutes: Int, tasks: List<PlanTask>) {
        val history = loadHistory().toMutableList()
        history.add(0, PlanEntry(System.currentTimeMillis(), capacityMinutes, tasks))
        // Keep last 7
        val trimmed = history.take(7)
        val arr = JSONArray()
        trimmed.forEach { entry ->
            val obj = JSONObject().apply {
                put("timestamp", entry.timestamp)
                put("capacityMinutes", entry.capacityMinutes)
                val taskArr = JSONArray()
                entry.tasks.forEach { t ->
                    taskArr.put(JSONObject().apply {
                        put("id", t.id)
                        put("text", t.text)
                        put("estimatedMinutes", t.estimatedMinutes)
                    })
                }
                put("tasks", taskArr)
            }
            arr.put(obj)
        }
        // Clear dismissal so the Dashboard card reappears after regenerating
        prefs.edit()
            .putString("history", arr.toString())
            .remove("dismissed_until")
            .apply()
    }

    /** Returns today's plan (most recent entry from today), or null if none. */
    fun getTodaysPlan(): PlanEntry? {
        val history = loadHistory()
        if (history.isEmpty()) return null
        val latest = history.first()
        val todayStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        return if (latest.timestamp >= todayStart) latest else null
    }

    /** Returns all stored plan entries, newest first. */
    fun loadHistory(): List<PlanEntry> {
        val json = prefs.getString("history", null) ?: return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                val taskArr = obj.getJSONArray("tasks")
                val tasks = (0 until taskArr.length()).map { j ->
                    val t = taskArr.getJSONObject(j)
                    PlanTask(t.getLong("id"), t.getString("text"), t.getInt("estimatedMinutes"))
                }
                PlanEntry(obj.getLong("timestamp"), obj.getInt("capacityMinutes"), tasks)
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** Mark today's plan as dismissed (so the Dashboard card hides). */
    fun dismissTodaysPlan() {
        prefs.edit().putLong("dismissed_until", System.currentTimeMillis() + 86400000).apply()
    }

    fun isTodaysPlanDismissed(): Boolean {
        val until = prefs.getLong("dismissed_until", 0L)
        return System.currentTimeMillis() < until
    }

    /**
     * Returns the capacity (in minutes) from today's morning check-in only.
     * Returns null if no capacity was set today.
     */
    fun getLastCapacityMinutes(): Int? = getTodaysPlan()?.capacityMinutes

    /**
     * Removes a task from today's plan (the most recent history entry) if it's present.
     * Called when a task is completed, trashed, or rescheduled to a future date.
     */
    fun removeTaskFromPlan(taskId: Long) {
        val history = loadHistory().toMutableList()
        if (history.isEmpty()) return
        val latest = history[0]
        val filtered = latest.tasks.filter { it.id != taskId }
        if (filtered.size == latest.tasks.size) return // task not in plan, nothing to do
        history[0] = latest.copy(tasks = filtered)
        val arr = JSONArray()
        history.forEach { entry ->
            val obj = JSONObject().apply {
                put("timestamp", entry.timestamp)
                put("capacityMinutes", entry.capacityMinutes)
                val taskArr = JSONArray()
                entry.tasks.forEach { t ->
                    taskArr.put(JSONObject().apply {
                        put("id", t.id)
                        put("text", t.text)
                        put("estimatedMinutes", t.estimatedMinutes)
                    })
                }
                put("tasks", taskArr)
            }
            arr.put(obj)
        }
        prefs.edit().putString("history", arr.toString()).apply()
    }
}
