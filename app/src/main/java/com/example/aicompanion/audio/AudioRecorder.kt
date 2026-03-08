package com.example.aicompanion.audio

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed class RecorderState {
    data object Idle : RecorderState()
    data class Recording(val filePath: String, val startTime: Long) : RecorderState()
    data class Completed(val filePath: String) : RecorderState()
    data class Error(val message: String) : RecorderState()
}

class AudioRecorder(private val context: Context) {

    private val _state = MutableStateFlow<RecorderState>(RecorderState.Idle)
    val state: StateFlow<RecorderState> = _state.asStateFlow()

    private var recorder: MediaRecorder? = null

    fun startRecording(): String? {
        val outputDir = getRecordingsDir()
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val outputFile = File(outputDir, "note_$timestamp.m4a")

        return try {
            recorder = createRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128000)
                setAudioSamplingRate(44100)
                setOutputFile(outputFile.absolutePath)
                prepare()
                start()
            }
            _state.value = RecorderState.Recording(
                filePath = outputFile.absolutePath,
                startTime = System.currentTimeMillis()
            )
            outputFile.absolutePath
        } catch (e: Exception) {
            _state.value = RecorderState.Error("Failed to start recording: ${e.message}")
            null
        }
    }

    fun stopRecording(): String? {
        val currentState = _state.value
        if (currentState !is RecorderState.Recording) return null

        return try {
            recorder?.apply {
                stop()
                release()
            }
            recorder = null
            _state.value = RecorderState.Completed(currentState.filePath)
            currentState.filePath
        } catch (e: Exception) {
            recorder?.release()
            recorder = null
            _state.value = RecorderState.Error("Failed to stop recording: ${e.message}")
            null
        }
    }

    fun reset() {
        recorder?.release()
        recorder = null
        _state.value = RecorderState.Idle
    }

    private fun createRecorder(): MediaRecorder {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
    }

    private fun getRecordingsDir(): File {
        val dir = File(context.getExternalFilesDir(null), "recordings")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }
}
