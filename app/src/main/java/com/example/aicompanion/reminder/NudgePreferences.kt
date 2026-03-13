package com.example.aicompanion.reminder

import android.content.Context

/**
 * SharedPreferences wrapper for nudge notification settings.
 * Stores an enabled flag and a set of hours (0-23) at which to nudge.
 */
class NudgePreferences(context: Context) {

    private val prefs = context.getSharedPreferences("nudge_settings", Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = prefs.getBoolean("enabled", false)
        set(value) = prefs.edit().putBoolean("enabled", value).apply()

    /** Set of hours (0-23) at which nudge notifications should fire. */
    var hours: Set<Int>
        get() = prefs.getStringSet("hours", setOf("14"))!!.map { it.toInt() }.toSet()
        set(value) = prefs.edit().putStringSet("hours", value.map { it.toString() }.toSet()).apply()

    /** Epoch millis of the last nudge notification, to avoid duplicates within the same hour. */
    var lastNudgeTimestamp: Long
        get() = prefs.getLong("last_nudge", 0L)
        set(value) = prefs.edit().putLong("last_nudge", value).apply()
}
