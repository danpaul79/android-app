package com.example.aicompanion.ui.focus

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.aicompanion.AICompanionApplication
import com.example.aicompanion.data.local.entity.ActionItem
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class FocusTimerUiState(
    val task: ActionItem? = null,
    val elapsedSeconds: Int = 0,
    val targetSeconds: Int? = null, // null = count up mode (no estimate)
    val isRunning: Boolean = false,
    val isPaused: Boolean = false,
    val isFinished: Boolean = false
) {
    val remainingSeconds: Int? get() = targetSeconds?.let { (it - elapsedSeconds).coerceAtLeast(0) }
    val progress: Float get() = targetSeconds?.let {
        if (it > 0) (elapsedSeconds.toFloat() / it).coerceIn(0f, 1f) else 0f
    } ?: 0f
    val isOvertime: Boolean get() = targetSeconds != null && elapsedSeconds > targetSeconds
}

class FocusTimerViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = (application as AICompanionApplication).container.taskRepository

    private val _uiState = MutableStateFlow(FocusTimerUiState())
    val uiState: StateFlow<FocusTimerUiState> = _uiState.asStateFlow()

    private var timerJob: Job? = null

    fun loadTask(taskId: Long) {
        viewModelScope.launch {
            repo.getTaskByIdSync(taskId)?.let { task ->
                val targetSec = if (task.estimatedMinutes > 1) task.estimatedMinutes * 60 else null
                _uiState.value = FocusTimerUiState(
                    task = task,
                    targetSeconds = targetSec
                )
            }
        }
    }

    fun start() {
        if (_uiState.value.isFinished) return
        _uiState.value = _uiState.value.copy(isRunning = true, isPaused = false)
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (_uiState.value.isRunning && !_uiState.value.isFinished) {
                delay(1000)
                if (_uiState.value.isRunning) {
                    val newElapsed = _uiState.value.elapsedSeconds + 1
                    _uiState.value = _uiState.value.copy(elapsedSeconds = newElapsed)
                }
            }
        }
    }

    fun pause() {
        _uiState.value = _uiState.value.copy(isRunning = false, isPaused = true)
        timerJob?.cancel()
    }

    fun finish() {
        timerJob?.cancel()
        _uiState.value = _uiState.value.copy(isRunning = false, isPaused = false, isFinished = true)
    }

    fun completeTask() {
        val task = _uiState.value.task ?: return
        viewModelScope.launch {
            repo.toggleCompleted(task.id, true)
        }
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
    }
}
