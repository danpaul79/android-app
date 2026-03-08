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
    val isLoading: Boolean = true
)

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
                DashboardUiState(
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
}
