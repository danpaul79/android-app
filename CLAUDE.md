# AI Companion — Personal Task Hub

## Vision
A "second brain" that ingests tasks from multiple sources (voice notes, email, texts, chat), extracts action items using AI, and organizes them into projects. The user interacts primarily with **tasks organized by project**, not with individual voice notes or messages. Sources are just how items arrive.

## Current Status
**Phase 1+ complete.** Core task hub is fully operational with multi-select, trash, and batch editing. See `RESTRUCTURE_PLAN.md` for the full plan.

### What works now:
- **Dashboard**: overdue, today, upcoming tasks at a glance; long-press for multi-select with batch due date/complete/rename/trash; swipe right to complete, swipe left to trash; search, trash, and settings icons in top bar
- **Search**: search icon in Dashboard opens search screen; searches task names and notes with debounced input
- **Inbox**: unassigned tasks with project assignment; multi-select with batch assign/due date/rename/trash; confirmation dialogs on all trash actions
- **Projects**: create projects, view tasks per project; trash icon navigates to Trash screen
- **Project Detail**: tasks within a project; long-press multi-select with batch due date/rename/trash; confirmation dialog on project delete (warns about cascading task deletion)
- **Capture**: voice recording with waveform visualization, timer, pause/resume/cancel; text input option
- **Auto-pipeline**: record → auto-transcribe (Deepgram) → auto-extract (Gemini) → review → save
- **Project creation from voice**: say "create a new project called X" during recording; AI detects intent, creates project, assigns extracted tasks
- **Voice commands**: persistent bar above bottom nav on all main screens; record or type commands; supports multiple commands in one prompt; say "create task X", "complete Y", "change due date of Z to Friday", "move task to project W", "delete task", "rename task"
- **Transcript-only mode**: toggle on Capture screen to skip extraction and just get a transcript
- **Task Detail**: view/edit task name, change due date, change project, add notes, see source info; confirmation dialog on trash
- **Quick add**: manual task creation from Dashboard (+) and Project Detail (+) without voice
- **Trash**: tasks and projects moved to trash instead of deleted; trashing a project cascades to its tasks; restore or permanently delete; "Empty trash" button; accessible from Dashboard top bar and Projects screen
- **Settings**: gear icon in Dashboard; export/import data (JSON backup); voice history with transcript viewer (voice notes + voice command logs with transcript and actions taken)
- **Bottom nav**: Dashboard | Inbox | Capture | Projects
- Voice notes recorded within a project auto-assign extracted items to that project
- Screen stays on during recording
- CI/CD: push to main → GitHub Actions → Firebase App Distribution

### Next phases:
- **Phase 2 (complete)**: Smarter AI — project-aware extraction, priority inference, auto-extraction, duplicate detection (highlights similar existing tasks in review UI)
- **Phase 2.5 (complete)**: Voice commands — mic button on Dashboard, Inbox, Project Detail; record → transcribe → AI parses command → executes
- **Phase 3**: More input sources — Gmail, SMS, Google Chat

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
- WorkManager for hourly reminder checks

### Data Model
```
Project (id, name, color, icon, sortOrder, isArchived, isTrashed, createdAt)
ActionItem (id, projectId, sourceId, text, notes, dueDate, priority, isCompleted, completedAt, reminderFired, isTrashed, createdAt, updatedAt)
Source (id, type[VOICE_NOTE|EMAIL|CHAT|SMS|MANUAL], rawContent, sourceRef, processedAt, createdAt)
```
- ActionItems with projectId=null live in the **Inbox**
- ActionItems/Projects with isTrashed=true live in the **Trash** (soft delete)
- Sources track provenance (where a task came from)
- Projects organize tasks by life area (Work, Home, Health, etc.)
- DB version: 3 (proper migrations — schema exported to `app/schemas/`, no more destructive fallback)
- Firebase Crashlytics: enabled when `google-services.json` present (conditional plugin apply)

### Screen Flow
- **Dashboard** — overdue, today, upcoming tasks; recently completed section; long-press → multi-select mode; top bar: search, trash, settings
- **Inbox** — unassigned tasks; long-press → multi-select mode
- **Projects** — list of projects with task counts; trash icon → Trash screen
- **Project Detail** — tasks within a project; long-press → multi-select mode; confirmation on project/task trash
- **Capture** — voice note recording/transcription/extraction; text input option
- **Task Detail** — view/edit a single task; confirmation on trash
- **Settings** — export/import data; voice history (voice notes + command logs) with transcript viewer
- **Trash** — trashed tasks and projects; restore or permanently delete
- Bottom navigation: Dashboard | Inbox | Capture | Projects

## Key Packages
- `auth/` - Google Sign-In via Credential Manager (dormant, needed for Gmail in Phase 3)
- `audio/` - MediaRecorder wrapper (AudioRecorder), transcript file helpers
- `network/` - TranscriptionClient (Deepgram), GeminiClient (extraction)
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
- `domain/command/` - VoiceCommand sealed class, VoiceCommandProcessor (Gemini parsing + task execution, multi-command support)
- `ui/search/` - Search screen (task name + notes search)
- `ui/settings/` - Settings screen (export/import, voice history incl. command logs, transcript viewer)
- `ui/trash/` - Trash screen (restore / permanent delete)

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
- Required GitHub Secrets: `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`, `DEEPGRAM_API_KEY`, `GEMINI_API_KEY`, `FIREBASE_SERVICE_ACCOUNT`, `FIREBASE_APP_ID`, `GOOGLE_SERVICES_JSON` (optional, enables Crashlytics)

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
- Transcripts saved as .txt alongside audio + to Downloads/AI Companion
- Raw Deepgram JSON also saved alongside for future analysis

## Related Projects
- **transcription-appScript**: `C:\repos\github_personal\transcription-appScript`
  - Apps Script + Cloud Function system for meeting transcription from Google Drive
  - Uses the same GCP project and Deepgram account
  - Has its own CLAUDE.md with full details
