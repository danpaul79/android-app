# Restructure Plan: Voice Note App → Personal Task Hub

## Status: Phase 1 COMPLETE

Phase 1 has been implemented. The app now has:
- Project, ActionItem, Source entities (VoiceNote entity removed)
- Dashboard, Inbox, Projects, Capture, Task Detail screens
- Bottom navigation (Dashboard | Inbox | Capture | Projects)
- Voice recording with waveform visualization, timer, pause/resume, cancel
- Auto-transcription (Deepgram) immediately after recording stops
- Auto-extraction (Gemini) immediately after transcription
- Voice notes from within a project auto-assign items to that project
- Keep screen on during recording

## Overview
Transform the app from a voice-note-centric tool into a project-based task management hub ("second brain") that ingests tasks from multiple sources.

**Core principle**: Action items are the primary entity. Sources (voice notes, emails, texts) are just how items arrive. Projects are how you organize your life.

---

## Phase 1: Data Model + Core UI Restructure (DONE)

### 1. New Room Entities

**Project** — `data/local/entity/Project.kt` (new)
```kotlin
@Entity(tableName = "projects")
data class Project(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val color: Int = 0xFF6200EE.toInt(),
    val icon: String = "folder",
    val sortOrder: Int = 0,
    val isArchived: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
```

**Source** — `data/local/entity/Source.kt` (new)
```kotlin
@Entity(tableName = "sources")
data class Source(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: SourceType,
    val rawContent: String,
    val sourceRef: String? = null,  // file path, email ID, etc.
    val processedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)

enum class SourceType { VOICE_NOTE, EMAIL, CHAT, SMS, MANUAL }
```

**ActionItem** — `data/local/entity/ActionItem.kt` (modify)
- Remove `voiceNoteId` FK
- Add `projectId` FK (nullable, null = inbox)
- Add `sourceId` FK (nullable)
- Add `notes`, `priority`, `completedAt`, `updatedAt` fields
- Add `Priority` enum: NONE, LOW, MEDIUM, HIGH, URGENT

### 2. Migration Strategy
- **Destructive migration** (app is pre-release, personal use)
- Bump DB version to 2, add `fallbackToDestructiveMigration()`
- Remove VoiceNote entity entirely
- Add Converters for SourceType and Priority enums

### 3. New DAOs

**ProjectDao** (new):
- insert, update, getAll (non-archived), getById, archive, delete

**SourceDao** (new):
- insert, getById, getByActionItemId

**ActionItemDao** (modify):
- Keep: insertAll, setCompleted, getUpcomingUnfired, markReminderFired, deleteById
- Add: insert (single), update, getInboxItems, getByProjectId, getTodayItems, getOverdueItems, getUpcomingItems, assignToProject, getCountByProjectId

**Remove**: VoiceNoteDao

### 4. New Repository

**TaskRepository** (replaces CaptureRepository):
- getInboxItems, getTodayItems, getOverdueItems, getUpcomingItems
- getItemsByProject, getAllProjects, createProject
- assignItemToProject, toggleCompleted
- saveFromSource(source, items) — inserts source then items with sourceId
- createManualTask, updateTask, deleteTask
- Reminder methods (carried forward)

### 5. New Screens

| Screen | Package | Purpose |
|---|---|---|
| Dashboard | `ui/dashboard/` | Overdue, today, upcoming tasks at a glance |
| Inbox | `ui/inbox/` | Unassigned tasks, assign to projects |
| Projects | `ui/projects/` | Project list + project detail (tasks in project) |
| Capture | `ui/capture/` (or keep `ui/record/`) | Voice note input, saves as Source + ActionItems |
| Task Detail | `ui/task/` | View/edit single task: text, notes, due, priority, project |

**Bottom nav**: Dashboard | Inbox | Capture (+/mic) | Projects

**Remove**: `ui/home/`, `ui/detail/` (replaced by Dashboard and Task Detail)

