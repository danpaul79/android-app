package com.example.aicompanion.ui.settings

import android.app.Application
import android.content.ContentValues
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.aicompanion.AICompanionApplication
import com.example.aicompanion.data.export.DataExportImport
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
    val displayName: String
)

data class SettingsUiState(
    val voiceNotes: List<VoiceNoteFile> = emptyList(),
    val message: String? = null
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = (application as AICompanionApplication).container.taskRepository

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadVoiceNotes()
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

        _uiState.value = _uiState.value.copy(voiceNotes = notes)
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
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/AI Companion")
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
}
