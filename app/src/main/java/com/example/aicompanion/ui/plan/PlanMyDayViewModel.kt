package com.example.aicompanion.ui.plan

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.aicompanion.AICompanionApplication
import com.example.aicompanion.data.local.entity.ActionItem
import com.example.aicompanion.reminder.MorningPlanStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PlanMyDayUiState(
    val capacityMinutes: Int = 60,
    val selectedContext: String? = null,  // null = Anywhere
    val pickedTasks: List<ActionItem> = emptyList(),
    val isLoading: Boolean = false,
    val hasPlan: Boolean = false          // true once pickTasks has been called at least once
)

/** Context filter options shown as chips. null tag = no filter (anywhere). */
val CONTEXT_OPTIONS: List<Pair<String, String?>> = listOf(
    "Anywhere" to null,
    "Computer" to "computer",
    "Home" to "home",
    "Errands" to "errand",
    "Phone" to "phone-call",
    "Quick" to "quick"
)

val CAPACITY_PRESETS = listOf(30, 60, 90, 120, 180)

class PlanMyDayViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = (application as AICompanionApplication).container.taskRepository
    private val planStore = MorningPlanStore(application)

    private val _uiState = MutableStateFlow(PlanMyDayUiState())
    val uiState: StateFlow<PlanMyDayUiState> = _uiState.asStateFlow()

    init {
        // Pre-fill capacity from last morning check-in
        val lastCapacity = planStore.getLastCapacityMinutes()
        if (lastCapacity != null) {
            _uiState.value = _uiState.value.copy(capacityMinutes = lastCapacity)
        }
    }

    fun setCapacity(minutes: Int) {
        _uiState.value = _uiState.value.copy(capacityMinutes = minutes)
    }

    fun setContext(tag: String?) {
        _uiState.value = _uiState.value.copy(selectedContext = tag)
    }

    fun pickTasks() {
        val state = _uiState.value
        _uiState.value = state.copy(isLoading = true)
        viewModelScope.launch {
            val tasks = repo.pickTasksForCapacity(state.capacityMinutes, state.selectedContext)
            _uiState.value = _uiState.value.copy(
                pickedTasks = tasks,
                isLoading = false,
                hasPlan = true
            )
            // Persist plan so Dashboard banner updates
            planStore.savePlan(
                capacityMinutes = state.capacityMinutes,
                tasks = tasks.map { MorningPlanStore.PlanTask(it.id, it.text, it.estimatedMinutes) }
            )
        }
    }

    fun removeTask(id: Long) {
        _uiState.value = _uiState.value.copy(
            pickedTasks = _uiState.value.pickedTasks.filter { it.id != id }
        )
    }
}
