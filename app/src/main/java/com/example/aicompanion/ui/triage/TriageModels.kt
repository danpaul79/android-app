package com.example.aicompanion.ui.triage

import com.example.aicompanion.data.local.entity.ActionItem

enum class TriageCategory { STALE, RESCHEDULED, LARGE_UNDATED, WAITING_FOR, OVERDUE, UNDATED }

enum class TriageMode { SMART, ALL }

data class TriageItem(
    val task: ActionItem,
    val reason: String,
    val category: TriageCategory
)
