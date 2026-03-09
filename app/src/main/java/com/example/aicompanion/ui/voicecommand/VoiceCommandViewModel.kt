package com.example.aicompanion.ui.voicecommand

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.aicompanion.AICompanionApplication
import com.example.aicompanion.audio.AudioRecorder
import com.example.aicompanion.audio.RecorderState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class CommandState {
    Idle,
    Recording,
    Processing,
    Success,
    Error
}

data class VoiceCommandUiState(
    val commandState: CommandState = CommandState.Idle,
    val message: String? = null,
    val amplitudes: List<Float> = emptyList()
)

class VoiceCommandViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as AICompanionApplication).container
    private val processor = container.voiceCommandProcessor
    private val audioRecorder = AudioRecorder(application)

    private val _uiState = MutableStateFlow(VoiceCommandUiState())
    val uiState: StateFlow<VoiceCommandUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            audioRecorder.state.collect { recorderState ->
                when (recorderState) {
                    is RecorderState.Recording -> {
                        _uiState.value = _uiState.value.copy(commandState = CommandState.Recording)
                    }
                    is RecorderState.Idle -> {
                        if (_uiState.value.commandState == CommandState.Recording) {
                            _uiState.value = _uiState.value.copy(commandState = CommandState.Idle)
                        }
                    }
                    else -> {}
                }
            }
        }
    }

    fun startRecording() {
        _uiState.value = VoiceCommandUiState(commandState = CommandState.Recording)
        audioRecorder.startRecording()
        // Start amplitude polling
        viewModelScope.launch {
            while (_uiState.value.commandState == CommandState.Recording) {
                val amp = audioRecorder.getMaxAmplitude() / 32767f
                val current = _uiState.value.amplitudes.takeLast(19) + amp
                _uiState.value = _uiState.value.copy(amplitudes = current)
                kotlinx.coroutines.delay(100)
            }
        }
    }

    fun stopAndProcess() {
        val filePath = audioRecorder.stopRecording() ?: return
        _uiState.value = _uiState.value.copy(
            commandState = CommandState.Processing,
            message = "Processing..."
        )

        viewModelScope.launch {
            val audioFile = java.io.File(filePath)
            val result = processor.processAudioFile(audioFile)

            _uiState.value = _uiState.value.copy(
                commandState = if (result.success) CommandState.Success else CommandState.Error,
                message = result.message
            )

            // Clean up the command audio file
            audioFile.delete()

            // Auto-dismiss after delay
            kotlinx.coroutines.delay(3000)
            if (_uiState.value.commandState == CommandState.Success ||
                _uiState.value.commandState == CommandState.Error
            ) {
                _uiState.value = VoiceCommandUiState()
            }
        }
    }

    fun cancel() {
        audioRecorder.cancelRecording()
        _uiState.value = VoiceCommandUiState()
    }

    fun dismiss() {
        _uiState.value = VoiceCommandUiState()
    }
}
