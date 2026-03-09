package com.example.aicompanion.ui.projects

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.aicompanion.AICompanionApplication
import com.example.aicompanion.data.local.entity.Project
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ProjectsUiState(
    val projects: List<Project> = emptyList(),
    val projectTaskCounts: Map<Long, Int> = emptyMap(),
    val isLoading: Boolean = true
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
}
