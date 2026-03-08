# AI Companion — Personal Task Hub

## Vision
A "second brain" that ingests tasks from multiple sources (voice notes, email, texts, chat), extracts action items using AI, and organizes them into projects. The user interacts primarily with **tasks organized by project**, not with individual voice notes or messages. Sources are just how items arrive.

## Current Status
**Phase 1 complete.** The app has been restructured from a voice-note-centric tool into a project-based task hub. See `RESTRUCTURE_PLAN.md` for the full plan.

### What works now (Phase 1):
- **Dashboard**: overdue, today, upcoming tasks at a glance
- **Inbox**: unassigned tasks with project assignment dropdown
- **Projects**: create projects, view tasks per project, voice capture within a project
- **Capture**: voice recording with waveform visualization, timer, pause/resume/cancel
- **Auto-pipeline**: record → auto-transcribe (Deepgram) → auto-extract (Gemini) → save
- **Task Detail**: view/edit task, change project, add notes, see source info
- **Bottom nav**: Dashboard | Inbox | Capture | Projects
- Voice notes recorded within a project auto-assign extracted items to that project
- Screen stays on during recording
- CI/CD: push to main → GitHub Actions → Firebase App Distribution

### Next phases:
- **Phase 2**: Smarter AI — project-aware extraction prompts, priority inference, duplicate detection
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
Project (id, name, color, icon, sortOrder, isArchived, createdAt)
ActionItem (id, projectId, sourceId, text, notes, dueDate, priority, isCompleted, completedAt, reminderFired, createdAt, updatedAt)
Source (id, type[VOICE_NOTE|EMAIL|CHAT|SMS|MANUAL], rawContent, sourceRef, processedAt, createdAt)
```
- ActionItems with projectId=null live in the **Inbox**
- Sources track provenance (where a task came from)
- Projects organize tasks by life area (Work, Home, Health, etc.)

### Screen Flow
- **Dashboard** — overdue, today, upcoming tasks at a glance
- **Inbox** — unassigned tasks waiting for project assignment
- **Projects** — list of projects with task counts
- **Project Detail** — tasks within a project
- **Capture** — voice note recording/transcription/extraction (future: other sources)
- **Task Detail** — view/edit a single task
- Bottom navigation: Dashboard | Inbox | Capture | Projects

## Key Packages
- `auth/` - Google Sign-In via Credential Manager (dormant, needed for Gmail in Phase 3)
- `audio/` - MediaRecorder wrapper (AudioRecorder), transcript file helpers
- `network/` - TranscriptionClient (Deepgram), GeminiClient (extraction)
- `data/local/` - Room DB, DAOs, entities, type converters
- `data/repository/` - TaskRepository (replaces CaptureRepository)
- `domain/extraction/` - Action item extraction from text
- `domain/model/` - DashboardState, ProjectSummary
- `ui/dashboard/` - Dashboard screen
- `ui/inbox/` - Inbox screen
- `ui/projects/` - Projects list + detail
- `ui/capture/` - Capture screen (voice notes + future sources)
- `ui/task/` - Task detail/edit screen

## Transcription
- **Calls Deepgram API directly** from the Android app (no cloud function middleman)
- Deepgram Nova-3 with diarize=true, paragraphs=true, smart_format=true
- API key stored in `local.properties` as `DEEPGRAM_API_KEY` (gitignored)
- Exposed via `BuildConfig.DEEPGRAM_API_KEY` at build time
- No file size limit (Deepgram supports up to 2GB)
- Audio is streamed to Deepgram without buffering entirely in memory

## Action Item Extraction
- Uses **Gemini 2.0 Flash** via REST API (`generativelanguage.googleapis.com`)
- API key stored in `local.properties` as `GEMINI_API_KEY` (gitignored)
- Exposed via `BuildConfig.GEMINI_API_KEY` at build time
- `GeminiExtractor` is the primary implementation; `HeuristicExtractor` is a fallback
- Extraction is automatic: record → transcribe → extract (full pipeline on stop)
- **Phase 2**: prompt will include existing project names for smart assignment, priority extraction, duplicate detection

## CI/CD
- **GitHub Actions** workflow at `.github/workflows/build-and-distribute.yml`
- Triggers on push to `main`, builds release APK, uploads to **Firebase App Distribution**
- Signing config in `app/build.gradle.kts` reads from env vars (CI) or `local.properties` (local)
- Required GitHub Secrets: `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`, `DEEPGRAM_API_KEY`, `GEMINI_API_KEY`, `FIREBASE_SERVICE_ACCOUNT`, `FIREBASE_APP_ID`

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
