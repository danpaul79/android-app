package com.example.aicompanion.reminder

/**
 * Type of task review surfaced in morning check-in notifications.
 */
enum class ReviewType(val subtitle: String, val secondaryLabel: String) {
    STALE("Untouched for 2+ weeks — still need this?", "Trash"),
    WAITING("Waiting on someone — still blocked?", "Unblock");
}
