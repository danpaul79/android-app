package com.example.aicompanion.ui.settings

import android.app.Application
import android.content.ContentValues
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.aicompanion.AICompanionApplication
import com.example.aicompanion.data.export.DataExportImport
import com.example.aicompanion.data.local.entity.SyncState
import com.example.aicompanion.data.sync.SyncStatus
import com.example.aicompanion.reminder.MorningCheckInWorker
import com.example.aicompanion.reminder.MorningPlanStore
import com.example.aicompanion.reminder.MorningPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class VoiceNoteFile(
    val audioFile: File,
    val transcriptFile: File?,
    val date: Date,
    val displayName: String,
    val isVoiceCommand: Boolean = false
)

data class SyncUiState(
    val syncEnabled: Boolean = false,
    val accountEmail: String? = null,
    val lastSyncTime: Long? = null,
    val syncStatus: SyncStatus = SyncStatus.Idle
)

data class EnrichmentUiState(
    val unenrichedCount: Int = 0,
    val isRunning: Boolean = false,
    val progress: Int = 0,       // tasks processed so far
    val total: Int = 0,          // total tasks to process
    val enriched: Int = 0,       // tasks actually updated
    val isDone: Boolean = false,
    val log: List<String> = emptyList()
)

data class MorningUiState(
    val enabled: Boolean = false,
    val hourOfDay: Int = 8,
    val minute: Int = 0
)

