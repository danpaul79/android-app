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
    val isLoading: Boolean = true
)

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
                    isLoading = false
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

    fun deleteTask(id: Long) {
        viewModelScope.launch { repo.deleteTask(id) }
    }
}
