# Phase 4: Platform Integrations — Gemini AppFunctions & Samsung Now Bar

## Overview

Two platform integration opportunities exist for deeper OS-level integration:

1. **Android AppFunctions** — Let Gemini add tasks to Pocket Pilot via natural language
2. **Samsung Now Bar / Android Live Updates** — Show an active task progress indicator in the phone's persistent status bar chip

---

## Part 1: Android AppFunctions (Gemini Integration)

### What it is

AppFunctions is Android 16's answer to MCP — a standard way for AI agents (primarily Gemini) to invoke functions exposed by third-party apps. A user can say to Gemini: *"Add 'pick up prescription' to Pocket Pilot"* and Gemini calls our registered `createTask` function directly.

**Status**: API is public and documented now (Android 16 / `androidx.appfunctions`). Gemini's live invocation is in beta on Samsung S26 and Pixel 10 hardware. Broader rollout expected with Android 17.

### Key insight: no logic duplication needed

Our voice command pipeline already does exactly what AppFunctions integration needs — take natural language, parse it with Gemini, execute against TaskRepository. AppFunctions is just a new **entry point** into that same pipeline.

The cleanest implementation:
```
Gemini calls AppFunctions.processNaturalLanguage(text)
    → AppFunctionsService hands text to VoiceCommandProcessor.processTranscript()
    → Same pipeline as voice commands, same execution
    → Return result to Gemini
```

### Implementation sketch

#### 1. New dependency in `app/build.gradle.kts`
```kotlin
implementation("androidx.appfunctions:appfunctions:1.0.0-alpha01")
ksp("androidx.appfunctions:appfunctions-compiler:1.0.0-alpha01")
```

#### 2. New file: `appfunctions/PocketPilotAppFunctionService.kt`
```kotlin
@AppFunctionService
class PocketPilotAppFunctionService : AppFunctionService() {

    private val container by lazy {
        (applicationContext as AICompanionApplication).container
    }

    /**
     * Primary entry point: raw natural language from Gemini.
     * Hands off directly to VoiceCommandProcessor — no logic duplication.
     */
    @AppFunction(
        name = "process_request",
        description = "Process a natural language request to manage tasks in Pocket Pilot",
        parameters = [
            AppFunctionParameter(name = "text", description = "What the user wants to do, in plain English"),
        ]
    )
    suspend fun processRequest(params: ProcessRequestParams): ProcessRequestResult {
        val result = container.voiceCommandProcessor.processTranscript(params.text)
        return ProcessRequestResult(
            success = result.success,
            message = result.message ?: "Done"
        )
    }

    /**
     * Simpler direct create for structured calls (Gemini may prefer this
     * when it already has structured data from its own parsing).
     */
    @AppFunction(
        name = "create_task",
        description = "Add a new task to Pocket Pilot",
        parameters = [
            AppFunctionParameter(name = "title", description = "Task title"),
            AppFunctionParameter(name = "project_name", description = "Project to add to (optional)"),
            AppFunctionParameter(name = "due_date", description = "Due date in ISO 8601 (optional)"),
        ]
    )
    suspend fun createTask(params: CreateTaskParams): CreateTaskResult {
        val projects = container.taskRepository.getAllProjects().first()
        val projectId = params.projectName?.let { name ->
            projects.find { it.name.equals(name, ignoreCase = true) }?.id
        }
        val taskId = container.taskRepository.createTask(
            text = params.title,
            projectId = projectId,
            dueDate = params.dueDate?.parseToLocalNoonMillis()
        )
        return CreateTaskResult(taskId = taskId, success = true)
    }
}
```

#### 3. Register in `AndroidManifest.xml`
```xml
<service
    android:name=".appfunctions.PocketPilotAppFunctionService"
    android:exported="true"
    android:permission="android.permission.EXECUTE_APP_FUNCTIONS">
    <intent-filter>
        <action android:name="android.app.appfunctions.AppFunctionService" />
    </intent-filter>
</service>
```

#### 4. Wire into `AppContainer.kt`
```kotlin
// No changes needed — service uses the existing container lazily via Application
```

### Why `processRequest` is the better primary function

- Gemini already handles NLP parsing well — we don't need to re-parse
- But sending raw text to our VoiceCommandProcessor means **our** Gemini instance re-parses it
- This is actually fine: we get consistent behavior, full command support (complete, rename, set due date, etc.), not just create
- Alternative: expose separate `createTask`, `completeTask`, `setDueDate` functions and let Gemini choose — more structured but more surface area to maintain

### Realistic timeline

AppFunctions rollout on S26/Pixel 10 is still beta/limited. No urgency to implement immediately, but the API is stable enough to build against. Fits naturally into **Phase 4** alongside Gmail/SMS input sources.

