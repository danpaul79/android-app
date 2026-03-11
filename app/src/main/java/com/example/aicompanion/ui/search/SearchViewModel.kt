package com.example.aicompanion.ui.search

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.aicompanion.AICompanionApplication
import com.example.aicompanion.data.local.entity.ActionItem
import com.example.aicompanion.data.local.entity.parsedTags
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SearchUiState(
    val query: String = "",
    val results: List<ActionItem> = emptyList(),
    val isSearching: Boolean = false,
    val popularTags: List<Pair<String, Int>> = emptyList()
)

class SearchViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = (application as AICompanionApplication).container.taskRepository

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            val tasks = repo.getAllNonTrashedTasks()
            val tagCounts = mutableMapOf<String, Int>()
            tasks.forEach { task ->
                task.parsedTags().forEach { tag ->
                    tagCounts[tag] = (tagCounts[tag] ?: 0) + 1
                }
            }
            val popular = tagCounts.entries
                .sortedByDescending { it.value }
                .take(10)
                .map { it.key to it.value }
            _uiState.value = _uiState.value.copy(popularTags = popular)
        }
    }

    fun onQueryChange(query: String) {
        _uiState.value = _uiState.value.copy(query = query)
        searchJob?.cancel()

        if (query.isBlank()) {
            _uiState.value = _uiState.value.copy(results = emptyList(), isSearching = false)
            return
        }

        searchJob = viewModelScope.launch {
            delay(300) // debounce
            _uiState.value = _uiState.value.copy(isSearching = true)
            repo.searchItems(query).collect { results ->
                _uiState.value = _uiState.value.copy(results = results, isSearching = false)
            }
        }
    }

    fun toggleCompleted(itemId: Long, completed: Boolean) {
        viewModelScope.launch { repo.toggleCompleted(itemId, completed) }
    }
}
