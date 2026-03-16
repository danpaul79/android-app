package com.example.aicompanion.ui.projects

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

data class ProjectsUiState(
    val projects: List<Project> = emptyList(),
    val projectTaskCounts: Map<Long, Int> = emptyMap(),
    val projectTasks: Map<Long, List<ActionItem>> = emptyMap(),
    val inboxItems: List<ActionItem> = emptyList(),
    val expandedProjectIds: Set<Long> = emptySet(),
    val inboxExpanded: Boolean = false,
    val isLoading: Boolean = true,
    val undatedCount: Int = 0
) {
    val allExpanded: Boolean
        get() {
            val allProjectIds = projects.map { it.id }.toSet()
            return inboxExpanded && allProjectIds == expandedProjectIds
        }

    val anyExpanded: Boolean
        get() = inboxExpanded || expandedProjectIds.isNotEmpty()
}

class ProjectsViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = (application as AICompanionApplication).container.taskRepository

    private val _uiState = MutableStateFlow(ProjectsUiState())
    val uiState: StateFlow<ProjectsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repo.getUndatedCount().collect { count ->
                _uiState.value = _uiState.value.copy(undatedCount = count)
            }
        }
        viewModelScope.launch {
            combine(
                repo.getAllProjects(),
                repo.getInboxItems()
            ) { projects, inboxItems ->
                _uiState.value.copy(
                    projects = projects,
                    inboxItems = inboxItems,
                    isLoading = false
                )
            }.collect { state ->
                _uiState.value = state
                // Collect task counts and tasks for each project
                state.projects.forEach { project ->
                    if (_uiState.value.projectTasks[project.id] == null) {
                        launch {
                            repo.getActiveCountByProject(project.id).collect { count ->
                                _uiState.value = _uiState.value.copy(
                                    projectTaskCounts = _uiState.value.projectTaskCounts + (project.id to count)
                                )
                            }
                        }
                        launch {
                            repo.getAllItemsByProject(project.id).collect { items ->
                                _uiState.value = _uiState.value.copy(
                                    projectTasks = _uiState.value.projectTasks + (project.id to items)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    fun toggleProjectExpanded(projectId: Long) {
        val current = _uiState.value.expandedProjectIds.toMutableSet()
        if (projectId in current) current.remove(projectId) else current.add(projectId)
        _uiState.value = _uiState.value.copy(expandedProjectIds = current)
    }

    fun toggleInboxExpanded() {
        _uiState.value = _uiState.value.copy(inboxExpanded = !_uiState.value.inboxExpanded)
    }

    fun expandAll() {
        _uiState.value = _uiState.value.copy(
            expandedProjectIds = _uiState.value.projects.map { it.id }.toSet(),
            inboxExpanded = true
        )
    }

    fun collapseAll() {
        _uiState.value = _uiState.value.copy(
            expandedProjectIds = emptySet(),
            inboxExpanded = false
        )
    }

    fun createProject(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            repo.createProject(name.trim())
        }
    }

    fun deleteProject(id: Long) {
        viewModelScope.launch { repo.deleteProject(id) }
    }

    fun toggleCompleted(itemId: Long, completed: Boolean) {
        viewModelScope.launch { repo.toggleCompleted(itemId, completed) }
    }

    fun trashTask(id: Long) {
        viewModelScope.launch { repo.trashTask(id) }
    }

    fun trashProject(id: Long) {
        viewModelScope.launch { repo.trashProject(id) }
    }
}
