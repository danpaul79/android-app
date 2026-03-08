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
- `ActionItemExtractor` interface with `GeminiExtractor` impl (uses Gemini 2.0 Flash via REST API)
- WorkManager for hourly reminder checks

## Key Packages
- `auth/` - Google Sign-In via Credential Manager (GoogleAuthManager)
- `audio/` - MediaRecorder wrapper (AudioRecorder), transcript file helpers
- `network/` - TranscriptionClient (Deepgram), GeminiClient (action item extraction)
- `data/local/` - Room DB, DAOs, entities
- `data/repository/` - CaptureRepository
- `domain/extraction/` - Action item extraction from transcripts
- `ui/home/` - Home screen (list of voice notes)
- `ui/record/` - Record screen (record/pick audio, transcribe, save)
- `ui/detail/` - Detail screen (view note + action items)

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
- Extracts action items + topic from transcripts via `GeminiClient`
- `HeuristicExtractor` still available as fallback (regex-based, no API needed)
- Extraction is user-triggered (button), not automatic after transcription

## CI/CD
- **GitHub Actions** workflow at `.github/workflows/build-and-distribute.yml`
- Triggers on push to `main`, builds release APK, uploads to **Firebase App Distribution**
- Signing config in `app/build.gradle.kts` reads from env vars (CI) or `local.properties` (local)
- See plan file for full Firebase setup instructions
- Required GitHub Secrets: `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`, `DEEPGRAM_API_KEY`, `GEMINI_API_KEY`, `FIREBASE_SERVICE_ACCOUNT`, `FIREBASE_APP_ID`

## Cloud Functions (legacy, no longer used by mobile app)
- **`stream-audio-to-deepgram`** - Was used before direct Deepgram integration
  - Had 32MB request body limit from Cloud Functions platform
- **`stream-drive-file-to-deepgram`** - Used by Apps Script pipeline only
  - This one should NOT be modified — it's used by the transcription-appScript project

## Auth Status
- GoogleAuthManager uses `GetSignInWithGoogleOption` (Credential Manager API)
- Web Client ID: `809575369316-gntgmi8hd2m4rcd8danc15r0oa47ij17.apps.googleusercontent.com`
- Auth manager is initialized in AppContainer but **not currently used** for transcription

## Audio Recording
- MediaRecorder: AAC codec, 44.1kHz, .m4a format
- Files saved to app's external files dir under `recordings/`
- File picker supports `audio/*` MIME types
- Transcripts saved as .txt alongside audio files with timestamps (named after source file)
- Raw Deepgram JSON also saved alongside for future analysis
- Transcripts also saved to Downloads/AI Companion for easy access
- Share button available to share transcript via any app

## Related Projects
- **transcription-appScript**: `C:\repos\github_personal\transcription-appScript`
  - Apps Script + Cloud Function system for meeting transcription from Google Drive
  - Uses the same GCP project and Deepgram account
  - Has its own CLAUDE.md with full details
