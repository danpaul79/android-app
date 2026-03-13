package com.example.aicompanion.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpScreen(
    onNavigateBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Help & Features") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Spacer(Modifier.height(4.dp)) }

            // Overview
            item {
                HelpSection(
                    title = "What is Pocket Pilot?",
                    content = "Pocket Pilot is your personal task hub \u2014 a \"second brain\" that captures tasks from voice notes, organizes them into projects, and helps you plan your day with AI-powered scheduling."
                )
            }

            // Voice Commands
            item {
                HelpSection(
                    title = "Voice Commands",
                    content = "The voice command bar sits above the bottom navigation on every screen (except Settings). Tap the mic icon to speak, or tap the keyboard icon to type a command.\n\nYou can say multiple commands in one prompt.",
                    bullets = listOf(
                        bold("Create tasks:") + " \"Create task buy groceries\" \u2022 \"Add task call dentist due tomorrow in Health\" \u2022 \"Create task plan birthday party, priority high\"",
                        bold("Complete tasks:") + " \"Complete buy groceries\" \u2022 \"Mark dentist as done\"",
                        bold("Change due dates:") + " \"Change due date of buy groceries to Friday\" \u2022 \"Move dentist to next week\"",
                        bold("Set deadlines:") + " \"Set drop dead date for passport to July 25\" \u2022 \"The deadline for renew passport is July 25\"",
                        bold("Move tasks:") + " \"Move buy groceries to Home project\"",
                        bold("Trash tasks:") + " \"Delete buy groceries\" \u2022 \"Remove the dentist task\"",
                        bold("Rename tasks:") + " \"Rename buy groceries to buy organic groceries\"",
                        bold("Create projects:") + " \"Create a project called Vacation\"",
                        bold("Plan your day:") + " \"Plan my day\" \u2022 \"I have 45 minutes\" \u2022 \"What should I work on?\"",
                        bold("Review tasks:") + " \"Review my tasks\" \u2022 \"Triage\" \u2022 \"What needs attention?\""
                    )
                )
            }

            // Voice Capture
            item {
                HelpSection(
                    title = "Voice Capture",
                    content = "Record a voice note and Pocket Pilot will automatically transcribe it (via Deepgram), extract action items using AI (Gemini), and show you the extracted tasks for review before saving.",
                    bullets = listOf(
                        "Tap the mic icon in the Dashboard top bar to open the Capture screen",
                        "Say \"create a new project called X\" during a recording to auto-create a project and assign tasks to it",
                        "Toggle \"Transcript only\" mode if you just want a transcript without task extraction",
                        "Share audio or video files from other apps to Pocket Pilot \u2014 they'll be auto-transcribed in transcript-only mode",
                        "Recording from within a project auto-assigns all extracted tasks to that project",
                        "Transcripts are saved to Downloads/Pocket Pilot"
                    )
                )
            }

            // Dashboard
            item {
                HelpSection(
                    title = "Dashboard",
                    content = "Your home screen showing overdue, today, upcoming (next 7 days), and recently completed tasks.",
                    bullets = listOf(
                        "Tap a task to view/edit details",
                        "Swipe right to complete a task (with undo snackbar)",
                        "Swipe left to trash a task (with undo snackbar)",
                        "Long-press for multi-select mode (batch due date, complete, rename, trash)",
                        "(+) button for quick add \u2014 creates a task due today",
                        "Triage card appears when tasks need review \u2014 tap \"Review\" to open the triage screen",
                        "Capacity indicator shows \"Today: Xm planned / Yh capacity\" \u2014 tap to open Plan My Day",
                        "Today's Plan card shows your planned tasks after morning check-in (dismissible)",
                        "Priority color bars on the left edge of task cards (red = urgent, accent = high)",
                        "Top bar: Search | Trash | Settings"
                    )
                )
            }

            // Inbox
            item {
                HelpSection(
                    title = "Inbox",
                    content = "Tasks without a project assignment land here.",
                    bullets = listOf(
                        "Swipe right to complete, swipe left to trash (with undo snackbar)",
                        "Long-press for multi-select mode",
                        "Batch assign to a project, set due dates, rename, or trash"
                    )
                )
            }

            // Projects
            item {
                HelpSection(
                    title = "Projects",
                    content = "Organize tasks by life area (Work, Home, Health, etc.).",
                    bullets = listOf(
                        "Create projects from the Projects tab or via voice command",
                        "Tap a project to see its tasks",
                        "Inside a project: long-press for multi-select, (+) for quick add, mic icon to capture directly into the project",
                        "Undated tasks filter: tap the filter icon to show only tasks with no due date or drop-dead date",
                        "Navigation badge shows count of undated tasks across all projects",
                        "Delete a project: all its tasks are trashed too (with confirmation)",
                        "Trash icon in the Projects tab opens the Trash screen"
                    )
                )
            }

            // Task Detail
            item {
                HelpSection(
                    title = "Task Detail",
                    content = "Tap any task to open its detail screen.",
                    bullets = listOf(
                        "Edit task name",
                        "Set due date (soft/aspirational) and drop-dead date (hard deadline)",
                        "Lock due date: tap the lock icon to prevent voice commands from changing the date",
                        "Set effort estimate: 10m, 20m, 30m, 1h, 90m, 2h+",
                        "Change project assignment",
                        "Add notes (including #tags for context)",
                        "View source info (where the task came from)",
                        "Trash the task (with confirmation)",
                        "Drop-dead dates show in a warning color; tasks auto-escalate to URGENT priority as the deadline approaches"
                    )
                )
            }

            // Plan My Day
            item {
                HelpSection(
                    title = "Plan My Day",
                    content = "AI-powered daily task scheduling.",
                    bullets = listOf(
                        "Select how much time you have (30m, 1h, 90m, 2h, 3h, or custom)",
                        "Optionally filter by context (Computer, Home, Errands, Phone, Quick)",
                        "Tap \"Pick my tasks\" \u2014 the AI selects tasks that fit your capacity",
                        "Prioritizes: drop-dead dates > overdue/due-today > higher priority > context match",
                        "#waiting-for tasks are always excluded from scheduling",
                        "Access via: Dashboard capacity indicator, voice command \"plan my day\", or morning check-in notification"
                    )
                )
            }

            // Task Triage
            item {
                HelpSection(
                    title = "Task Triage (Review)",
                    content = "A guided review screen that walks you through tasks needing attention, one at a time.",
                    bullets = listOf(
                        bold("Stale") + " \u2014 untouched for 14+ days",
                        bold("Frequently rescheduled") + " \u2014 due date changed 3+ times",
                        bold("Large and undated") + " \u2014 60+ min effort with no due date",
                        bold("Waiting on someone") + " \u2014 tagged #waiting-for",
                        "",
                        bold("Done") + " \u2014 mark complete",
                        bold("Keep") + " \u2014 snooze for 2 weeks (won't appear stale again)",
                        bold("Set due date") + " \u2014 give it a concrete date",
                        bold("Break it down") + " \u2014 AI suggests 2\u20135 subtasks with effort estimates; pick which to create",
                        bold("Snooze 2w") + " \u2014 bump it out of stale detection",
                        bold("Waiting on someone") + " \u2014 add/remove #waiting-for tag",
                        bold("Trash") + " \u2014 soft delete (with confirmation)",
                        "",
                        "Access via: Dashboard triage card, voice command \"review my tasks\", or morning notification"
                    )
                )
            }

            // Morning Check-In
            item {
                HelpSection(
                    title = "Morning Check-In",
                    content = "Daily notification at your configured time (Settings \u2192 Morning Check-In).",
                    bullets = listOf(
                        "Pick your capacity: 30m, 1h, 90m, 2h, or 3h",
                        "Pocket Pilot builds a task plan and sends a follow-up notification",
                        "Tap the plan notification to open Plan My Day",
                        "Also surfaces 2\u20133 tasks needing review (stale or waiting-for) as separate notifications",
                        "Quick actions on review notifications: Done, Trash/Unblock, Skip",
                        "Tap \"Review all\" to open the full triage screen",
                        "Enable in Settings \u2192 Morning Check-In \u2192 toggle on + pick your wake-up time"
                    )
                )
            }

            // Effort Estimates & Tags
            item {
                HelpSection(
                    title = "Effort Estimates & Tags",
                    content = "Every task has an effort estimate used for capacity planning.",
                    bullets = listOf(
                        "AI guesses effort at extraction time",
                        "Edit on Task Detail: 10m, 20m, 30m, 1h, 90m, 2h+",
                        "Unestimated tasks default to 30m for scheduling",
                        "",
                        bold("Context tags") + " (#hashtags in notes):",
                        "AI suggests tags at extraction: #computer, #errand, #phone-call, #waiting-for, #home, #quick, #creative, #financial",
                        "Tags show as chips on task cards in Dashboard, Inbox, and Project Detail",
                        "#waiting-for excludes tasks from scheduling and Plan My Day",
                        "Tags enable context filtering on Plan My Day",
                        "",
                        "Bulk enrichment: Settings \u2192 AI Analysis \u2192 Run AI Enrichment to backfill effort estimates and tags for existing tasks"
                    )
                )
            }

            // Drop-Dead Dates
            item {
                HelpSection(
                    title = "Due Dates vs Drop-Dead Dates",
                    content = "Pocket Pilot distinguishes between two types of dates:",
                    bullets = listOf(
                        bold("Due Date (soft):") + " \"I'd like to do this by Friday\" \u2014 aspirational, can slip",
                        bold("Drop-Dead Date (hard):") + " \"This MUST be done by Friday\" \u2014 real deadline with consequences",
                        "",
                        "As a drop-dead date approaches, priority auto-escalates:",
                        "  URGENT: deadline \u2264 1 day away, or \u2264 3 days + effort \u2265 60m",
                        "  HIGH: deadline \u2264 7 days away",
                        "",
                        "Set via Task Detail or voice: \"set drop dead date for X to July 25\"",
                        "Lock a due date (Task Detail lock icon) to prevent voice commands from changing it"
                    )
                )
            }

            // Swipe Gestures & Undo
            item {
                HelpSection(
                    title = "Swipe Gestures & Undo",
                    content = "Quick task actions available on Dashboard, Inbox, and Project Detail screens.",
                    bullets = listOf(
                        "Swipe right \u2192 complete task",
                        "Swipe left \u2192 trash task",
                        "Both actions show a snackbar with an \"Undo\" button",
                        "Tap \"Undo\" to immediately reverse the action",
                        "Undo is available for a few seconds before the snackbar auto-dismisses"
                    )
                )
            }

            // Home Screen Widget
            item {
                HelpSection(
                    title = "Home Screen Widget",
                    content = "Add a \"Today's Plan\" widget to your Android home screen for quick glance access.",
                    bullets = listOf(
                        "Shows up to 7 tasks from your daily plan with completion status",
                        "Displays progress (e.g., \"2/5 \u00b7 2h 15m\")",
                        "Completed tasks show a checkmark; incomplete tasks show an open circle",
                        bold("Quick capture buttons:") + " tap the mic icon to start voice recording, or the keyboard icon to type a command \u2014 directly from the home screen",
                        "Tap the widget body to open Pocket Pilot",
                        "Auto-refreshes when you complete, trash, or reschedule tasks",
                        "Updates every 30 minutes in the background",
                        "Add via: long-press home screen \u2192 Widgets \u2192 Pocket Pilot",
                        "",
                        bold("App shortcuts:") + " long-press the Pocket Pilot app icon for \"Voice Capture\" and \"Quick Command\" shortcuts"
                    )
                )
            }

            // Google Tasks Sync
            item {
                HelpSection(
                    title = "Google Tasks Sync",
                    content = "Bi-directional sync with Google Tasks (Settings \u2192 Google Tasks Sync).",
                    bullets = listOf(
                        "Projects \u2194 Task Lists",
                        "Inbox tasks sync to \"AI Companion Inbox\" list",
                        "Syncs on app resume (debounced 60s) + every 30 minutes via WorkManager",
                        "Conflict resolution: last-writer-wins by timestamp",
                        "Not synced: priority, color, icon (app-only features)",
                        "Sign in via Google to enable; Sync Now button available in Settings"
                    )
                )
            }

            // Data Management
            item {
                HelpSection(
                    title = "Data Management",
                    content = "Backup and restore your data.",
                    bullets = listOf(
                        bold("Export:") + " Settings \u2192 Export Data \u2014 saves a JSON backup to Downloads/Pocket Pilot",
                        bold("Import:") + " Settings \u2192 Import Data \u2014 loads from a JSON file; duplicate projects (by name) are skipped, tasks are always imported",
                        bold("Trash:") + " tasks and projects are soft-deleted (moved to Trash); restore or permanently delete from the Trash screen",
                        "\"Empty trash\" removes everything permanently",
                        "Trashing a project also trashes all its tasks"
                    )
                )
            }

            // Search
            item {
                HelpSection(
                    title = "Search",
                    content = "Tap the search icon on Dashboard to search across all tasks.\n\nSearches task names and notes with debounced input. Results show both active and completed tasks."
                )
            }

            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

/** Helper to create a bold span for use in bullet lists. */
private fun bold(text: String): androidx.compose.ui.text.AnnotatedString =
    buildAnnotatedString {
        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
            append(text)
        }
    }

/** Concatenate AnnotatedString + plain String. */
private operator fun androidx.compose.ui.text.AnnotatedString.plus(other: String): androidx.compose.ui.text.AnnotatedString =
    buildAnnotatedString {
        append(this@plus)
        append(other)
    }

@Composable
private fun HelpSection(
    title: String,
    content: String,
    bullets: List<Any> = emptyList()
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = content,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (bullets.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                bullets.forEach { bullet ->
                    if (bullet is androidx.compose.ui.text.AnnotatedString) {
                        Row(modifier = Modifier.padding(vertical = 2.dp)) {
                            Text(
                                "\u2022",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = bullet,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    } else {
                        val text = bullet.toString()
                        if (text.isEmpty()) {
                            Spacer(Modifier.height(4.dp))
                        } else {
                            Row(modifier = Modifier.padding(vertical = 2.dp)) {
                                Text(
                                    "\u2022",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = text,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
