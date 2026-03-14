# Pocket Pilot — Personal Task Hub

## Vision
A "second brain" that ingests tasks from multiple sources (voice notes, email, texts, chat), extracts action items using AI, and organizes them into projects. The user interacts primarily with **tasks organized by project**, not with individual voice notes or messages. Sources are just how items arrive.

## Current Status
**Phases 1–3c + recurring tasks complete.** Full capacity-aware scheduling, task triage, home screen widget, living task list, and recurring tasks.

### What works now:
- **App name**: Pocket Pilot
- **Dashboard**: overdue, today, upcoming, undated tasks at a glance; long-press for multi-select with batch due date/complete/rename/trash; swipe right to complete, swipe left to trash (both with undo snackbar); drag-and-drop reorder for today's tasks (long-press drag handle); triage card when tasks need review; capacity indicator (today only) tappable to Plan My Day; Today's Plan card after morning check-in; priority color bars on task cards; repeat icon on recurring tasks; compact voice command bar (Type / Voice command); top bar: Capture (mic), Search, overflow menu (Plan My Day, Triage, Trash)
- **Search**: search icon in Dashboard opens search screen; searches task names and notes with debounced input
- **Inbox**: unassigned tasks with project assignment; multi-select with batch assign/due date/rename/trash; swipe right to complete, swipe left to trash (both with undo snackbar); confirmation dialogs on all trash actions
- **Projects**: create projects, view tasks per project; undated tasks filter (filter icon); navigation badge shows undated task count; trash icon navigates to Trash screen
- **Project Detail**: tasks within a project; long-press multi-select with batch due date/rename/trash; swipe right to complete, swipe left to trash (with undo snackbar); (+) quick add; mic icon to capture into project; confirmation dialog on project delete (warns about cascading task deletion)
- **Capture**: voice recording with waveform visualization, timer, pause/resume/cancel; text input option
- **Auto-pipeline**: record → auto-transcribe (Deepgram) → auto-extract (Gemini) → review → save
- **Project creation from voice**: say "create a new project called X" during recording; AI detects intent, creates project, assigns extracted tasks
- **Voice commands**: persistent bar above bottom nav on all main screens; record or type commands; supports multiple commands in one prompt; say "create task X", "complete Y", "change due date of Z to Friday", "set drop dead date for X to July 25", "move task to project W", "delete task", "rename task", "plan my day", "I have 45 minutes", "review my tasks", "triage", "make X weekly", "set Y to repeat every 3 days"
- **Transcript-only mode**: toggle on Capture screen to skip extraction and just get a transcript
- **Task Detail**: view/edit task name, change due date, set drop-dead date (hard deadline, warning color), lock due date (prevents voice command changes), effort estimate chips (10m/20m/30m/1h/90m/2h+), set repeat (daily/weekly/monthly/yearly), change project, add notes, see source info; confirmation dialog on trash
- **Recurring tasks**: set tasks to repeat on a schedule; on completion, next instance auto-created with advanced due date; repeat icon on task cards; voice command support; each instance syncs independently to Google Tasks
- **Quick add**: manual task creation from Dashboard (+) and Project Detail (+) without voice
- **Trash**: tasks and projects moved to trash instead of deleted; trashing a project cascades to its tasks; restore or permanently delete; "Empty trash" button; accessible from Dashboard top bar and Projects screen
- **Settings**: bottom nav tab; appearance (theme mode: system/light/dark); export/import data (JSON backup); AI enrichment (bulk backfill effort estimates + tags for existing tasks); voice history with transcript viewer; Google Tasks sync toggle; Morning check-in toggle + time picker; Task nudge notifications (configurable times); Send Feedback (GitHub issue submission)
- **Task nudge notifications**: configurable nudge times in Settings; fires notifications for due-today tasks not yet completed; NudgeWorker + NudgePreferences in `reminder/`
- **Google Tasks Sync**: bi-directional sync with Google Tasks; Projects ↔ Task Lists, ActionItems ↔ Tasks; Inbox tasks sync to "AI Companion Inbox" list; on-resume + 30min WorkManager periodic sync; conflict resolution (last-writer-wins by timestamp)
- **Morning check-in notification**: Settings → Morning Check-In; toggle + hour picker; fires daily at configured time; capacity buttons (30m/1h/90m/2h/3h); tap → follow-up notification with task plan; also surfaces 2-3 stale/waiting-for tasks as review notifications with quick actions (Done, Trash/Unblock, Skip); "Review all" opens triage screen
- **Effort estimates**: `estimatedMinutes` on every task; AI-guessed at extraction, user-editable in Task Detail; enrichment batch-backfills existing tasks; unestimated = 30m default for scheduling
- **Drop-dead dates**: `dropDeadDate` separate from soft `dueDate`; voice command "set drop dead date"; Task Detail picker with warning styling
- **Context tags**: `#hashtags` in task notes; AI suggests at extraction; `#waiting-for` excluded from scheduling; displayed as chips on task cards in Dashboard/Inbox/Project Detail
- **Dynamic priority**: `effectivePriority()` auto-escalates to URGENT when drop-dead ≤1 day, or ≤3 days + effort ≥60m; HIGH when ≤7 days
- **Plan My Day**: Dashboard capacity indicator (today's planned vs capacity); tap to open PlanMyDay screen; select time + context → see recommended task list; adjusting capacity saves new plan
- **Task Triage**: guided review screen with two modes — "Needs attention" (stale 7+ days, rescheduled 3+, large undated 60+ min, #waiting-for) and "Overdue & undated" (all overdue + undated tasks); actions: done, keep, set due date, break it down (AI subtask generation), snooze 2w, toggle #waiting-for, trash; accessible via dashboard card, voice command "review my tasks", morning notification
- **Task event tracking**: records lifecycle events (created, completed, uncompleted, trashed, restored, due date changed, triaged, snoozed) in task_events table; powers triage logic and future pattern learning
- **Home screen widget**: Jetpack Glance "Today's Plan" widget; shows up to 7 tasks with completion status and effort; auto-refreshes on complete/trash/reschedule; updates every 30 min
- **Swipe undo**: swipe-to-complete and swipe-to-trash show snackbar with "Undo" button on Dashboard, Inbox, and Project Detail
- **Lock due date**: lock icon in Task Detail prevents voice commands from changing due date; `dueDateLocked` field in ActionItem
- **Priority color bars**: left-edge color bar on task cards; red = URGENT, accent = HIGH, subtle = MEDIUM
- **Visual design**: Inter font, deep-blue/coral color palette (dynamic colors disabled), flat task rows with dividers (no card wrappers), checkboxes flush at left edge, compact top bars (18sp title)
- **Theme mode**: user-selectable light/dark/system theme in Settings → Appearance; persisted via SharedPreferences (ThemePreferences); default follows system
- **Help & Features guide**: Settings → Help & Features; comprehensive in-app documentation of all features
- **Bottom nav**: Dashboard | Inbox | Projects | Settings
- **Share intent**: accepts audio/* and video/* files shared from other apps; opens Capture screen in transcript-only mode (auto-transcribes, no task extraction by default)
- Voice notes recorded within a project auto-assign extracted items to that project
- Screen stays on during recording
- **In-app updates**: Firebase App Distribution SDK checks for new builds on launch; shows native dialog to download and install without leaving the app
- **What's New dialog**: shown once after each update with release notes; version tracked via SharedPreferences; release notes maintained in `update/ReleaseNotes.kt`
- CI/CD: push to main → GitHub Actions → Firebase App Distribution

### Next phases:
- **Phase 2 (complete)**: Smarter AI — project-aware extraction, priority inference, auto-extraction, duplicate detection
- **Phase 2.5 (complete)**: Voice commands — mic button on Dashboard, Inbox, Project Detail; multi-command support
- **Phase 2.75 (complete)**: Google Tasks bi-directional sync
- **Phase 3a (complete)**: Effort estimates, drop-dead dates, context tags, AI enrichment, morning check-in notification
- **Phase 3b (complete)**: Tag chips on cards, PlanMyDay screen with context filter, dashboard capacity indicator, "plan my day" voice command
- **Phase 3c (complete)**: Morning check-in notification, task triage screen, morning review notifications with quick actions, task event tracking
- **Recurring tasks (complete)**: Repeat schedule on tasks (daily/weekly/monthly/yearly with interval), auto-create next instance on completion, voice command support
- **Phase 3d (future)**: AI pattern learning, task rot detection
- **Phase 4**: More input sources — Gmail, SMS, Google Chat

## Phase 3 — Capacity-Aware Scheduling & Living Task List

### Vision
The app becomes a true "second brain" — not just storing tasks but actively managing them. It learns your patterns, prunes stale tasks, and each morning tells you exactly what to work on given the time you have.

### Core concepts

**Effort estimation** (stored as `estimatedMinutes: Int`):
- AI estimates at extraction time from task text
- Quick-select in review UI: `< 15 min | ~30 min | ~1 hr | 2 hr+` (stored as 10/30/60/120)
- User can override on Task Detail screen
- Unestimated tasks default to 30 min for scheduling purposes

**Drop-dead date** (`dropDeadDate: Long?`):
- The "holy cow, if I haven't done it by then forget it" date — a true hard deadline
- Distinct from soft `dueDate` which is aspirational and often slips
- Dynamic priority: as drop-dead approaches + large effort → auto-escalates to URGENT
- `dueDate` is now considered a soft hint; `dropDeadDate` is the real anchor

**Context tags** (parsed from `#hashtags` in task notes):
- Common tags: `#computer`, `#errand`, `#phone-call`, `#waiting-for`, `#home`, `#quick`
- AI suggests tags at extraction time based on task text
- `#waiting-for` tasks automatically deprioritized (blocked)
- Tags stored as derived field, parsed from notes on read (no separate table needed initially)
- Enable context filtering: "I'm at my computer" → surface only `#computer` tasks

### Phase 3a — Foundation (complete)
- [x] Add `estimatedMinutes` + `dropDeadDate` to ActionItem (DB migration v5)
- [x] AI estimates effort at extraction time; strengthened prompt always returns a guess
- [x] AI suggests context tags at extraction time
- [x] Task Detail: drop-dead date picker (warning color) + effort estimate chips
- [x] AI enrichment in Settings: bulk backfill effort + tags for existing tasks
- [x] Voice command: "set drop dead date" correctly sets dropDeadDate (not dueDate)
- [x] `pickTasksForCapacity()` in TaskRepository (bin-pack, excludes #waiting-for, optional context tag filter)
- [x] "Pick my tasks for today" in-app PlanMyDay screen
- [x] Dynamic priority computation (`effectivePriority()` extension on ActionItem — drop-dead proximity auto-escalates)

### Phase 3b — Context & Tags (complete)
- [x] Parse `#tags` from notes (`parsedTags()` extension on ActionItem), display as chips on task cards (Dashboard/Inbox/ProjectDetail)
- [x] Context filter on PlanMyDay screen (Anywhere / Computer / Home / Errands / Phone / Quick)
- [x] Dashboard capacity indicator: "Today: Xm planned / Yh capacity" — tappable to PlanMyDay; red when overloaded
- [x] Voice command: "plan my day", "I have 45 minutes today" → navigates to PlanMyDay screen
- Capacity source: last morning check-in; adjustable on PlanMyDay screen (saves new plan → Dashboard updates)
- New key files: `ui/plan/PlanMyDayScreen.kt`, `ui/plan/PlanMyDayViewModel.kt`, `ui/common/TagChips.kt`

### Phase 3c — Living Task List (complete)
- [x] Morning notification: capacity buttons (30m/1h/90m/2h/3h); follow-up shows task plan
- [x] Settings: toggle + hour picker for notification time
- [x] MorningCheckInWorker (WorkManager daily periodic), MorningActionReceiver (BroadcastReceiver)
- [x] Stale/waiting-for task review in morning notification: 2-3 tasks with quick actions (Done, Trash/Unblock, Skip) + "Review all" opens triage
- [x] Task Triage screen: guided card-by-card review with AI breakdown, snooze, set due date, toggle #waiting-for
- [x] Task event tracking: task_events table records lifecycle (created, completed, trashed, restored, due_date_changed, triaged, snoozed)
- [x] Dashboard triage card: "X tasks need review" prompt
- [x] Voice command: "review my tasks" / "triage" → navigates to Task Triage screen
- [ ] Track task completion patterns to improve future recommendations

### Phase 3d — AI Pattern Learning (future)
- [ ] Store behavioral patterns (which tasks get done, which rot, time-of-day preferences)
- [ ] Storage: local Room table initially; long-term consider Google Drive JSON (app is Google-centric)
- [ ] AI learns: "Dan never does #computer tasks on weekends", "finance tasks take 2x estimates"
- [ ] Periodic "task rot" detection: surfaces tasks untouched for 2+ weeks with a nudge

### Data model additions (Phase 3a)
```
ActionItem gains:
  estimatedMinutes: Int = 0        // 0 = unestimated; AI-guessed or user-set
  dropDeadDate: Long? = null       // hard deadline; null = no hard deadline
  // tags parsed dynamically from notes (#hashtag) — no schema change needed
```

### "Pick my tasks" algorithm
1. Lock tasks with drop-dead date (fixed anchors, sort by proximity)
2. Filter by requested context (if provided)
3. Exclude `#waiting-for` tasks
4. Rank remaining: drop-dead proximity → priority → effort fit within remaining capacity
5. Pack into capacity budget (bin-packing, greedy)
6. Present as ordered list with total time shown; user can swap items

### Key UX principles
- **Zero friction at capture** — estimatedMinutes and tags are AI-guessed, never required
- **Drop-dead date only when it truly matters** — don't encourage soft due dates
- **Morning check-in is opt-in** — power feature, not annoying default
- **AI decides, human approves** — always show proposed plan before committing due dates

## Project Overview
- **Package**: com.example.aicompanion
- **Stack**: Kotlin, Jetpack Compose, Material 3, Room, Compose Navigation
- **GCP Project**: transcription-app-481721 (project number: 809575369316)
- **Min SDK**: See app/build.gradle.kts

## Build & Run
```bash
./gradlew assembleDebug          # Build debug APK
./gradlew installDebug           # Install on connected device
./gradlew test                   # Run unit tests
./gradlew connectedAndroidTest   # Run instrumented tests
```

## Architecture
- Single-activity app (MainActivity) with Compose Navigation
- Manual DI via `AppContainer` in `di/` (no Hilt)
- WorkManager for hourly reminder checks, 30-min Google Tasks sync, and daily morning check-in

### Data Model
```
Project (id, name, color, icon, sortOrder, isArchived, isTrashed, createdAt, googleTaskListId, syncVersion)
ActionItem (id, projectId, sourceId, text, notes, dueDate, dropDeadDate, dueDateLocked, priority, estimatedMinutes, isCompleted, completedAt, reminderFired, isTrashed, createdAt, updatedAt, googleTaskId, googleTaskListId, syncVersion, recurrenceRule, recurrenceInterval, todaySortOrder)
TaskEvent (id, taskId, eventType, timestamp, projectId, tags, estimatedMinutes, metadata)
Source (id, type[VOICE_NOTE|EMAIL|CHAT|SMS|MANUAL], rawContent, sourceRef, processedAt, createdAt)
SyncState (id=1, lastSyncTimestamp, lastSyncedVersion, inboxTaskListId, syncEnabled, googleAccountEmail)
```
- ActionItems with projectId=null live in the **Inbox**
- ActionItems/Projects with isTrashed=true live in the **Trash** (soft delete)
- Sources track provenance (where a task came from)
- Projects organize tasks by life area (Work, Home, Health, etc.)
- App version: 1.2 (versionCode 3) — bump versionCode for each release and add entry to `update/ReleaseNotes.kt`
- DB version: 9 (proper migrations — schema exported to `app/schemas/`, no more destructive fallback)
- v5 adds: `estimatedMinutes INT NOT NULL DEFAULT 0`, `dropDeadDate INTEGER` to action_items
- v6 adds: `task_events` table for lifecycle tracking
- v8 adds: `recurrenceRule TEXT`, `recurrenceInterval INTEGER NOT NULL DEFAULT 1` to action_items
- v9 adds: `todaySortOrder INTEGER` to action_items (manual drag-and-drop sort for today section)
- v7 adds: `dueDateLocked INTEGER NOT NULL DEFAULT 0` to action_items
- Sync fields: `googleTaskId`/`googleTaskListId` link to Google Tasks API; `syncVersion` tracks dirty items for incremental sync
- Firebase Crashlytics: enabled when `google-services.json` present (conditional plugin apply)

### Screen Flow
- **Dashboard** — overdue, today, upcoming, undated tasks; recently completed section; triage card; capacity indicator (today only); Today's Plan card; long-press → multi-select mode; swipe gestures with undo; compact voice command bar; top bar: Capture, Search, overflow (Plan My Day, Triage, Trash)
- **Inbox** — unassigned tasks; long-press → multi-select mode; swipe gestures with undo
- **Projects** — list of projects with task counts; undated filter; trash icon → Trash screen
- **Project Detail** — tasks within a project; long-press → multi-select mode; swipe gestures with undo; (+) quick add; mic capture; confirmation on project/task trash
- **Capture** — voice note recording/transcription/extraction; text input option; accessed via Dashboard top bar or share intent
- **Task Detail** — view/edit a single task; lock due date; effort chips; repeat schedule (daily/weekly/monthly/yearly); confirmation on trash
- **Plan My Day** — capacity selection + context filter → AI-picked task list
- **Task Triage** — guided card-by-card review of tasks needing attention; AI breakdown
- **Settings** — bottom nav tab; export/import data; Google Tasks sync; AI enrichment; voice history; morning check-in; Help & Features; Send Feedback
- **Feedback** — submit bugs/feature requests as GitHub Issues; optional screenshot attachment; auto-populated device info
- **Help & Features** — comprehensive in-app documentation of all features
- **Trash** — trashed tasks and projects; restore or permanently delete
- Bottom navigation: Dashboard | Inbox | Projects | Settings

## Key Packages
- `auth/` - Google Sign-In via Credential Manager (dormant, for future Gmail Phase 3)
- `data/sync/` - Google Tasks bi-directional sync: TokenManager, GoogleTasksApiClient, SyncEngine, SyncWorker, SyncMappers
- `audio/` - MediaRecorder wrapper (AudioRecorder), transcript file helpers
- `network/` - TranscriptionClient (Deepgram), GeminiClient (extraction), GitHubIssuesClient (feedback)
- `data/local/` - Room DB, DAOs, entities, type converters
- `data/repository/` - TaskRepository (replaces CaptureRepository)
- `domain/extraction/` - Action item extraction from text
- `domain/model/` - DashboardState, ProjectSummary
- `ui/dashboard/` - Dashboard screen (multi-select)
- `ui/inbox/` - Inbox screen (multi-select)
- `ui/projects/` - Projects list + detail (multi-select in detail)
- `ui/capture/` - Capture screen (voice notes + future sources)
- `ui/task/` - Task detail/edit screen
- `ui/voicecommand/` - Persistent voice command bar + ViewModel (record → transcribe → parse → execute); text input mode
- `domain/command/` - VoiceCommand sealed class, VoiceCommandProcessor (Gemini parsing + task execution, multi-command support; supports set_drop_dead_date)
- `reminder/` - NotificationHelper, ReminderWorker (hourly due-date reminders), MorningCheckInWorker, MorningActionReceiver, MorningPreferences, MorningNotificationHelper, NudgeWorker, NudgePreferences
- `ui/plan/` - Plan My Day screen + ViewModel (capacity selection, context filter, AI task picking)
- `ui/triage/` - Task Triage screen + ViewModel + models (guided review, AI breakdown)
- `ui/theme/` - Custom theme: Inter font (res/font/), deep-blue/coral palette (Color.kt, Type.kt, Theme.kt); ThemePreferences (system/light/dark mode via SharedPreferences); dynamic colors disabled so custom palette is always visible
- `ui/common/` - Shared composables (TagChips, DateTagsRow)
- `ui/search/` - Search screen (task name + notes search)
- `ui/feedback/` - FeedbackScreen + FeedbackViewModel (in-app feedback → GitHub Issues)
- `ui/settings/` - Settings screen (export/import, AI enrichment, voice history, Google Tasks sync, morning check-in); HelpScreen (feature guide)
- `ui/trash/` - Trash screen (restore / permanent delete)
- `update/` - In-app update check (Firebase App Distribution SDK), What's New dialog + preferences, release notes
- `widget/` - TodayPlanWidget (Jetpack Glance home screen widget)

## Transcription
- **Calls Deepgram API directly** from the Android app (no cloud function middleman)
- Deepgram Nova-3 with diarize=true, paragraphs=true, smart_format=true
- API key stored in `local.properties` as `DEEPGRAM_API_KEY` (gitignored)
- Exposed via `BuildConfig.DEEPGRAM_API_KEY` at build time
- No file size limit (Deepgram supports up to 2GB)
- Audio is streamed to Deepgram without buffering entirely in memory

## Action Item Extraction
- Uses **Gemini 2.0 Flash** via REST API (`generativelanguage.googleapis.com`), temperature 0.1
- API key stored in `local.properties` as `GEMINI_API_KEY` (gitignored)
- Exposed via `BuildConfig.GEMINI_API_KEY` at build time
- `GeminiExtractor` is the primary implementation; `HeuristicExtractor` is a fallback
- Extraction is automatic: record → transcribe → extract (full pipeline on stop, unless transcript-only mode)
- **Phase 2 (done)**: prompt includes existing project names for smart project suggestion + priority inference from language cues + duplicate detection at review time
- Extracted items include `suggestedProject` (mapped to project ID on save) and `priority` (NONE/LOW/MEDIUM/HIGH/URGENT)
- **Project creation from voice**: Gemini detects "create a new project called X" intent, returns `newProject` name; CaptureViewModel creates the project on save and assigns tasks
- When recording from a project, all items auto-assign to that project (overrides AI suggestion)

## Voice Command Parsing
- Uses **Gemini 3 Flash Preview** (`gemini-3-flash-preview`) — separate from extraction model
- Temperature 1.0 (Google's recommendation for Gemini 3), `thinkingLevel: "low"`, native JSON mode (`responseMimeType: "application/json"`)
- Supports multiple commands in one prompt (returns JSON array, deduplicated before execution)
- With thinking enabled, response `parts` array has thought part(s) before the text part — parser iterates to find the last text part
- Persistent VoiceCommandBar at nav level survives screen navigation; supports both voice recording and text input
- Voice command prompt supports: set_drop_dead_date, review_tasks, set_recurrence (with recurrenceRule and recurrenceInterval fields)

## Google Tasks Sync
- Bi-directional sync: Projects ↔ Task Lists, ActionItems ↔ Tasks
- Inbox tasks (projectId=null) sync to a dedicated "AI Companion Inbox" Google Tasks list
- Auth via `GoogleSignIn` + `GoogleAuthUtil.getToken()` for OAuth2 access tokens (scope: `tasks`)
- REST API calls via OkHttp (consistent with Deepgram/Gemini pattern)
- Sync triggers: on app resume (debounced 60s) + WorkManager every 30 min (requires network)
- Dirty tracking: `syncVersion` on ActionItem/Project incremented on every local mutation
- Conflict resolution: last-writer-wins by timestamp; ties → remote wins
- Initial sync merges by name (projects) and title (tasks); no data deleted
- Fields NOT synced: priority, color, icon, recurrenceRule, recurrenceInterval, dropDeadDate, estimatedMinutes, dueDateLocked, todaySortOrder (app-only); Google Tasks position, parent (ignored)
- Recurring task instances sync independently (Google Tasks API has no recurrence support)
- Moving a task between projects = delete from old Google Tasks list + create in new (API limitation)
- `SyncState` table tracks: last sync time, version watermark, inbox list ID, enabled flag, account email

## Git Workflow
- Remote uses **SSH** (`git@github.com:danpaul79/android-app.git`) — required for Claude Code to push without credential prompts
- SSH key (`~/.ssh/id_rsa`) is registered on GitHub
- `gh` CLI is authenticated; token may need refresh with `gh auth login` periodically
- Always push to `main` — CI triggers automatically on push
- `git push` must be run **synchronously** (with a timeout) — background push hangs waiting for input

## CI/CD
- **GitHub Actions** workflow at `.github/workflows/build-and-distribute.yml`
- Triggers on push to `main`, builds release APK, uploads to **Firebase App Distribution**
- Signing config in `app/build.gradle.kts` reads from env vars (CI) or `local.properties` (local)
- **Feedback processor** workflow at `.github/workflows/feedback-processor.yml` — triggers on new issues labeled as feedback; uses Claude to analyze and auto-implement fixes
- Required GitHub Secrets: `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`, `DEEPGRAM_API_KEY`, `GEMINI_API_KEY`, `GH_ISSUES_PAT` (GitHub PAT for feedback issues; mapped to `GITHUB_PAT` in local.properties), `FIREBASE_SERVICE_ACCOUNT`, `FIREBASE_APP_ID`, `GOOGLE_SERVICES_JSON` (optional, enables Crashlytics), `ANTHROPIC_API_KEY` (for feedback processor)

## Cloud Functions (legacy, no longer used by mobile app)
- **`stream-drive-file-to-deepgram`** - Used by Apps Script pipeline only. Do NOT modify.
- **`stream-audio-to-deepgram`** - Was used before direct Deepgram integration. Legacy.

## Auth
- GoogleAuthManager uses `GetSignInWithGoogleOption` (Credential Manager API)
- Web Client ID: `809575369316-gntgmi8hd2m4rcd8danc15r0oa47ij17.apps.googleusercontent.com`
- **Not currently used** — will be re-enabled for Gmail integration in Phase 3

## Audio Recording
- MediaRecorder: AAC codec, 44.1kHz, .m4a format
- Pause/resume support (API 24+)
- Real-time amplitude monitoring for waveform visualization
- Recording timer (mm:ss) with color-coded states
- Cancel button deletes partial recording file
- Keep screen on during recording (via `View.keepScreenOn`)
- Files saved to app's external files dir under `recordings/`
- File picker supports `audio/*` MIME types
- Transcripts saved as .txt alongside audio + to Downloads/Pocket Pilot
- Raw Deepgram JSON also saved alongside for future analysis

## Related Projects
- **transcription-appScript**: `C:\repos\github_personal\transcription-appScript`
  - Apps Script + Cloud Function system for meeting transcription from Google Drive
  - Uses the same GCP project and Deepgram account
  - Has its own CLAUDE.md with full details
