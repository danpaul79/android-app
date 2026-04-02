package com.example.aicompanion.ui.dashboard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.aicompanion.AICompanionApplication
import com.example.aicompanion.data.local.entity.ActionItem
import com.example.aicompanion.data.local.entity.Project
import com.example.aicompanion.data.repository.TaskRepository
import com.example.aicompanion.reminder.MorningPlanStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class DashboardUiState(
    val overdueItems: List<ActionItem> = emptyList(),
    val todayItems: List<ActionItem> = emptyList(),
    val nextWeekItems: List<ActionItem> = emptyList(),   // tomorrow through +7 days
    val futureItems: List<ActionItem> = emptyList(),     // 8+ days out
    val undatedItems: List<ActionItem> = emptyList(),
    val recentlyCompleted: List<ActionItem> = emptyList(),
    val inboxCount: Int = 0,
    val projects: List<Project> = emptyList(),
    val isLoading: Boolean = true,
    val selectedIds: Set<Long> = emptySet(),
    val showCompleted: Boolean = false,
    val todaysPlan: MorningPlanStore.PlanEntry? = null,
    val capacityMinutes: Int? = null,  // from last morning check-in; null = not set yet
    val triageCount: Int = 0,
    val productivityStats: TaskRepository.ProductivityStats? = null
) {
    val isSelectionMode: Boolean get() = selectedIds.isNotEmpty()
    val allItems: List<ActionItem> get() = overdueItems + todayItems + nextWeekItems + futureItems + undatedItems

    /** Sum of estimated minutes for overdue + today tasks (the "load" for today). */
    val plannedMinutesToday: Int get() = (overdueItems + todayItems).sumOf {
        when {
            it.estimatedMinutes == 1 -> 0  // 0m / planning task
            it.estimatedMinutes > 1 -> it.estimatedMinutes
            else -> 30  // unestimated → default 30m
        }
    }
}

class DashboardViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = (application as AICompanionApplication).container.taskRepository
    private val planStore = MorningPlanStore(application)

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
            ) { overdue, today, nextWeek, inboxCount, projects ->
                _uiState.value.copy(
                    overdueItems = overdue,
                    todayItems = today,
                    nextWeekItems = nextWeek,
                    inboxCount = inboxCount,
                    projects = projects,
                    isLoading = false
                )
            }.collect { _uiState.value = it }
        }
        viewModelScope.launch {
            repo.getFutureItems().collect { future ->
                _uiState.value = _uiState.value.copy(futureItems = future)
            }
        }
        viewModelScope.launch {
            repo.getRecentlyCompleted().collect { completed ->
                _uiState.value = _uiState.value.copy(recentlyCompleted = completed)
            }
        }
        viewModelScope.launch {
            repo.getUndatedItems().collect { undated ->
                _uiState.value = _uiState.value.copy(undatedItems = undated)
            }
        }
        loadTodaysPlan()
        _uiState.value = _uiState.value.copy(capacityMinutes = planStore.getLastCapacityMinutes())
        refreshTriageCount()
        refreshProductivityStats()
    }

    fun refreshProductivityStats() {
        viewModelScope.launch {
            val stats = repo.getProductivityStats()
            _uiState.value = _uiState.value.copy(productivityStats = stats)
        }
    }

    fun refreshTriageCount() {
        viewModelScope.launch {
            val count = repo.getTriageCandidates().size
            _uiState.value = _uiState.value.copy(triageCount = count)
        }
    }

    fun refreshCapacity() {
        _uiState.value = _uiState.value.copy(capacityMinutes = planStore.getLastCapacityMinutes())
    }

    fun loadTodaysPlan() {
        val plan = if (!planStore.isTodaysPlanDismissed()) planStore.getTodaysPlan() else null
        _uiState.value = _uiState.value.copy(todaysPlan = plan)
    }

    fun dismissTodaysPlan() {
        planStore.dismissTodaysPlan()
        _uiState.value = _uiState.value.copy(todaysPlan = null)
    }

    fun toggleShowCompleted() {
        _uiState.value = _uiState.value.copy(showCompleted = !_uiState.value.showCompleted)
    }

    fun toggleCompleted(itemId: Long, completed: Boolean) {
        viewModelScope.launch {
            repo.toggleCompleted(itemId, completed)
            if (completed) loadTodaysPlan()
        }
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

    fun trashTask(id: Long) {
        viewModelScope.launch {
            repo.trashTask(id)
            loadTodaysPlan()
        }
    }

    fun undoTrash(id: Long) {
        viewModelScope.launch { repo.restoreTask(id) }
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

    fun reorderTodayTasks(reorderedItems: List<ActionItem>) {
        viewModelScope.launch {
            repo.reorderTodayTasks(reorderedItems.map { it.id })
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
