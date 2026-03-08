# AI Companion Android App

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
- Screens: Home → Record → Detail
- Manual DI via `AppContainer` in `di/` (no Hilt)
- Room DB: `VoiceNote` + `ActionItem` entities with FK relationship
- `ActionItemExtractor` interface with `HeuristicExtractor` impl (swappable for LLM later)
- WorkManager for hourly reminder checks

## Key Packages
- `auth/` - Google Sign-In via Credential Manager (GoogleAuthManager)
- `audio/` - MediaRecorder wrapper (AudioRecorder), transcript file helpers
- `network/` - TranscriptionClient (calls Cloud Function)
- `data/local/` - Room DB, DAOs, entities
- `data/repository/` - CaptureRepository
- `domain/extraction/` - Action item extraction from transcripts
- `ui/home/` - Home screen (list of voice notes)
- `ui/record/` - Record screen (record/pick audio, transcribe, save)
- `ui/detail/` - Detail screen (view note + action items)

## Cloud Functions
- **`stream-audio-to-deepgram`** - Accepts direct audio upload, forwards to Deepgram
  - URL: `https://us-central1-transcription-app-481721.cloudfunctions.net/stream-audio-to-deepgram`
  - Currently **publicly accessible** (--allow-unauthenticated) as a workaround
  - OAuth flow was not working from the Android app, so auth was removed
  - Deepgram Nova-3 with diarize=true, paragraphs=true, smart_format=true
  - us-central1, gen2, python312, 1GiB memory, 540s timeout
- **`stream-drive-file-to-deepgram`** - Existing function used by Apps Script pipeline
  - This one should NOT be modified — it's used by the transcription-appScript project
  - May need a separate copy of stream-audio-to-deepgram for the mobile app
    to avoid conflicts with the Apps Script usage

## Auth Status
- GoogleAuthManager uses `GetSignInWithGoogleOption` (Credential Manager API)
- Web Client ID: `809575369316-gntgmi8hd2m4rcd8danc15r0oa47ij17.apps.googleusercontent.com`
- Auth manager is initialized in AppContainer but **not currently used** for transcription
- The transcription flow sends audio directly without an Authorization header
- TODO: Either re-enable auth with a dedicated cloud function, or add API key auth

## Audio Recording
- MediaRecorder: AAC codec, 44.1kHz, .m4a format
- Files saved to app's external files dir under `recordings/`
- File picker supports `audio/*` MIME types
- Transcripts saved as .txt alongside audio files with timestamps
- Raw Deepgram JSON also saved alongside for future analysis

## Related Projects
- **transcription-appScript**: `C:\repos\github_personal\transcription-appScript`
  - Apps Script + Cloud Function system for meeting transcription from Google Drive
  - Uses the same GCP project and Deepgram account
  - Has its own CLAUDE.md with full details
