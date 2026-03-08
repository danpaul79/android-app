package com.example.aicompanion.ui.dashboard

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

data class DashboardUiState(
    val overdueItems: List<ActionItem> = emptyList(),
    val todayItems: List<ActionItem> = emptyList(),
    val upcomingItems: List<ActionItem> = emptyList(),
    val inboxCount: Int = 0,
    val projects: List<Project> = emptyList(),
    val isLoading: Boolean = true,
    val selectedIds: Set<Long> = emptySet()
) {
    val isSelectionMode: Boolean get() = selectedIds.isNotEmpty()
    val allItems: List<ActionItem> get() = overdueItems + todayItems + upcomingItems
}

class DashboardViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = (application as AICompanionApplication).container.taskRepository

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                repo.getOverdueItems(),
                repo.getTodayItems(),
                repo.getUpcomingItems(),
                repo.getInboxCount(),
                repo.getAllProjects()
            ) { overdue, today, upcoming, inboxCount, projects ->
                _uiState.value.copy(
                    overdueItems = overdue,
                    todayItems = today,
                    upcomingItems = upcoming,
                    inboxCount = inboxCount,
                    projects = projects,
                    isLoading = false
                )
            }.collect { _uiState.value = it }
        }
    }

    fun toggleCompleted(itemId: Long, completed: Boolean) {
        viewModelScope.launch { repo.toggleCompleted(itemId, completed) }
    }

    fun toggleSelection(id: Long) {
        val current = _uiState.value.selectedIds.toMutableSet()
        if (id in current) current.remove(id) else current.add(id)
        _uiState.value = _uiState.value.copy(selectedIds = current)
    }

    fun selectAll() {
        _uiState.value = _uiState.value.copy(
            selectedIds = _uiState.value.allItems.map { it.id }.toSet()
        )
    }

    fun clearSelection() {
        _uiState.value = _uiState.value.copy(selectedIds = emptySet())
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

    fun completeSelected() {
        val ids = _uiState.value.selectedIds.toList()
        viewModelScope.launch {
            ids.forEach { repo.toggleCompleted(it, true) }
            clearSelection()
        }
    }

    fun getSelectedItemText(): String? {
        val selectedId = _uiState.value.selectedIds.singleOrNull() ?: return null
        return _uiState.value.allItems.find { it.id == selectedId }?.text
    }

    fun renameTask(id: Long, text: String) {
        viewModelScope.launch { repo.updateTaskText(id, text) }
        clearSelection()
    }

    fun quickAddTask(text: String) {
        viewModelScope.launch {
            repo.createTask(text = text, dueDate = System.currentTimeMillis())
        }
    }
}