data class SettingsUiState(
    val voiceNotes: List<VoiceNoteFile> = emptyList(),
    val message: String? = null,
    val sync: SyncUiState = SyncUiState(),
    val enrichment: EnrichmentUiState = EnrichmentUiState(),
    val morning: MorningUiState = MorningUiState(),
    val morningPlanHistory: List<MorningPlanStore.PlanEntry> = emptyList()
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val appContainer = (application as AICompanionApplication).container
    private val repo = appContainer.taskRepository
    private val syncEngine = appContainer.syncEngine
    private val tokenManager = appContainer.tokenManager
    private val syncStateDao = appContainer.syncStateDao
    private val morningPrefs = MorningPreferences(application)
    private val morningPlanStore = MorningPlanStore(application)

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadVoiceNotes()
        loadSyncState()
        observeSyncStatus()
        loadEnrichmentCount()
        loadMorningState()
        loadMorningPlanHistory()
    }

    private fun loadMorningPlanHistory() {
        _uiState.value = _uiState.value.copy(
            morningPlanHistory = morningPlanStore.loadHistory()
        )
    }

    private fun loadMorningState() {
        _uiState.value = _uiState.value.copy(
            morning = MorningUiState(
                enabled = morningPrefs.enabled,
                hourOfDay = morningPrefs.hourOfDay,
                minute = morningPrefs.minute
            )
        )
    }

    fun setMorningEnabled(enabled: Boolean) {
        morningPrefs.enabled = enabled
        _uiState.value = _uiState.value.copy(morning = _uiState.value.morning.copy(enabled = enabled))
        MorningCheckInWorker.schedule(getApplication())
    }

    fun setMorningTime(hour: Int, minute: Int) {
        morningPrefs.hourOfDay = hour
        morningPrefs.minute = minute
        _uiState.value = _uiState.value.copy(morning = _uiState.value.morning.copy(hourOfDay = hour, minute = minute))
        MorningCheckInWorker.schedule(getApplication())
    }

    private fun loadSyncState() {
        viewModelScope.launch {
            val state = syncStateDao.get()
            _uiState.value = _uiState.value.copy(
                sync = SyncUiState(
                    syncEnabled = state?.syncEnabled == true,
                    accountEmail = state?.googleAccountEmail,
                    lastSyncTime = state?.lastSyncTimestamp
                )
            )
            // Restore token manager account from persisted state
            state?.googleAccountEmail?.let { tokenManager.setAccount(it) }
        }
    }

    private fun observeSyncStatus() {
        viewModelScope.launch {
            syncEngine.status.collect { status ->
                _uiState.value = _uiState.value.copy(
                    sync = _uiState.value.sync.copy(syncStatus = status)
                )
                if (status is SyncStatus.Success) {
                    _uiState.value = _uiState.value.copy(
                        sync = _uiState.value.sync.copy(lastSyncTime = status.timestamp)
                    )
                }
            }
        }
    }

    fun onGoogleSignIn(email: String) {
        viewModelScope.launch {
            tokenManager.setAccount(email)
            val state = syncStateDao.get() ?: SyncState()
            syncStateDao.upsert(state.copy(
                syncEnabled = true,
                googleAccountEmail = email
            ))
            _uiState.value = _uiState.value.copy(
                sync = _uiState.value.sync.copy(
                    syncEnabled = true,
                    accountEmail = email
                )
            )
            // Run initial sync
            try {
                syncEngine.initialSync()
                _uiState.value = _uiState.value.copy(message = "Google Tasks sync enabled")
            } catch (e: Exception) {
                Log.e("SettingsViewModel", "Initial sync failed: ${e.message}", e)
                _uiState.value = _uiState.value.copy(message = "Sync setup failed: ${e.message}")
            }
        }
    }

    fun onGoogleSignOut() {
        viewModelScope.launch {
            tokenManager.clearAccount()
            val state = syncStateDao.get() ?: SyncState()
            syncStateDao.upsert(state.copy(
                syncEnabled = false,
                googleAccountEmail = null
            ))
            _uiState.value = _uiState.value.copy(
                sync = SyncUiState()
            )
            _uiState.value = _uiState.value.copy(message = "Google Tasks sync disabled")
        }
    }

    fun syncNow() {
        viewModelScope.launch {
            syncEngine.sync()
        }
    }

    fun loadVoiceNotes() {
        val context = getApplication<Application>()
        val recordingsDir = File(context.getExternalFilesDir(null), "recordings")
        if (!recordingsDir.exists()) {
            _uiState.value = _uiState.value.copy(voiceNotes = emptyList())
            return
        }

        val audioFiles = recordingsDir.listFiles { file ->
            file.extension == "m4a"
        }?.sortedByDescending { it.lastModified() } ?: emptyList()

        val notes = audioFiles.map { audioFile ->
            val baseName = audioFile.nameWithoutExtension
            val transcriptFile = recordingsDir.listFiles { file ->
                file.name.startsWith(baseName) && file.name.contains("Transcript") && file.extension == "txt"
            }?.firstOrNull()

            VoiceNoteFile(
                audioFile = audioFile,
                transcriptFile = transcriptFile,
                date = Date(audioFile.lastModified()),
                displayName = baseName.removePrefix("note_").replace("_", " ")
            )
        }

        // Also load voice command logs
        val commandLogs = recordingsDir.listFiles { file ->
            file.name.startsWith("command_") && file.extension == "txt"
        }?.sortedByDescending { it.lastModified() }?.map { logFile ->
            VoiceNoteFile(
                audioFile = logFile,
                transcriptFile = logFile,
                date = Date(logFile.lastModified()),
                displayName = logFile.nameWithoutExtension.removePrefix("command_").replace("_", " "),
                isVoiceCommand = true
            )
        } ?: emptyList()

        val allNotes = (notes + commandLogs).sortedByDescending { it.date }
        _uiState.value = _uiState.value.copy(voiceNotes = allNotes)
    }

    fun exportData() {
        viewModelScope.launch {
            try {
                val data = repo.exportData()
                val json = DataExportImport.toJson(data)
                val dateStr = SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.getDefault()).format(Date())
                val fileName = "ai_companion_backup_$dateStr.json"

                val context = getApplication<Application>()
                val contentValues = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "application/json")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Pocket Pilot")
                }

                val uri = context.contentResolver.insert(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    contentValues
                )

                if (uri != null) {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(json.toByteArray())
                    }
                    val taskCount = data.tasks.size
                    val projectCount = data.projects.size
                    _uiState.value = _uiState.value.copy(
                        message = "Exported $taskCount tasks, $projectCount projects"
                    )
                } else {
                    _uiState.value = _uiState.value.copy(message = "Export failed: could not create file")
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(message = "Export failed: ${e.message}")
            }
        }
    }

    fun importData(uri: Uri) {
        viewModelScope.launch {
            try {
                val context = getApplication<Application>()
                val json = context.contentResolver.openInputStream(uri)?.use { it.bufferedReader().readText() }
                    ?: throw Exception("Could not read file")

                val data = DataExportImport.fromJson(json)
                val result = repo.importData(data)

                val msg = buildString {
                    append("Imported ${result.tasksImported} tasks, ${result.projectsImported} projects")
                    if (result.projectsSkipped > 0) {
                        append(" (${result.projectsSkipped} existing)")
                    }
                }
                _uiState.value = _uiState.value.copy(message = msg)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(message = "Import failed: ${e.message}")
            }
        }
    }

    fun dismissMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }

    private fun loadEnrichmentCount() {
        viewModelScope.launch {
            val count = repo.countUnenrichedTasks()
            _uiState.value = _uiState.value.copy(
                enrichment = _uiState.value.enrichment.copy(unenrichedCount = count)
            )
        }
    }

    fun runEnrichment() {
        if (_uiState.value.enrichment.isRunning) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                enrichment = EnrichmentUiState(
                    unenrichedCount = _uiState.value.enrichment.unenrichedCount,
                    isRunning = true,
                    isDone = false
                )
            )
            val result = repo.enrichUnenrichedTasks { progress ->
                _uiState.value = _uiState.value.copy(
                    enrichment = _uiState.value.enrichment.copy(
                        progress = progress.processed,
                        total = progress.total,
                        enriched = progress.enriched,
                        log = progress.log
                    )
                )
            }
            _uiState.value = _uiState.value.copy(
                enrichment = EnrichmentUiState(
                    unenrichedCount = 0,
                    isRunning = false,
                    progress = result.processed,
                    total = result.total,
                    enriched = result.enriched,
                    isDone = true,
                    log = result.log
                ),
                message = "Enriched ${result.enriched} tasks (${result.processed} analyzed)"
            )
        }
    }
}
