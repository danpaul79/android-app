package com.example.aicompanion.ui.projects

import android.app.Application
import android.content.ContentValues
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.aicompanion.AICompanionApplication
import com.example.aicompanion.data.export.DataExportImport
import com.example.aicompanion.data.local.entity.Project
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ProjectsUiState(
    val projects: List<Project> = emptyList(),
    val projectTaskCounts: Map<Long, Int> = emptyMap(),
    val isLoading: Boolean = true,
    val exportMessage: String? = null,
    val importMessage: String? = null
)

class ProjectsViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = (application as AICompanionApplication).container.taskRepository

    private val _uiState = MutableStateFlow(ProjectsUiState())
    val uiState: StateFlow<ProjectsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repo.getAllProjects().collect { projects ->
                _uiState.value = _uiState.value.copy(
                    projects = projects,
                    isLoading = false
                )
                // Collect task counts for each project
                projects.forEach { project ->
                    launch {
                        repo.getActiveCountByProject(project.id).collect { count ->
                            _uiState.value = _uiState.value.copy(
                                projectTaskCounts = _uiState.value.projectTaskCounts + (project.id to count)
                            )
                        }
                    }
                }
            }
        }
    }

    fun createProject(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            repo.createProject(name.trim())
        }
    }

    fun deleteProject(id: Long) {
        viewModelScope.launch { repo.deleteProject(id) }
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
                        exportMessage = "Exported $taskCount tasks, $projectCount projects to Downloads/AI Companion/$fileName"
                    )
                } else {
                    _uiState.value = _uiState.value.copy(exportMessage = "Export failed: could not create file")
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(exportMessage = "Export failed: ${e.message}")
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
                        append(" (${result.projectsSkipped} projects already existed)")
                    }
                }
                _uiState.value = _uiState.value.copy(importMessage = msg)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(importMessage = "Import failed: ${e.message}")
            }
        }
    }

    fun dismissExportMessage() {
        _uiState.value = _uiState.value.copy(exportMessage = null)
    }

    fun dismissImportMessage() {
        _uiState.value = _uiState.value.copy(importMessage = null)
    }
}
