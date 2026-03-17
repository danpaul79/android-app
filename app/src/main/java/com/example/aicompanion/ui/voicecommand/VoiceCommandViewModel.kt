package com.example.aicompanion.ui.voicecommand

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.aicompanion.AICompanionApplication
import com.example.aicompanion.audio.RecorderState
import com.example.aicompanion.audio.RecordingService
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
    val navigationCommand: VoiceCommand? = null,
    /** When true, stop recording shows transcript in a dialog instead of executing commands. */
    val transcriptMode: Boolean = false,
    /** Set when transcript mode produces a transcript to display. */
    val transcriptResult: String? = null
)

class VoiceCommandViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as AICompanionApplication).container
    private val processor = container.voiceCommandProcessor

    private val _uiState = MutableStateFlow(VoiceCommandUiState())
    val uiState: StateFlow<VoiceCommandUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            RecordingService.serviceState.collect { recorderState ->
                when (recorderState) {
                    is RecorderState.Recording -> {
                        if (_uiState.value.commandState != CommandState.Recording) {
                            _uiState.value = _uiState.value.copy(commandState = CommandState.Recording)
                        }
                    }
                    is RecorderState.Completed -> {
                        // Handle stop from notification: process the recording
                        if (_uiState.value.commandState == CommandState.Recording) {
                            processCompletedRecording(recorderState.filePath)
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
        // Collect amplitude from service
        viewModelScope.launch {
            RecordingService.amplitude.collect { amp ->
                if (amp > 0 && _uiState.value.commandState == CommandState.Recording) {
                    val normalized = amp / 32767f
                    val current = _uiState.value.amplitudes.takeLast(29) + normalized
                    _uiState.value = _uiState.value.copy(amplitudes = current)
                }
            }
        }
    }

    fun toggleTranscriptMode() {
        _uiState.value = _uiState.value.copy(transcriptMode = !_uiState.value.transcriptMode)
    }

    fun startRecording() {
        _uiState.value = VoiceCommandUiState(
            commandState = CommandState.Recording,
            transcriptMode = _uiState.value.transcriptMode
        )
        RecordingService.start(getApplication())
    }

    fun stopAndProcess() {
        _uiState.value = _uiState.value.copy(
            commandState = CommandState.Processing,
            message = if (_uiState.value.transcriptMode) "Transcribing..." else "Transcribing & processing..."
        )
        RecordingService.stop(getApplication())

        viewModelScope.launch {
            // Wait for service to deliver the completed file path
            var filePath: String? = null
            for (i in 1..50) { // wait up to 5 seconds
                val state = RecordingService.serviceState.value
                if (state is RecorderState.Completed) {
                    filePath = state.filePath
                    break
                }
                delay(100)
            }
            if (filePath == null) {
                _uiState.value = _uiState.value.copy(
                    commandState = CommandState.Error,
                    message = "Recording failed"
                )
                return@launch
            }
            processCompletedRecording(filePath)
        }
    }

    private fun processCompletedRecording(filePath: String) {
        val isTranscriptMode = _uiState.value.transcriptMode
        _uiState.value = _uiState.value.copy(
            commandState = CommandState.Processing,
            message = if (isTranscriptMode) "Transcribing..." else "Transcribing & processing..."
        )

        viewModelScope.launch {
            val audioFile = File(filePath)

            if (isTranscriptMode) {
                // Transcript-only: transcribe and show result without executing commands
                val transcript = processor.transcribeOnly(audioFile)
                audioFile.delete()
                if (transcript != null) {
                    _uiState.value = _uiState.value.copy(
                        commandState = CommandState.Idle,
                        transcriptResult = transcript
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        commandState = CommandState.Error,
                        message = "Transcription failed"
                    )
                    delay(3000)
                    _uiState.value = VoiceCommandUiState(transcriptMode = true)
                }
                return@launch
            }

            val result = processor.processAudioFile(audioFile)

            val navCmd = when (result.command) {
                is VoiceCommand.PlanMyDay, is VoiceCommand.ReviewTasks -> result.command
                else -> null
            }
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
            RecordingService.cancel(getApplication())
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

            val navCmd = when (result.command) {
                is VoiceCommand.PlanMyDay, is VoiceCommand.ReviewTasks -> result.command
                else -> null
            }
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

    fun dismissTranscriptResult() {
        _uiState.value = _uiState.value.copy(transcriptResult = null, commandState = CommandState.Idle)
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
        RecordingService.cancel(getApplication())
        _uiState.value = VoiceCommandUiState(transcriptMode = _uiState.value.transcriptMode)
    }

    fun dismiss() {
        _uiState.value = VoiceCommandUiState(transcriptMode = _uiState.value.transcriptMode)
    }
}
