package com.example.aicompanion.ui.triage

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.aicompanion.AICompanionApplication
import com.example.aicompanion.data.local.entity.parsedTags
import com.example.aicompanion.network.GeminiClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class TaskTriageUiState(
    val items: List<TriageItem> = emptyList(),
    val currentIndex: Int = 0,
    val isLoading: Boolean = true,
    val isBreakingDown: Boolean = false,
    val breakdownResult: List<GeminiClient.BreakdownItem>? = null,
    val breakdownSelections: Set<Int> = emptySet(),
    val showDatePicker: Boolean = false,
    val showTrashConfirm: Boolean = false,
    val snackbarMessage: String? = null,
    val triageComplete: Boolean = false,
    val projectNames: Map<Long, String> = emptyMap(),
    val mode: TriageMode = TriageMode.SMART
) {
    val currentItem: TriageItem? get() = items.getOrNull(currentIndex)
    val progress: String get() = "${currentIndex + 1} of ${items.size}"
    val hasNext: Boolean get() = currentIndex < items.size - 1
    val hasPrev: Boolean get() = currentIndex > 0
}

class TaskTriageViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as AICompanionApplication).container
    private val repo = container.taskRepository
    private val geminiClient = container.geminiClient

    private val _uiState = MutableStateFlow(TaskTriageUiState())
    val uiState: StateFlow<TaskTriageUiState> = _uiState.asStateFlow()

    init {
        loadItems()
    }

    private fun loadItems() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val mode = _uiState.value.mode
            val candidates = when (mode) {
                TriageMode.SMART -> repo.getTriageCandidates()
                TriageMode.ALL -> repo.getAllTriageCandidates()
            }
            val projectMap = repo.getAllProjectNamesWithIds()
                .associate { (name, id) -> id to name }
            _uiState.value = _uiState.value.copy(
                items = candidates,
                currentIndex = 0,
                isLoading = false,
                triageComplete = candidates.isEmpty(),
                projectNames = projectMap,
                breakdownResult = null,
                breakdownSelections = emptySet()
            )
        }
    }

    fun setMode(mode: TriageMode) {
        if (_uiState.value.mode == mode) return
        _uiState.value = _uiState.value.copy(mode = mode)
        loadItems()
    }

    private fun advance() {
        val state = _uiState.value
        val nextIndex = state.currentIndex + 1
        if (nextIndex >= state.items.size) {
            _uiState.value = state.copy(
                triageComplete = true,
                breakdownResult = null,
                breakdownSelections = emptySet()
            )
        } else {
            _uiState.value = state.copy(
                currentIndex = nextIndex,
                breakdownResult = null,
                breakdownSelections = emptySet(),
                showDatePicker = false,
                showTrashConfirm = false
            )
        }
    }

    fun previous() {
        val state = _uiState.value
        if (state.hasPrev) {
            _uiState.value = state.copy(
                currentIndex = state.currentIndex - 1,
                breakdownResult = null,
                breakdownSelections = emptySet()
            )
        }
    }

    fun keep() {
        val item = _uiState.value.currentItem ?: return
        viewModelScope.launch {
            repo.triageTask(item.task.id)
            advance()
        }
    }

    /** Skip to next card without recording any action — task stays in triage queue next time. */
    fun skip() {
        advance()
    }

    fun complete() {
        val item = _uiState.value.currentItem ?: return
        viewModelScope.launch {
            repo.toggleCompleted(item.task.id, true)
            showSnackbar("Completed!")
            advance()
        }
    }

    fun requestTrash() {
        _uiState.value = _uiState.value.copy(showTrashConfirm = true)
    }

    fun dismissTrashConfirm() {
        _uiState.value = _uiState.value.copy(showTrashConfirm = false)
    }

    fun confirmTrash() {
        val item = _uiState.value.currentItem ?: return
        _uiState.value = _uiState.value.copy(showTrashConfirm = false)
        viewModelScope.launch {
            repo.trashTask(item.task.id)
            showSnackbar("Trashed")
            advance()
        }
    }

    fun snooze() {
        val item = _uiState.value.currentItem ?: return
        viewModelScope.launch {
            repo.snoozeTask(item.task.id)
            showSnackbar("Snoozed for 2 weeks")
            advance()
        }
    }

    fun showDatePicker() {
        _uiState.value = _uiState.value.copy(showDatePicker = true)
    }

    fun dismissDatePicker() {
        _uiState.value = _uiState.value.copy(showDatePicker = false)
    }

    fun setDueDate(millis: Long) {
        val item = _uiState.value.currentItem ?: return
        _uiState.value = _uiState.value.copy(showDatePicker = false)
        viewModelScope.launch {
            repo.setDueDate(item.task.id, millis, force = true)
            showSnackbar("Due date set")
            advance()
        }
    }

    fun toggleWaitingFor() {
        val item = _uiState.value.currentItem ?: return
        val hasTag = item.task.parsedTags().any { it.equals("waiting-for", ignoreCase = true) }
        viewModelScope.launch {
            if (hasTag) {
                repo.removeTagFromNotes(item.task.id, "waiting-for")
                showSnackbar("Unblocked")
            } else {
                repo.addTagToNotes(item.task.id, "waiting-for")
                showSnackbar("Marked as waiting")
            }
            advance()
        }
    }

    fun breakDown() {
        val item = _uiState.value.currentItem ?: return
        _uiState.value = _uiState.value.copy(isBreakingDown = true)
        viewModelScope.launch {
            val projectNames = repo.getAllProjectNames()
            val result = geminiClient.breakdownTask(
                taskText = item.task.text,
                taskNotes = item.task.notes,
                existingProjects = projectNames
            )
            result.onSuccess { breakdownItems ->
                _uiState.value = _uiState.value.copy(
                    isBreakingDown = false,
                    breakdownResult = breakdownItems,
                    breakdownSelections = breakdownItems.indices.toSet()
                )
            }
            result.onFailure {
                _uiState.value = _uiState.value.copy(isBreakingDown = false)
                showSnackbar("AI breakdown failed: ${it.message}")
            }
        }
    }

    fun toggleBreakdownSelection(index: Int) {
        val current = _uiState.value.breakdownSelections.toMutableSet()
        if (index in current) current.remove(index) else current.add(index)
        _uiState.value = _uiState.value.copy(breakdownSelections = current)
    }

    fun confirmBreakdown(trashOriginal: Boolean) {
        val item = _uiState.value.currentItem ?: return
        val breakdown = _uiState.value.breakdownResult ?: return
        val selections = _uiState.value.breakdownSelections
        viewModelScope.launch {
            var created = 0
            for (i in selections) {
                val sub = breakdown[i]
                val tagStr = sub.suggestedTags.joinToString(" ") { "#$it" }
                val notes = tagStr.ifBlank { null }
                repo.createTask(
                    text = sub.text,
                    projectId = item.task.projectId,
                    notes = notes
                )
                // Set effort estimate on the newly created task
                val allActive = repo.getAllActiveItemTexts()
                val newTask = allActive.lastOrNull { it.text == sub.text }
                if (newTask != null && sub.estimatedMinutes > 0) {
                    repo.setEstimatedMinutes(newTask.id, sub.estimatedMinutes)
                }
                created++
            }
            if (trashOriginal) {
                repo.trashTask(item.task.id)
            }
            showSnackbar("Created $created subtasks${if (trashOriginal) ", original trashed" else ""}")
            advance()
        }
    }

    fun dismissBreakdown() {
        _uiState.value = _uiState.value.copy(
            breakdownResult = null,
            breakdownSelections = emptySet()
        )
    }

    fun clearSnackbar() {
        _uiState.value = _uiState.value.copy(snackbarMessage = null)
    }

    private fun showSnackbar(message: String) {
        _uiState.value = _uiState.value.copy(snackbarMessage = message)
    }
}
