package com.example.aicompanion.ui.capture

import android.app.Application
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.aicompanion.AICompanionApplication
import com.example.aicompanion.audio.AudioRecorder
import com.example.aicompanion.audio.RecorderState
import com.example.aicompanion.audio.TranscriptFileHelper
import com.example.aicompanion.data.local.entity.ActionItem
import com.example.aicompanion.data.local.entity.Source
import com.example.aicompanion.data.local.entity.SourceType
import com.example.aicompanion.domain.extraction.ExtractedItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

data class CaptureUiState(
    val recorderState: RecorderState = RecorderState.Idle,
    val transcript: String = "",
    val extractedItems: List<ExtractedItem> = emptyList(),
    val isTranscribing: Boolean = false,
    val isExtracting: Boolean = false,
    val isSaving: Boolean = false,
    val transcriptionError: String? = null,
    val extractionError: String? = null,
    val audioFilePath: String? = null,
    val selectedFileName: String? = null,
    val transcriptFilePath: String? = null,
    val projectId: Long? = null,
    val projectName: String? = null,
    val isDone: Boolean = false,
    // Amplitude samples for waveform (0.0 to 1.0)
    val amplitudes: List<Float> = emptyList()
)

class CaptureViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as AICompanionApplication).container
    private val repo = container.taskRepository
    private val extractor = container.extractor
    private val transcriptionClient = container.transcriptionClient

    val audioRecorder = AudioRecorder(application)

    private val _uiState = MutableStateFlow(CaptureUiState())
    val uiState: StateFlow<CaptureUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            audioRecorder.state.collect { recorderState ->
                _uiState.value = _uiState.value.copy(recorderState = recorderState)
                if (recorderState is RecorderState.Completed) {
                    _uiState.value = _uiState.value.copy(
                        audioFilePath = recorderState.filePath
                    )
                    // Auto-transcribe when recording completes
                    transcribe()
                }
            }
        }
    }

    fun setProjectContext(projectId: Long?) {
        if (projectId == null) return
        _uiState.value = _uiState.value.copy(projectId = projectId)
        viewModelScope.launch {
            repo.getProjectById(projectId).collect { project ->
                if (project != null) {
                    _uiState.value = _uiState.value.copy(projectName = project.name)
                }
            }
        }
    }

    fun startRecording() { audioRecorder.startRecording() }
    fun stopRecording() { audioRecorder.stopRecording() }
    fun pauseRecording() { audioRecorder.pauseRecording() }
    fun resumeRecording() { audioRecorder.resumeRecording() }
    fun cancelRecording() { audioRecorder.cancelRecording() }

    fun recordAmplitude() {
        val amp = audioRecorder.getMaxAmplitude()
        val normalized = (amp / 32767f).coerceIn(0f, 1f)
        val current = _uiState.value.amplitudes.toMutableList()
        current.add(normalized)
        // Keep last 50 samples for display
        if (current.size > 50) current.removeAt(0)
        _uiState.value = _uiState.value.copy(amplitudes = current)
    }

    fun onAudioFilePicked(uri: Uri, context: Context) {
        viewModelScope.launch {
            val fileName = getFileName(uri, context) ?: "picked_audio.m4a"
            val destDir = File(context.getExternalFilesDir(null), "recordings")
            if (!destDir.exists()) destDir.mkdirs()
            val destFile = File(destDir, fileName)

            context.contentResolver.openInputStream(uri)?.use { input ->
                destFile.outputStream().use { output -> input.copyTo(output) }
            }

            _uiState.value = _uiState.value.copy(
                audioFilePath = destFile.absolutePath,
                selectedFileName = fileName,
                recorderState = RecorderState.Completed(destFile.absolutePath)
            )
            // Auto-transcribe picked file
            transcribe()
        }
    }

    private fun transcribe() {
        val audioPath = _uiState.value.audioFilePath ?: return
        val audioFile = File(audioPath)
        if (!audioFile.exists()) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isTranscribing = true,
                transcriptionError = null
            )

            val result = transcriptionClient.transcribe(audioFile)

            result.fold(
                onSuccess = { transcriptionResult ->
                    val originalName = _uiState.value.selectedFileName
                    val transcriptPath = TranscriptFileHelper.saveTranscript(
                        getApplication<Application>(),
                        audioPath,
                        transcriptionResult.transcript,
                        originalName
                    )
                    TranscriptFileHelper.saveRawJson(
                        audioPath, transcriptionResult.rawJson, originalName
                    )

                    _uiState.value = _uiState.value.copy(
                        isTranscribing = false,
                        transcript = transcriptionResult.transcript,
                        transcriptFilePath = transcriptPath
                    )
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isTranscribing = false,
                        transcriptionError = error.message
                    )
                }
            )
        }
    }

    fun extractActionItems() {
        val transcript = _uiState.value.transcript
        if (transcript.isBlank()) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isExtracting = true,
                extractionError = null
            )

            try {
                val items = extractor.extract(transcript)

                _uiState.value = _uiState.value.copy(
                    isExtracting = false,
                    extractedItems = items
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isExtracting = false,
                    extractionError = "Extraction failed: ${e.message}"
                )
            }
        }
    }

    fun removeExtractedItem(index: Int) {
        val current = _uiState.value.extractedItems.toMutableList()
        if (index in current.indices) {
            current.removeAt(index)
            _uiState.value = _uiState.value.copy(extractedItems = current)
        }
    }

    fun save() {
        val state = _uiState.value
        if (state.extractedItems.isEmpty() && state.transcript.isBlank()) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true)

            val source = Source(
                type = SourceType.VOICE_NOTE,
                rawContent = state.transcript,
                sourceRef = state.audioFilePath
            )

            val actionItems = state.extractedItems.map { extracted ->
                ActionItem(
                    text = extracted.text,
                    dueDate = extracted.dueDate,
                    projectId = state.projectId
                )
            }

            repo.saveFromSource(source, actionItems)

            _uiState.value = _uiState.value.copy(
                isSaving = false,
                isDone = true
            )
        }
    }

    fun reset() {
        audioRecorder.reset()
        _uiState.value = CaptureUiState(
            projectId = _uiState.value.projectId,
            projectName = _uiState.value.projectName
        )
    }

    private fun getFileName(uri: Uri, context: Context): String? {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0 && cursor.moveToFirst()) {
                return cursor.getString(nameIndex)
            }
        }
        return uri.lastPathSegment
    }
}
