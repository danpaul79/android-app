package com.example.aicompanion.data.local.entity

import java.util.Calendar

enum class RecurrenceRule {
    DAILY, WEEKLY, MONTHLY, YEARLY;

    fun displayName(): String = when (this) {
        DAILY -> "Daily"
        WEEKLY -> "Weekly"
        MONTHLY -> "Monthly"
        YEARLY -> "Yearly"
    }

    fun displayNameWithInterval(interval: Int): String = when {
        interval == 1 -> displayName()
        this == DAILY -> "Every $interval days"
        this == WEEKLY -> "Every $interval weeks"
        this == MONTHLY -> "Every $interval months"
        this == YEARLY -> "Every $interval years"
        else -> displayName()
    }

    companion object {
        fun fromString(value: String?): RecurrenceRule? =
            value?.let { entries.firstOrNull { e -> e.name.equals(it, ignoreCase = true) } }
    }
}

/**
 * Given the current due date and recurrence rule, compute the next due date.
 * If the task has no due date, uses today as the base.
 */
fun computeNextDueDate(
    currentDueDate: Long?,
    rule: RecurrenceRule,
    interval: Int
): Long {
    val base = Calendar.getInstance().apply {
        timeInMillis = currentDueDate ?: System.currentTimeMillis()
        // Normalize to noon to avoid timezone edge cases
        set(Calendar.HOUR_OF_DAY, 12)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }

    when (rule) {
        RecurrenceRule.DAILY -> base.add(Calendar.DAY_OF_YEAR, interval)
        RecurrenceRule.WEEKLY -> base.add(Calendar.WEEK_OF_YEAR, interval)
        RecurrenceRule.MONTHLY -> base.add(Calendar.MONTH, interval)
        RecurrenceRule.YEARLY -> base.add(Calendar.YEAR, interval)
    }

    // If the computed date is in the past (e.g. completing a task late),
    // advance until it's in the future
    val today = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    while (base.before(today)) {
        when (rule) {
            RecurrenceRule.DAILY -> base.add(Calendar.DAY_OF_YEAR, interval)
            RecurrenceRule.WEEKLY -> base.add(Calendar.WEEK_OF_YEAR, interval)
            RecurrenceRule.MONTHLY -> base.add(Calendar.MONTH, interval)
            RecurrenceRule.YEARLY -> base.add(Calendar.YEAR, interval)
        }
    }

    return base.timeInMillis
}
