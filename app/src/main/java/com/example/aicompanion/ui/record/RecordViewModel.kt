package com.example.aicompanion.ui.record

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.aicompanion.AICompanionApplication
import com.example.aicompanion.data.local.entity.ActionItem
import com.example.aicompanion.data.local.entity.VoiceNote
import com.example.aicompanion.domain.extraction.ExtractedItem
import com.example.aicompanion.speech.SpeechRecognizerManager
import com.example.aicompanion.speech.SpeechState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class RecordUiState(
    val speechState: SpeechState = SpeechState.Idle,
    val transcript: String = "",
    val extractedItems: List<ExtractedItem> = emptyList(),
    val topic: String? = null,
    val isSaving: Boolean = false,
    val savedNoteId: Long? = null,
    val manualInput: String = ""
)

class RecordViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as AICompanionApplication).container
    private val repository = container.repository
    private val extractor = container.extractor

    private var speechManager: SpeechRecognizerManager? = null

    private val _uiState = MutableStateFlow(RecordUiState())
    val uiState: StateFlow<RecordUiState> = _uiState.asStateFlow()

    fun initSpeechManager(manager: SpeechRecognizerManager) {
        speechManager = manager
        viewModelScope.launch {
            manager.state.collect { speechState ->
                _uiState.value = _uiState.value.copy(speechState = speechState)
                if (speechState is SpeechState.Result) {
                    processTranscript(speechState.text)
                } else if (speechState is SpeechState.Listening) {
                    _uiState.value = _uiState.value.copy(
                        transcript = speechState.partialText
                    )
                }
            }
        }
    }

    fun startRecording() {
        speechManager?.startListening()
    }

    fun stopRecording() {
        speechManager?.stopListening()
    }

    fun updateManualInput(text: String) {
        _uiState.value = _uiState.value.copy(manualInput = text)
    }

    fun submitManualInput() {
        val text = _uiState.value.manualInput.trim()
        if (text.isNotBlank()) {
            processTranscript(text)
        }
    }

    fun removeExtractedItem(index: Int) {
        val current = _uiState.value.extractedItems.toMutableList()
        if (index in current.indices) {
            current.removeAt(index)
            _uiState.value = _uiState.value.copy(extractedItems = current)
        }
    }

    fun updateTopic(topic: String) {
        _uiState.value = _uiState.value.copy(topic = topic.ifBlank { null })
    }

    fun save() {
        val state = _uiState.value
        if (state.transcript.isBlank() && state.manualInput.isBlank()) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true)
            val transcript = state.transcript.ifBlank { state.manualInput }
            val voiceNote = VoiceNote(
                rawTranscript = transcript,
                topic = state.topic
            )
            val actionItems = state.extractedItems.map { extracted ->
                ActionItem(
                    voiceNoteId = 0, // will be set by repository
                    text = extracted.text,
                    dueDate = extracted.dueDate
                )
            }
            val noteId = repository.saveVoiceNoteWithActionItems(voiceNote, actionItems)
            _uiState.value = _uiState.value.copy(
                isSaving = false,
                savedNoteId = noteId
            )
        }
    }

    fun reset() {
        _uiState.value = RecordUiState()
    }

    private fun processTranscript(text: String) {
        val items = extractor.extract(text)
        val topic = extractor.extractTopic(text)
        _uiState.value = _uiState.value.copy(
            transcript = text,
            extractedItems = items,
            topic = topic
        )
    }

    override fun onCleared() {
        speechManager?.destroy()
        super.onCleared()
    }
}
