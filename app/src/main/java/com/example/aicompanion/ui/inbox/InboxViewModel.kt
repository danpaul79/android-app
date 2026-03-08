package com.example.aicompanion.ui.inbox

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

data class InboxUiState(
    val items: List<ActionItem> = emptyList(),
    val projects: List<Project> = emptyList(),
    val inboxCount: Int = 0,
    val isLoading: Boolean = true,
    val selectedIds: Set<Long> = emptySet()
) {
    val isSelectionMode: Boolean get() = selectedIds.isNotEmpty()
}

class InboxViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = (application as AICompanionApplication).container.taskRepository

    private val _uiState = MutableStateFlow(InboxUiState())
    val uiState: StateFlow<InboxUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                repo.getInboxItems(),
                repo.getAllProjects(),
                repo.getInboxCount()
            ) { items, projects, count ->
                InboxUiState(
                    items = items,
                    projects = projects,
                    inboxCount = count,
                    isLoading = false,
                    selectedIds = _uiState.value.selectedIds
                )
            }.collect { _uiState.value = it }
        }
    }

    fun assignToProject(itemId: Long, projectId: Long) {
        viewModelScope.launch { repo.assignToProject(itemId, projectId) }
    }

    fun toggleCompleted(itemId: Long, completed: Boolean) {
        viewModelScope.launch { repo.toggleCompleted(itemId, completed) }
    }

    fun trashTask(id: Long) {
        viewModelScope.launch { repo.trashTask(id) }
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

    fun assignSelectedToProject(projectId: Long) {
        val ids = _uiState.value.selectedIds.toList()
        viewModelScope.launch {
            ids.forEach { repo.assignToProject(it, projectId) }
            clearSelection()
        }
    }

    fun trashSelected() {
        val ids = _uiState.value.selectedIds.toList()
        viewModelScope.launch {
            ids.forEach { repo.trashTask(it) }
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

    fun renameTask(id: Long, text: String) {
        viewModelScope.launch { repo.updateTaskText(id, text) }
        clearSelection()
    }

    fun getSelectedItemText(): String? {
        val selectedId = _uiState.value.selectedIds.singleOrNull() ?: return null
        return _uiState.value.items.find { it.id == selectedId }?.text
    }
}
