package com.example.aicompanion.data.sync

import android.content.Context

class GmailPreferences(context: Context) {

    companion object {
        private const val PREFS = "gmail_ingest_prefs"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_LAST_INGEST_TIME = "last_ingest_time"
        private const val KEY_LAST_INGEST_TASK_COUNT = "last_ingest_task_count"
        private const val KEY_LAST_INGEST_MSG_COUNT = "last_ingest_msg_count"
        private const val KEY_LOOKBACK_DAYS = "lookback_days"
        private const val KEY_LAST_ERROR = "last_error"
    }

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_ENABLED, value).apply()

    var lastIngestTime: Long
        get() = prefs.getLong(KEY_LAST_INGEST_TIME, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_INGEST_TIME, value).apply()

    var lastIngestTaskCount: Int
        get() = prefs.getInt(KEY_LAST_INGEST_TASK_COUNT, 0)
        set(value) = prefs.edit().putInt(KEY_LAST_INGEST_TASK_COUNT, value).apply()

    var lastIngestMessageCount: Int
        get() = prefs.getInt(KEY_LAST_INGEST_MSG_COUNT, 0)
        set(value) = prefs.edit().putInt(KEY_LAST_INGEST_MSG_COUNT, value).apply()

    var lookbackDays: Int
        get() = prefs.getInt(KEY_LOOKBACK_DAYS, 1)
        set(value) = prefs.edit().putInt(KEY_LOOKBACK_DAYS, value).apply()

    var lastError: String?
        get() = prefs.getString(KEY_LAST_ERROR, null)
        set(value) = prefs.edit().putString(KEY_LAST_ERROR, value).apply()
}
