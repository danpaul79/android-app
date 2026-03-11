package com.example.aicompanion.ui.voicecommand

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.aicompanion.AICompanionApplication
import com.example.aicompanion.audio.AudioRecorder
import com.example.aicompanion.audio.RecorderState
import com.example.aicompanion.domain.command.VoiceCommand
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class CommandState {
    Idle,
    Recording,
    TextInput,
    Processing,
    Success,
    Error
}

data class VoiceCommandUiState(
    val commandState: CommandState = CommandState.Idle,
    val message: String? = null,
    val amplitudes: List<Float> = emptyList(),
    val textDraft: String = "",
    /** Set when a navigation action is required; consumer must clear after handling. */
    val navigationCommand: VoiceCommand? = null
)

class VoiceCommandViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as AICompanionApplication).container
    private val processor = container.voiceCommandProcessor
    val audioRecorder = AudioRecorder(application)

    private val _uiState = MutableStateFlow(VoiceCommandUiState())
    val uiState: StateFlow<VoiceCommandUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            audioRecorder.state.collect { recorderState ->
                when (recorderState) {
                    is RecorderState.Recording -> {
                        if (_uiState.value.commandState != CommandState.Recording) {
                            _uiState.value = _uiState.value.copy(commandState = CommandState.Recording)
                        }
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
        viewModelScope.launch {
            while (_uiState.value.commandState == CommandState.Recording) {
                val amp = audioRecorder.getMaxAmplitude() / 32767f
                val current = _uiState.value.amplitudes.takeLast(29) + amp
                _uiState.value = _uiState.value.copy(amplitudes = current)
                delay(100)
            }
        }
    }

    fun stopAndProcess() {
        val filePath = audioRecorder.stopRecording() ?: return
        _uiState.value = _uiState.value.copy(
            commandState = CommandState.Processing,
            message = "Transcribing & processing..."
        )

        viewModelScope.launch {
            val audioFile = File(filePath)
            val result = processor.processAudioFile(audioFile)

            val navCmd = (result.command as? VoiceCommand.PlanMyDay)
            _uiState.value = _uiState.value.copy(
                commandState = if (result.success) CommandState.Success else CommandState.Error,
                message = result.message,
                navigationCommand = navCmd
            )

            saveCommandLog(result.transcript, result.message, result.success)
            audioFile.delete()

            delay(3000)
            if (_uiState.value.commandState == CommandState.Success ||
                _uiState.value.commandState == CommandState.Error
            ) {
                _uiState.value = VoiceCommandUiState()
            }
        }
    }

    fun showTextInput() {
        // If recording, cancel it first
        if (_uiState.value.commandState == CommandState.Recording) {
            audioRecorder.cancelRecording()
        }
        _uiState.value = VoiceCommandUiState(commandState = CommandState.TextInput)
    }

    fun updateTextDraft(text: String) {
        _uiState.value = _uiState.value.copy(textDraft = text)
    }

    fun submitText() {
        val text = _uiState.value.textDraft.trim()
        if (text.isBlank()) return

        _uiState.value = _uiState.value.copy(
            commandState = CommandState.Processing,
            message = "Processing..."
        )

        viewModelScope.launch {
            val result = processor.processTranscript(text)

            val navCmd = (result.command as? VoiceCommand.PlanMyDay)
            _uiState.value = _uiState.value.copy(
                commandState = if (result.success) CommandState.Success else CommandState.Error,
                message = result.message,
                navigationCommand = navCmd
            )

            saveCommandLog(result.transcript, result.message, result.success)

            delay(3000)
            if (_uiState.value.commandState == CommandState.Success ||
                _uiState.value.commandState == CommandState.Error
            ) {
                _uiState.value = VoiceCommandUiState()
            }
        }
    }

    fun clearNavigationCommand() {
        _uiState.value = _uiState.value.copy(navigationCommand = null)
    }

    private fun saveCommandLog(transcript: String?, actionsMessage: String?, success: Boolean) {
        if (transcript.isNullOrBlank()) return
        val context = getApplication<Application>()
        val recordingsDir = File(context.getExternalFilesDir(null), "recordings")
        if (!recordingsDir.exists()) recordingsDir.mkdirs()

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val logFile = File(recordingsDir, "command_$timestamp.txt")

        val content = buildString {
            appendLine("Voice Command")
            appendLine("=============")
            appendLine()
            appendLine("Transcript:")
            appendLine(transcript)
            appendLine()
            appendLine("Actions:")
            val status = if (success) "Success" else "Failed"
            appendLine("[$status] ${actionsMessage ?: "No actions"}")
        }

        logFile.writeText(content)
    }

    fun cancel() {
        audioRecorder.cancelRecording()
        _uiState.value = VoiceCommandUiState()
    }

    fun dismiss() {
        _uiState.value = VoiceCommandUiState()
    }
}
