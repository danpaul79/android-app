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
    data class Paused(val filePath: String, val startTime: Long, val elapsedBeforePause: Long) : RecorderState()
    data class Completed(val filePath: String) : RecorderState()
    data class Error(val message: String) : RecorderState()
}

class AudioRecorder(private val context: Context) {

    private val _state = MutableStateFlow<RecorderState>(RecorderState.Idle)
    val state: StateFlow<RecorderState> = _state.asStateFlow()

    private var recorder: MediaRecorder? = null

    /**
     * Returns the current amplitude (0-32767) from the mic.
     * Call this periodically (e.g. every 100ms) to build a waveform visualization.
     * Returns 0 if not recording.
     */
    fun getMaxAmplitude(): Int {
        return try {
            recorder?.maxAmplitude ?: 0
        } catch (_: Exception) {
            0
        }
    }

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

    fun pauseRecording() {
        val currentState = _state.value
        if (currentState !is RecorderState.Recording) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                recorder?.pause()
                val elapsed = System.currentTimeMillis() - currentState.startTime
                _state.value = RecorderState.Paused(
                    filePath = currentState.filePath,
                    startTime = currentState.startTime,
                    elapsedBeforePause = elapsed
                )
            } catch (e: Exception) {
                _state.value = RecorderState.Error("Failed to pause: ${e.message}")
            }
        }
    }

    fun resumeRecording() {
        val currentState = _state.value
        if (currentState !is RecorderState.Paused) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                recorder?.resume()
                _state.value = RecorderState.Recording(
                    filePath = currentState.filePath,
                    startTime = System.currentTimeMillis() - currentState.elapsedBeforePause
                )
            } catch (e: Exception) {
                _state.value = RecorderState.Error("Failed to resume: ${e.message}")
            }
        }
    }

    fun stopRecording(): String? {
        val currentState = _state.value
        val filePath = when (currentState) {
            is RecorderState.Recording -> currentState.filePath
            is RecorderState.Paused -> currentState.filePath
            else -> return null
        }

        return try {
            recorder?.apply {
                stop()
                release()
            }
            recorder = null
            _state.value = RecorderState.Completed(filePath)
            filePath
        } catch (e: Exception) {
            recorder?.release()
            recorder = null
            _state.value = RecorderState.Error("Failed to stop recording: ${e.message}")
            null
        }
    }

    fun cancelRecording() {
        val currentState = _state.value
        val filePath = when (currentState) {
            is RecorderState.Recording -> currentState.filePath
            is RecorderState.Paused -> currentState.filePath
            else -> {
                reset()
                return
            }
        }

        try {
            recorder?.apply {
                stop()
                release()
            }
        } catch (_: Exception) {
            recorder?.release()
        }
        recorder = null

        // Delete the partial recording file
        try {
            File(filePath).delete()
        } catch (_: Exception) { }

        _state.value = RecorderState.Idle
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
