package com.example.aicompanion.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.font.FontWeight
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
                    content = """The voice command bar sits above the bottom navigation on every screen (except Capture). Tap the mic icon to speak, or tap the keyboard icon to type a command.

You can say multiple commands in one prompt. Examples:

CREATE TASKS
  "Create task buy groceries"
  "Add task call dentist due tomorrow in Health"
  "Create task plan birthday party, priority high"

COMPLETE TASKS
  "Complete buy groceries"
  "Mark dentist appointment as done"

CHANGE DUE DATES
  "Change due date of buy groceries to Friday"
  "Move dentist to next week"

SET DEADLINES (DROP-DEAD DATES)
  "Set drop dead date for passport to July 25"
  "The deadline for renew passport is July 25"
  "Passport must be done by July 25"

MOVE TASKS
  "Move buy groceries to Home project"

TRASH TASKS
  "Delete buy groceries"
  "Remove the dentist task"

RENAME TASKS
  "Rename buy groceries to buy organic groceries"

CREATE PROJECTS
  "Create a project called Vacation"
  "Make a new list called Shopping"

PLAN YOUR DAY
  "Plan my day"
  "I have 45 minutes"
  "What should I work on?"
  "What can I get done in 2 hours?"

REVIEW TASKS
  "Review my tasks"
  "Triage"
  "What needs attention?"
  "Clean up my tasks" """
                )
            }

            // Voice Capture
            item {
                HelpSection(
                    title = "Voice Capture (Capture Tab)",
                    content = """Record a voice note and Pocket Pilot will automatically:
1. Transcribe it (via Deepgram Nova-3)
2. Extract action items using AI (Gemini)
3. Show you the extracted tasks for review before saving

Tips:
\u2022 Say "create a new project called X" during a recording to auto-create a project and assign tasks to it
\u2022 Toggle "Transcript only" mode if you just want a transcript without task extraction
\u2022 Recording from within a project auto-assigns all extracted tasks to that project
\u2022 Transcripts are saved to Downloads/Pocket Pilot"""
                )
            }

            // Dashboard
            item {
                HelpSection(
                    title = "Dashboard",
                    content = """Your home screen showing:
\u2022 Overdue tasks (past due date)
\u2022 Today's tasks
\u2022 Upcoming tasks (next 7 days)
\u2022 Recently completed (last 7 days, toggle to show/hide)

Actions:
\u2022 Tap a task to view/edit details
\u2022 Swipe right to complete a task
\u2022 Swipe left to trash a task
\u2022 Long-press for multi-select mode (batch due date, complete, rename, trash)
\u2022 (+) button for quick add \u2014 creates a task due today
\u2022 Triage card appears when tasks need review \u2014 tap "Review" to open the triage screen

Top bar: Search | Trash | Settings

Capacity indicator shows "Today: Xm planned / Yh capacity" based on your morning check-in. Tap it to open Plan My Day."""
                )
            }

            // Inbox
            item {
                HelpSection(
                    title = "Inbox",
                    content = """Tasks without a project assignment land here.

\u2022 Long-press for multi-select mode
\u2022 Batch assign to a project, set due dates, rename, or trash
\u2022 Same swipe gestures as Dashboard"""
                )
            }

            // Projects
            item {
                HelpSection(
                    title = "Projects",
                    content = """Organize tasks by life area (Work, Home, Health, etc.).

\u2022 Create projects from the Projects tab or via voice command
\u2022 Tap a project to see its tasks
\u2022 Inside a project: long-press for multi-select, (+) for quick add, mic icon to capture directly into the project
\u2022 Delete a project: all its tasks are trashed too (with confirmation)
\u2022 Trash icon in the Projects tab opens the Trash screen"""
                )
            }

            // Task Detail
            item {
                HelpSection(
                    title = "Task Detail",
                    content = """Tap any task to open its detail screen:

\u2022 Edit task name
\u2022 Set due date (soft/aspirational) and drop-dead date (hard deadline)
\u2022 Set effort estimate: 10m, 20m, 30m, 1h, 90m, 2h+
\u2022 Change project assignment
\u2022 Add notes (including #tags for context)
\u2022 View source info (where the task came from)
\u2022 Trash the task (with confirmation)

Drop-dead dates show in a warning color. Tasks auto-escalate to URGENT priority as their deadline approaches."""
                )
            }

            // Plan My Day
            item {
                HelpSection(
                    title = "Plan My Day",
                    content = """AI-powered daily task scheduling.

1. Select how much time you have (30m, 1h, 90m, 2h, 3h, or custom)
2. Optionally filter by context (Computer, Home, Errands, Phone, Quick)
3. Tap "Pick my tasks" \u2014 the AI selects tasks that fit your capacity

The algorithm prioritizes:
\u2022 Tasks with approaching drop-dead dates
\u2022 Overdue and due-today tasks
\u2022 Higher priority tasks
\u2022 Tasks matching your context filter

#waiting-for tasks are always excluded from scheduling.

Access via: Dashboard capacity indicator, voice command "plan my day", or morning check-in notification."""
                )
            }

            // Task Triage
            item {
                HelpSection(
                    title = "Task Triage (Review)",
                    content = """A guided review screen that walks you through tasks needing attention, one at a time.

Tasks are surfaced if they are:
\u2022 Stale \u2014 untouched for 14+ days
\u2022 Frequently rescheduled \u2014 due date changed 3+ times
\u2022 Large and undated \u2014 60+ min effort with no due date
\u2022 Waiting on someone \u2014 tagged #waiting-for

For each task, you can:
\u2022 Done \u2014 mark complete
\u2022 Keep \u2014 snooze for 2 weeks (won't appear stale again)
\u2022 Set due date \u2014 give it a concrete date
\u2022 Break it down \u2014 AI suggests 2-5 subtasks with effort estimates; pick which to create
\u2022 Snooze 2w \u2014 bump it out of stale detection
\u2022 Waiting on someone \u2014 add/remove #waiting-for tag
\u2022 Trash \u2014 soft delete (with confirmation)

Access via: Dashboard "X tasks need review" card, voice command "review my tasks", or morning notification."""
                )
            }

            // Morning Check-In
            item {
                HelpSection(
                    title = "Morning Check-In",
                    content = """Daily notification at your configured time (Settings \u2192 Morning Check-In).

1. Pick your capacity: 30m, 1h, 90m, 2h, or 3h
2. Pocket Pilot builds a task plan and sends a follow-up notification
3. Tap the plan notification to open Plan My Day

Also surfaces 2-3 tasks that need review (stale or waiting-for) as separate notifications with quick actions: Done, Trash/Unblock, Skip. Tap "Review all" to open the full triage screen.

Enable in Settings \u2192 Morning Check-In \u2192 toggle on + pick your wake-up time."""
                )
            }

            // Effort Estimates
            item {
                HelpSection(
                    title = "Effort Estimates & Tags",
                    content = """Every task has an effort estimate used for capacity planning.

\u2022 AI guesses effort at extraction time
\u2022 Edit on Task Detail: 10m, 20m, 30m, 1h, 90m, 2h+
\u2022 Unestimated tasks default to 30m for scheduling

Context tags (#hashtags in notes):
\u2022 AI suggests tags at extraction: #computer, #errand, #phone-call, #waiting-for, #home, #quick, #creative, #financial
\u2022 Tags show as chips on task cards
\u2022 #waiting-for excludes tasks from scheduling and Plan My Day
\u2022 Tags enable context filtering on Plan My Day

Bulk enrichment: Settings \u2192 AI Analysis \u2192 Run AI Enrichment to backfill effort estimates and tags for existing tasks."""
                )
            }

            // Drop-Dead Dates
            item {
                HelpSection(
                    title = "Due Dates vs Drop-Dead Dates",
                    content = """Pocket Pilot distinguishes between two types of dates:

Due Date (soft): "I'd like to do this by Friday" \u2014 aspirational, can slip.

Drop-Dead Date (hard): "This MUST be done by Friday" \u2014 real deadline with consequences.

As a drop-dead date approaches, priority auto-escalates:
\u2022 URGENT: deadline \u2264 1 day away, or \u2264 3 days + effort \u2265 60m
\u2022 HIGH: deadline \u2264 7 days away

Set via Task Detail or voice: "set drop dead date for X to July 25"."""
                )
            }

            // Google Tasks Sync
            item {
                HelpSection(
                    title = "Google Tasks Sync",
                    content = """Bi-directional sync with Google Tasks (Settings \u2192 Google Tasks Sync).

\u2022 Projects \u2194 Task Lists
\u2022 Inbox tasks sync to "AI Companion Inbox" list
\u2022 Syncs on app resume (debounced 60s) + every 30 minutes via WorkManager
\u2022 Conflict resolution: last-writer-wins by timestamp

Not synced: priority, color, icon (app-only features).

Sign in via Google to enable. Sync Now button available in Settings."""
                )
            }

            // Data Management
            item {
                HelpSection(
                    title = "Data Management",
                    content = """Export: Settings \u2192 Export Data \u2014 saves a JSON backup to Downloads/Pocket Pilot.

Import: Settings \u2192 Import Data \u2014 loads from a JSON file. Duplicate projects (by name) are skipped; tasks are always imported.

Trash: Tasks and projects are soft-deleted (moved to Trash). Restore or permanently delete from the Trash screen. "Empty trash" removes everything permanently.

Trashing a project also trashes all its tasks."""
                )
            }

            // Search
            item {
                HelpSection(
                    title = "Search",
                    content = """Tap the search icon on Dashboard to search across all tasks.

Searches task names and notes with debounced input. Results show both active and completed tasks."""
                )
            }

            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

@Composable
private fun HelpSection(
    title: String,
    content: String
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
        }
    }
}
