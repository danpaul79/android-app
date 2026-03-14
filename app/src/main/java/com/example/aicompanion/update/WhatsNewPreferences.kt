package com.example.aicompanion.update

import android.content.Context

/**
 * Tracks which version the user last saw the "What's New" dialog for.
 * On first launch after an update, the dialog is shown once.
 */
class WhatsNewPreferences(context: Context) {

    private val prefs = context.getSharedPreferences("whats_new", Context.MODE_PRIVATE)

    /** The versionCode that the user last dismissed the What's New dialog for. */
    var lastSeenVersionCode: Int
        get() = prefs.getInt("last_seen_version_code", 0)
        set(value) = prefs.edit().putInt("last_seen_version_code", value).apply()
}
