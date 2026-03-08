package com.example.aicompanion.ui.record

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
import com.example.aicompanion.data.local.entity.VoiceNote
import com.example.aicompanion.domain.extraction.ExtractedItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

data class RecordUiState(
    val recorderState: RecorderState = RecorderState.Idle,
    val transcript: String = "",
    val extractedItems: List<ExtractedItem> = emptyList(),
    val topic: String? = null,
    val isSaving: Boolean = false,
    val isTranscribing: Boolean = false,
    val transcriptionError: String? = null,
    val savedNoteId: Long? = null,
    val audioFilePath: String? = null,
    val transcriptFilePath: String? = null,
    val manualInput: String = "",
    val selectedFileName: String? = null
)

class RecordViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as AICompanionApplication).container
    private val repository = container.repository
    private val extractor = container.extractor
    private val transcriptionClient = container.transcriptionClient

    val audioRecorder = AudioRecorder(application)

    private val _uiState = MutableStateFlow(RecordUiState())
    val uiState: StateFlow<RecordUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            audioRecorder.state.collect { recorderState ->
                _uiState.value = _uiState.value.copy(recorderState = recorderState)
                if (recorderState is RecorderState.Completed) {
                    _uiState.value = _uiState.value.copy(
                        audioFilePath = recorderState.filePath
                    )
                }
            }
        }
    }

    fun startRecording() {
        audioRecorder.startRecording()
    }

    fun stopRecording() {
        audioRecorder.stopRecording()
    }

    fun onAudioFilePicked(uri: Uri, context: Context) {
        viewModelScope.launch {
            // Copy the file to our app directory so we can access it reliably
            val fileName = getFileName(uri, context) ?: "picked_audio.m4a"
            val destDir = File(context.getExternalFilesDir(null), "recordings")
            if (!destDir.exists()) destDir.mkdirs()
            val destFile = File(destDir, fileName)

            context.contentResolver.openInputStream(uri)?.use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            _uiState.value = _uiState.value.copy(
                audioFilePath = destFile.absolutePath,
                selectedFileName = fileName,
                recorderState = RecorderState.Completed(destFile.absolutePath)
            )
        }
    }

    fun transcribe() {
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
                    // Save transcript file alongside audio + to Downloads
                    val transcriptPath = TranscriptFileHelper.saveTranscript(
                        getApplication<Application>(),
                        audioPath,
                        transcriptionResult.transcript,
                        originalName
                    )
                    TranscriptFileHelper.saveRawJson(
                        audioPath, transcriptionResult.rawJson, originalName
                    )

                    // Extract action items from the transcript
                    val items = extractor.extract(transcriptionResult.transcript)
                    val topic = extractor.extractTopic(transcriptionResult.transcript)

                    _uiState.value = _uiState.value.copy(
                        isTranscribing = false,
                        transcript = transcriptionResult.transcript,
                        extractedItems = items,
                        topic = topic,
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
                    voiceNoteId = 0,
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
        audioRecorder.reset()
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