---

## Part 2: Samsung Now Bar / Android Live Updates

### What it is

Samsung's "Now Bar" is a persistent pill-shaped status indicator (similar to Apple's Dynamic Island). In One UI 8 (S26), it opens up to third-party apps via the **standard Android 16 Live Updates API** — no Samsung-specific code needed.

**Status**: Android 16 `Notification.ProgressStyle` API is available now. Samsung One UI 8 (S26) automatically routes these to the Now Bar. Requires `targetSdk 36`.

### What it would show

The Now Bar is best for **live/ongoing activity** — not a task list. Good candidates:
- An active focus session: "Writing proposal — 25 min remaining"
- Morning plan progress: "Today: 3/5 tasks done"
- A "currently working on" indicator the user manually sets

A static "here are my tasks" view doesn't fit the Now Bar model. It works best as a **focus mode companion**.

### Implementation sketch

#### 1. New notification channel: `TaskProgressNotification.kt`

New file: `reminder/TaskProgressNotification.kt`

```kotlin
class TaskProgressNotification(private val context: Context) {
    companion object {
        const val CHANNEL_ID = "pocket_pilot_live_task"
        const val NOTIFICATION_ID = 9000
    }

    fun showFocusSession(task: ActionItem, elapsedMinutes: Int) {
        ensureChannel()
        val remaining = (task.estimatedMinutes - elapsedMinutes).coerceAtLeast(0)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)  // use existing icon
            .setContentTitle("Focusing: ${task.text}")
            .setContentText("$remaining min remaining")
            .setProgress(task.estimatedMinutes, elapsedMinutes, false)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openAppIntent())

        // Android 16: request promotion to status bar chip / Now Bar
        if (Build.VERSION.SDK_INT >= 36) {
            builder.setRequestPromotedOngoing(true)
        }

        context.getSystemService(NotificationManager::class.java)
            ?.notify(NOTIFICATION_ID, builder.build())
    }

    fun showDayProgress(completed: Int, total: Int) {
        ensureChannel()
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Today's tasks")
            .setContentText("$completed of $total done")
            .setProgress(total, completed, false)
            .setOngoing(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openAppIntent())

        if (Build.VERSION.SDK_INT >= 36) {
            builder.setRequestPromotedOngoing(true)
        }

        context.getSystemService(NotificationManager::class.java)
            ?.notify(NOTIFICATION_ID, builder.build())
    }

    fun dismiss() {
        context.getSystemService(NotificationManager::class.java)
            ?.cancel(NOTIFICATION_ID)
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Active Task",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Live progress for current task or day plan" }
            context.getSystemService(NotificationManager::class.java)
                ?.createNotificationChannel(channel)
        }
    }

    private fun openAppIntent() = PendingIntent.getActivity(
        context, 0,
        Intent(context, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}
```

#### 2. Required permission in `AndroidManifest.xml`
```xml
<!-- Android 16 Live Updates / Now Bar -->
<uses-permission android:name="android.permission.POST_PROMOTED_NOTIFICATIONS" />
```

#### 3. Where to trigger it

Trigger points (none exist yet — this would be a new "focus mode" UX):

- **New voice command**: "start working on [task]" → start focus session
- **Task Detail**: "Start Focus" button that launches the live notification + timer
- **Dashboard**: long-press → "Start" option

The Now Bar integration is a natural companion to a **Phase 3b/4 focus mode** feature, not something that stands alone.

### Timeline

Requires `targetSdk 36` (Android 16). The `setRequestPromotedOngoing()` method is available now in `NotificationCompat` — it no-ops on older devices, so safe to add speculatively. Actual Now Bar appearance requires One UI 8 (S26 hardware).

---

## Summary

| Feature | Effort | When | Dependencies |
|---|---|---|---|
| AppFunctions `processRequest` | Small — 1 new Service + manifest entry | Phase 4 | `androidx.appfunctions`, Android 16 devices |
| AppFunctions `createTask` | Small — structured alternative | Phase 4 | Same |
| Now Bar / Live Updates | Medium — requires new "focus mode" UX | Phase 3b/4 | `targetSdk 36`, `POST_PROMOTED_NOTIFICATIONS` |

### Critical notes

- **AppFunctions**: The `processRequest` function that passes raw text to `VoiceCommandProcessor` is the right design — avoids duplicating NLP logic, gets full command support (not just create) for free
- **Now Bar**: Not a task list display. Works best as a focus session indicator with a timer/progress bar
- Both features are **safe to stub in now** (API exists, no-ops on older devices) but have limited reach until Android 16 / One UI 8 adoption grows
