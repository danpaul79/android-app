package com.example.aicompanion.ui.review

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.aicompanion.AICompanionApplication
import com.example.aicompanion.data.local.entity.ActionItem
import com.example.aicompanion.data.repository.TaskRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class WeeklyReviewUiState(
    val isLoading: Boolean = true,
    val completedTasks: List<ActionItem> = emptyList(),
    val newTasksCount: Int = 0,
    val rolledOverCount: Int = 0,
    val completionRateByProject: Map<String, Pair<Int, Int>> = emptyMap(),
    val totalActiveNow: Int = 0,
    val aiSummary: String? = null,
    val isLoadingSummary: Boolean = false
)

class WeeklyReviewViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as AICompanionApplication).container
    private val repo = container.taskRepository
    private val geminiClient = container.geminiClient

    private val _uiState = MutableStateFlow(WeeklyReviewUiState())
    val uiState: StateFlow<WeeklyReviewUiState> = _uiState.asStateFlow()

    init {
        loadReviewData()
    }

    private fun loadReviewData() {
        viewModelScope.launch {
            val data = repo.getWeeklyReviewData()
            _uiState.value = WeeklyReviewUiState(
                isLoading = false,
                completedTasks = data.completedTasks,
                newTasksCount = data.newTasksCount,
                rolledOverCount = data.rolledOverCount,
                completionRateByProject = data.completionRateByProject,
                totalActiveNow = data.totalActiveNow
            )
            generateAiSummary(data)
        }
    }

    private fun generateAiSummary(data: TaskRepository.WeeklyReviewData) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingSummary = true)
            try {
                val context = buildString {
                    appendLine("### Weekly Summary Data")
                    appendLine("- Tasks completed this week: ${data.completedTasks.size}")
                    appendLine("- New tasks added: ${data.newTasksCount}")
                    appendLine("- Overdue/rolled-over tasks: ${data.rolledOverCount}")
                    appendLine("- Active tasks remaining: ${data.totalActiveNow}")
                    if (data.completedTasks.isNotEmpty()) {
                        appendLine()
                        appendLine("### Completed Tasks")
                        data.completedTasks.take(15).forEach { appendLine("- ${it.text}") }
                    }
                    if (data.completionRateByProject.isNotEmpty()) {
                        appendLine()
                        appendLine("### By Project")
                        data.completionRateByProject.forEach { (proj, rate) ->
                            appendLine("- $proj: ${rate.first} completed / ${rate.second} total")
                        }
                    }
                }
                val question = "Give a brief, encouraging weekly productivity review (3-4 sentences max). Highlight wins, note areas for attention, and suggest one actionable tip for next week."
                val result = geminiClient.askInsight(question, context)
                result.onSuccess { summary ->
                    _uiState.value = _uiState.value.copy(aiSummary = summary, isLoadingSummary = false)
                }
                result.onFailure {
                    _uiState.value = _uiState.value.copy(isLoadingSummary = false)
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoadingSummary = false)
            }
        }
    }
}
