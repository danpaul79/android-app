package com.example.aicompanion.ui.detail

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.aicompanion.AICompanionApplication
import com.example.aicompanion.data.local.relation.VoiceNoteWithActionItems
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DetailUiState(
    val noteWithItems: VoiceNoteWithActionItems? = null,
    val isDeleted: Boolean = false
)

class DetailViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as AICompanionApplication).container.repository

    private val _uiState = MutableStateFlow(DetailUiState())
    val uiState: StateFlow<DetailUiState> = _uiState.asStateFlow()

    fun loadNote(id: Long) {
        viewModelScope.launch {
            repository.getVoiceNoteById(id).collect { noteWithItems ->
                _uiState.value = _uiState.value.copy(noteWithItems = noteWithItems)
            }
        }
    }

    fun toggleActionItem(id: Long, completed: Boolean) {
        viewModelScope.launch {
            repository.toggleActionItemCompleted(id, completed)
        }
    }

    fun deleteNote() {
        val noteId = _uiState.value.noteWithItems?.voiceNote?.id ?: return
        viewModelScope.launch {
            repository.deleteVoiceNote(noteId)
            _uiState.value = _uiState.value.copy(isDeleted = true)
        }
    }

    fun deleteActionItem(id: Long) {
        viewModelScope.launch {
            repository.deleteActionItem(id)
        }
    }
}