### 6. Files to Create
- `data/local/entity/Project.kt`
- `data/local/entity/Source.kt`
- `data/local/Converters.kt`
- `data/local/dao/ProjectDao.kt`
- `data/local/dao/SourceDao.kt`
- `data/local/relation/ProjectWithActionItems.kt`
- `data/local/relation/ActionItemWithSource.kt`
- `data/repository/TaskRepository.kt`
- `domain/model/DashboardState.kt`
- `ui/dashboard/DashboardScreen.kt` + `DashboardViewModel.kt`
- `ui/inbox/InboxScreen.kt` + `InboxViewModel.kt`
- `ui/projects/ProjectsScreen.kt` + `ProjectDetailScreen.kt` + ViewModels
- `ui/task/TaskDetailScreen.kt` + `TaskDetailViewModel.kt`

### 7. Files to Modify
- `data/local/entity/ActionItem.kt` — new fields and FKs
- `data/local/dao/ActionItemDao.kt` — new queries
- `data/local/AppDatabase.kt` — new entities, version 2, converters
- `di/AppContainer.kt` — wire TaskRepository
- `ui/navigation/NavRoutes.kt` — new routes
- `ui/navigation/AppNavHost.kt` — bottom nav + new screens
- `ui/record/RecordViewModel.kt` — save via TaskRepository
- `ui/record/RecordScreen.kt` — adapt to new flow
- `reminder/ReminderWorker.kt` — point to TaskRepository
- `domain/extraction/ExtractedItem.kt` — add priority, suggestedProject

### 8. Files to Delete
- `data/local/entity/VoiceNote.kt`
- `data/local/dao/VoiceNoteDao.kt`
- `data/local/relation/VoiceNoteWithActionItems.kt`
- `data/repository/CaptureRepository.kt`
- `domain/model/TopicGroup.kt`
- `ui/home/HomeScreen.kt` + `HomeViewModel.kt`
- `ui/detail/DetailScreen.kt` + `DetailViewModel.kt`

### 9. Implementation Order
1. Type converters
2. New entities (Project, Source)
3. Modify ActionItem entity
4. New DAOs (ProjectDao, SourceDao) + modify ActionItemDao
5. Update AppDatabase
6. Create TaskRepository
7. Update AppContainer
8. New navigation routes
9. Dashboard screen
10. Inbox screen
11. Projects screens
12. Modify Capture/Record screen
13. Task Detail screen
14. Wire up AppNavHost with bottom nav
15. Update ReminderWorker
16. Delete old files
17. End-to-end test

---

## Phase 2: Smarter AI

### Enhanced Extraction
- Prompt includes existing project names → AI suggests project assignment
- Extract priority from context ("urgent", "ASAP", "when you get a chance")
- Return richer JSON with `priority` and `suggestedProject` fields

### Duplicate Detection
- New GeminiClient method: compare new items against existing items
- Show user: "This looks similar to existing task X — skip?"

### Updated Interface
```kotlin
interface ActionItemExtractor {
    suspend fun extract(transcript: String, existingProjects: List<String> = emptyList()): List<ExtractedItem>
}
```
- Remove `extractTopic()` — project suggestion replaces it

### ExtractedItem Changes
```kotlin
data class ExtractedItem(
    val text: String,
    val dueDate: Long? = null,
    val priority: Priority = Priority.NONE,
    val suggestedProject: String? = null
)
```

---

## Phase 3: More Input Sources

### Gmail Integration
- Re-enable GoogleAuthManager for OAuth
- Gmail API to fetch recent emails
- Create Source(type=EMAIL) entries
- Run through same Gemini extraction pipeline
- Items land in Inbox

### SMS Integration
- Read SMS via content provider (READ_SMS permission)
- Same extraction pipeline

### Google Chat
- Chat API or alternative approach
- Lowest priority

### Source-agnostic Processor
- `domain/SourceProcessor.kt` — takes any Source, runs extraction with project context, detects duplicates, saves to DB, notifies user

---

## Design Principles
- **Tasks first**: the user interacts with tasks and projects, not raw sources
- **Inbox zero**: new items land in Inbox, user assigns to projects
- **AI assists, user decides**: AI suggests project/priority, user confirms
- **Source as provenance**: every task knows where it came from, but you rarely browse sources directly
- **Incremental delivery**: each phase is independently useful
