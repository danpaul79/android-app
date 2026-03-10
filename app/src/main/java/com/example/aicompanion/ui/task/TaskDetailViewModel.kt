package com.example.aicompanion.ui.task

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.aicompanion.AICompanionApplication
import com.example.aicompanion.data.local.entity.ActionItem
import com.example.aicompanion.data.local.entity.Project
import com.example.aicompanion.data.local.entity.Source
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class TaskDetailUiState(
    val item: ActionItem? = null,
    val source: Source? = null,
    val projects: List<Project> = emptyList(),
    val currentProjectName: String? = null,
    val isDeleted: Boolean = false
)

class TaskDetailViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = (application as AICompanionApplication).container.taskRepository

    private val _uiState = MutableStateFlow(TaskDetailUiState())
    val uiState: StateFlow<TaskDetailUiState> = _uiState.asStateFlow()

    private var collectJob: Job? = null

    fun loadTask(taskId: Long) {
        collectJob?.cancel()
        collectJob = viewModelScope.launch {
            combine(
                repo.getItemById(taskId),
                repo.getSourceForItem(taskId),
                repo.getAllProjects()
            ) { item, source, projects ->
                val projectName = item?.projectId?.let { pid ->
                    projects.find { it.id == pid }?.name
                }
                TaskDetailUiState(
                    item = item,
                    source = source,
                    projects = projects,
                    currentProjectName = projectName
                )
            }.collect { _uiState.value = it }
        }
    }

    fun toggleCompleted() {
        val item = _uiState.value.item ?: return
        viewModelScope.launch { repo.toggleCompleted(item.id, !item.isCompleted) }
    }

    fun assignToProject(projectId: Long?) {
        val item = _uiState.value.item ?: return
        viewModelScope.launch { repo.assignToProject(item.id, projectId) }
    }

    fun updateText(text: String) {
        val item = _uiState.value.item ?: return
        viewModelScope.launch { repo.updateTask(item.copy(text = text)) }
    }

    fun updateNotes(notes: String) {
        val item = _uiState.value.item ?: return
        viewModelScope.launch { repo.updateTask(item.copy(notes = notes.ifBlank { null })) }
    }

    fun setDueDate(dueDate: Long?) {
        val item = _uiState.value.item ?: return
        viewModelScope.launch { repo.setDueDate(item.id, dueDate) }
    }

    fun trashTask() {
        val item = _uiState.value.item ?: return
        collectJob?.cancel()
        viewModelScope.launch {
            repo.trashTask(item.id)
            _uiState.value = _uiState.value.copy(isDeleted = true)
        }
    }
}
