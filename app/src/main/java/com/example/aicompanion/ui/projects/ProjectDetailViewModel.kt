package com.example.aicompanion.ui.projects

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.aicompanion.AICompanionApplication
import com.example.aicompanion.data.local.entity.ActionItem
import com.example.aicompanion.data.local.entity.Project
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class ProjectDetailUiState(
    val project: Project? = null,
    val items: List<ActionItem> = emptyList(),
    val isLoading: Boolean = true
)

class ProjectDetailViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = (application as AICompanionApplication).container.taskRepository

    private val _uiState = MutableStateFlow(ProjectDetailUiState())
    val uiState: StateFlow<ProjectDetailUiState> = _uiState.asStateFlow()

    fun loadProject(projectId: Long) {
        viewModelScope.launch {
            combine(
                repo.getProjectById(projectId),
                repo.getAllItemsByProject(projectId)
            ) { project, items ->
                ProjectDetailUiState(
                    project = project,
                    items = items,
                    isLoading = false
                )
            }.collect { _uiState.value = it }
        }
    }

    fun toggleCompleted(itemId: Long, completed: Boolean) {
        viewModelScope.launch { repo.toggleCompleted(itemId, completed) }
    }

    fun deleteTask(id: Long) {
        viewModelScope.launch { repo.deleteTask(id) }
    }

    fun deleteProject() {
        val projectId = _uiState.value.project?.id ?: return
        viewModelScope.launch { repo.deleteProject(projectId) }
    }
}
