package com.example.aicompanion.ui.trash

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

data class TrashUiState(
    val trashedTasks: List<ActionItem> = emptyList(),
    val trashedProjects: List<Project> = emptyList(),
    val isLoading: Boolean = true
)

class TrashViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = (application as AICompanionApplication).container.taskRepository

    private val _uiState = MutableStateFlow(TrashUiState())
    val uiState: StateFlow<TrashUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                repo.getTrashedTasks(),
                repo.getTrashedProjects()
            ) { tasks, projects ->
                TrashUiState(trashedTasks = tasks, trashedProjects = projects, isLoading = false)
            }.collect { _uiState.value = it }
        }
    }

    fun restoreTask(id: Long) {
        viewModelScope.launch { repo.restoreTask(id) }
    }

    fun deleteTaskPermanently(id: Long) {
        viewModelScope.launch { repo.deleteTask(id) }
    }

    fun restoreProject(id: Long) {
        viewModelScope.launch { repo.restoreProject(id) }
    }

    fun deleteProjectPermanently(id: Long) {
        viewModelScope.launch { repo.deleteProject(id) }
    }

    fun emptyTrash() {
        viewModelScope.launch {
            _uiState.value.trashedTasks.forEach { repo.deleteTask(it.id) }
            _uiState.value.trashedProjects.forEach { repo.deleteProject(it.id) }
        }
    }
}
