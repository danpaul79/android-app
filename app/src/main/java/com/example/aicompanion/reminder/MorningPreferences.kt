package com.example.aicompanion.reminder

import android.content.Context

/**
 * Simple SharedPreferences wrapper for morning check-in settings.
 */
class MorningPreferences(context: Context) {

    private val prefs = context.getSharedPreferences("morning_checkin", Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = prefs.getBoolean("enabled", false)
        set(value) = prefs.edit().putBoolean("enabled", value).apply()

    /** Hour of day for morning notification (0-23), default 8 */
    var hourOfDay: Int
        get() = prefs.getInt("hour_of_day", 8)
        set(value) = prefs.edit().putInt("hour_of_day", value).apply()

    /** Minute of hour, default 0 */
    var minute: Int
        get() = prefs.getInt("minute", 0)
        set(value) = prefs.edit().putInt("minute", value).apply()
}
