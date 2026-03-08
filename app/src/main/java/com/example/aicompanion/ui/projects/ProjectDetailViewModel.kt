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
    val isLoading: Boolean = true,
    val selectedIds: Set<Long> = emptySet()
) {
    val isSelectionMode: Boolean get() = selectedIds.isNotEmpty()
}

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
                    isLoading = false,
                    selectedIds = _uiState.value.selectedIds
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

    fun toggleSelection(id: Long) {
        val current = _uiState.value.selectedIds.toMutableSet()
        if (id in current) current.remove(id) else current.add(id)
        _uiState.value = _uiState.value.copy(selectedIds = current)
    }

    fun selectAll() {
        _uiState.value = _uiState.value.copy(
            selectedIds = _uiState.value.items.map { it.id }.toSet()
        )
    }

    fun clearSelection() {
        _uiState.value = _uiState.value.copy(selectedIds = emptySet())
    }

    fun deleteSelected() {
        val ids = _uiState.value.selectedIds.toList()
        viewModelScope.launch {
            ids.forEach { repo.deleteTask(it) }
            clearSelection()
        }
    }

    fun setDueDateForSelected(dueDate: Long?) {
        val ids = _uiState.value.selectedIds.toList()
        viewModelScope.launch {
            ids.forEach { repo.setDueDate(it, dueDate) }
            clearSelection()
        }
    }
}
