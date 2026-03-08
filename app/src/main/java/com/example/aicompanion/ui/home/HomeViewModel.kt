package com.example.aicompanion.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.aicompanion.AICompanionApplication
import com.example.aicompanion.domain.model.TopicGroup
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HomeUiState(
    val topicGroups: List<TopicGroup> = emptyList(),
    val isLoading: Boolean = true
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as AICompanionApplication).container.repository

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.getAllVoiceNotesWithActionItems().collect { notes ->
                val groups = notes
                    .groupBy { it.voiceNote.topic ?: "Uncategorized" }
                    .map { (topic, noteList) -> TopicGroup(topic, noteList) }
                    .sortedBy { it.topic }
                _uiState.value = HomeUiState(
                    topicGroups = groups,
                    isLoading = false
                )
            }
        }
    }

    fun deleteVoiceNote(id: Long) {
        viewModelScope.launch {
            repository.deleteVoiceNote(id)
        }
    }
}
