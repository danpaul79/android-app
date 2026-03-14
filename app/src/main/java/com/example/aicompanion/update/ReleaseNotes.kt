package com.example.aicompanion.update

/**
 * A single release note entry, keyed by versionCode.
 * Add new entries at the TOP of RELEASE_NOTES when bumping versionCode.
 */
data class ReleaseNote(
    val versionCode: Int,
    val versionName: String,
    val highlights: List<String>
)

/**
 * All release notes, newest first.
 * The What's New dialog shows notes for all versions the user hasn't seen yet.
 */
val RELEASE_NOTES: List<ReleaseNote> = listOf(
    ReleaseNote(
        versionCode = 2,
        versionName = "1.1",
        highlights = listOf(
            "New look: Inter font and deep-blue/coral color palette across the entire app",
            "Flat task rows with checkboxes at the left edge for faster scanning",
            "In-app updates: get notified when a new version is available and install without leaving the app",
            "\"What's New\" dialog shows release notes after each update",
            "Undo snackbar when completing tasks via checkbox (not just swipe)",
            "Consolidated history section in Settings with Daily Plans / Voice Notes tabs",
            "Task nudge notifications at configurable times throughout the day"
        )
    )
)
