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
        versionCode = 11,
        versionName = "1.4.5",
        highlights = listOf(
            "'0m' effort chip now correctly counts as 0 minutes in capacity planning",
            "Planning tasks (0m) no longer consume capacity in Plan My Day or dashboard load"
        )
    ),
    ReleaseNote(
        versionCode = 10,
        versionName = "1.4.4",
        highlights = listOf(
            "Added '0m' effort chip for planning tasks that don't need time"
        )
    ),
    ReleaseNote(
        versionCode = 9,
        versionName = "1.4.3",
        highlights = listOf(
            "Projects page: long-press tasks for multi-select with batch due date, complete, and trash",
            "Selection mode shows count in top bar with cancel button",
            "Reverted effort chip label back to '?' (was 'None')"
        )
    ),
    ReleaseNote(
        versionCode = 8,
        versionName = "1.4.2",
        highlights = listOf(
            "Long-press projects to trash them from the Projects page",
            "Effort estimate: renamed '?' to 'None' for tasks with no time needed"
        )
    ),
    ReleaseNote(
        versionCode = 7,
        versionName = "1.4.1",
        highlights = listOf(
            "Voice command bar now uses background recording service",
            "Fixed notification tap to return to Capture screen",
            "All recording (Capture + voice commands) goes through foreground service"
        )
    ),
    ReleaseNote(
        versionCode = 6,
        versionName = "1.4",
        highlights = listOf(
            "Background recording: continue voice notes while using other apps",
            "Persistent notification with timer, pause/stop controls during recording",
            "Recording survives app switching and returns to Capture screen on tap"
        )
    ),
    ReleaseNote(
        versionCode = 5,
        versionName = "1.3.1",
        highlights = listOf(
            "Plan My Day no longer pulls in future-dated tasks (only today + overdue)",
            "Plan My Day prioritizes tasks by priority within capacity budget",
            "AI enrichment: fixed effort not saving and overwriting existing estimates"
        )
    ),
    ReleaseNote(
        versionCode = 4,
        versionName = "1.3",
        highlights = listOf(
            "Drag-and-drop reorder for today's tasks on Dashboard",
            "New tasks default to today's date so they appear immediately",
            "Expandable nudge notifications show all due tasks",
            "Selection mode: voice bar hides, icon-only action buttons",
            "Disabled accidental swipe-left-to-trash on Dashboard",
            "Fixed double-height top bars on all screens (Android 16)",
            "Fixed button text wrapping in Settings",
            "Plan My Day no longer pulls future tasks when today is full",
            "Screenshot upload warning in feedback form",
            "Version number and release history in Settings"
        )
    ),
    ReleaseNote(
        versionCode = 3,
        versionName = "1.2",
        highlights = listOf(
            "In-app update check now works with Firebase App Testers API",
            "Date picker fixes for triage and task detail screens",
            "Theme toggle: switch between light, dark, and system modes",
            "Compact top bar titles for more screen space"
        )
    ),
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
