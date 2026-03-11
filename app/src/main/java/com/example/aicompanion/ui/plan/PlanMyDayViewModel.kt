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
import java.util.Calendar

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

            // Set due date to today noon for any task that has no due date or an overdue date.
            // This surfaces them in the Dashboard "Today" section.
            val todayNoon = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 12)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            val dayStart = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            tasks.forEach { task ->
                when {
                    // No date or overdue → just set to today
                    task.dueDate == null || task.dueDate < dayStart -> {
                        repo.setDueDate(task.id, todayNoon)
                    }
                    // Future due date → move to today, promote old due date to drop-dead
                    // (only if no drop-dead date already set — don't overwrite user's hard deadline)
                    task.dueDate >= dayStart + 24L * 60 * 60 * 1000 -> {
                        if (task.dropDeadDate == null) {
                            repo.setDropDeadDate(task.id, task.dueDate)
                        }
                        repo.setDueDate(task.id, todayNoon)
                    }
                    // Already due today → leave as-is
                }
            }

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
