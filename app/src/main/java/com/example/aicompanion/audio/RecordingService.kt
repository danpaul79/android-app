package com.example.aicompanion.audio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.aicompanion.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Foreground service that manages audio recording.
 * Allows recording to continue when the app is in the background.
 * The notification shows elapsed time and provides pause/stop controls.
 */
class RecordingService : Service() {

    companion object {
        const val CHANNEL_ID = "pocket_pilot_recording"
        private const val NOTIFICATION_ID = 9600

        const val ACTION_START = "com.example.aicompanion.action.START_RECORDING"
        const val ACTION_PAUSE = "com.example.aicompanion.action.PAUSE_RECORDING"
        const val ACTION_RESUME = "com.example.aicompanion.action.RESUME_RECORDING"
        const val ACTION_STOP = "com.example.aicompanion.action.STOP_RECORDING"
        const val ACTION_CANCEL = "com.example.aicompanion.action.CANCEL_RECORDING"

        // Shared state accessible from ViewModel
        private val _serviceState = MutableStateFlow<RecorderState>(RecorderState.Idle)
        val serviceState: StateFlow<RecorderState> = _serviceState.asStateFlow()

        private val _amplitude = MutableStateFlow(0)
        val amplitude: StateFlow<Int> = _amplitude.asStateFlow()

        val isRunning: Boolean
            get() = _serviceState.value is RecorderState.Recording ||
                    _serviceState.value is RecorderState.Paused

        fun start(context: Context) {
            val intent = Intent(context, RecordingService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun pause(context: Context) {
            context.startService(Intent(context, RecordingService::class.java).apply {
                action = ACTION_PAUSE
            })
        }

        fun resume(context: Context) {
            context.startService(Intent(context, RecordingService::class.java).apply {
                action = ACTION_RESUME
            })
        }

        fun stop(context: Context) {
            context.startService(Intent(context, RecordingService::class.java).apply {
                action = ACTION_STOP
            })
        }

        fun cancel(context: Context) {
            context.startService(Intent(context, RecordingService::class.java).apply {
                action = ACTION_CANCEL
            })
        }
    }

    private var audioRecorder: AudioRecorder? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var amplitudeJob: Job? = null
    private var timerJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> handleStart()
            ACTION_PAUSE -> handlePause()
            ACTION_RESUME -> handleResume()
            ACTION_STOP -> handleStop()
            ACTION_CANCEL -> handleCancel()
        }
        return START_NOT_STICKY
    }

    private fun handleStart() {
        audioRecorder = AudioRecorder(this)
        val filePath = audioRecorder?.startRecording()
        if (filePath != null) {
            _serviceState.value = RecorderState.Recording(filePath, System.currentTimeMillis())
            startForeground(NOTIFICATION_ID, buildNotification("Recording...", showPause = true))
            startAmplitudePolling()
            startTimerUpdates()
        } else {
            _serviceState.value = RecorderState.Error("Failed to start recording")
            stopSelf()
        }
    }

    private fun handlePause() {
        audioRecorder?.pauseRecording()
        val state = audioRecorder?.state?.value
        if (state is RecorderState.Paused) {
            _serviceState.value = state
            amplitudeJob?.cancel()
            _amplitude.value = 0
            updateNotification("Paused", showResume = true)
        }
    }

    private fun handleResume() {
        audioRecorder?.resumeRecording()
        val state = audioRecorder?.state?.value
        if (state is RecorderState.Recording) {
            _serviceState.value = state
            startAmplitudePolling()
            updateNotification("Recording...", showPause = true)
        }
    }

    private fun handleStop() {
        amplitudeJob?.cancel()
        timerJob?.cancel()
        _amplitude.value = 0
        val filePath = audioRecorder?.stopRecording()
        if (filePath != null) {
            _serviceState.value = RecorderState.Completed(filePath)
        }
        audioRecorder = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun handleCancel() {
        amplitudeJob?.cancel()
        timerJob?.cancel()
        _amplitude.value = 0
        audioRecorder?.cancelRecording()
        _serviceState.value = RecorderState.Idle
        audioRecorder = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun startAmplitudePolling() {
        amplitudeJob?.cancel()
        amplitudeJob = serviceScope.launch {
            while (true) {
                _amplitude.value = audioRecorder?.getMaxAmplitude() ?: 0
                delay(100)
            }
        }
    }

    private fun startTimerUpdates() {
        timerJob?.cancel()
        timerJob = serviceScope.launch {
            while (true) {
                delay(1000)
                val state = _serviceState.value
                if (state is RecorderState.Recording) {
                    val elapsed = System.currentTimeMillis() - state.startTime
                    val minutes = (elapsed / 1000 / 60).toInt()
                    val seconds = (elapsed / 1000 % 60).toInt()
                    updateNotification(
                        "Recording ${String.format("%02d:%02d", minutes, seconds)}",
                        showPause = true
                    )
                }
            }
        }
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Recording",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows when Pocket Pilot is recording audio"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(
        text: String,
        showPause: Boolean = false,
        showResume: Boolean = false
    ): Notification {
        val tapIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_OPEN_CAPTURE, true)
        }
        val tapPi = PendingIntent.getActivity(
            this, NOTIFICATION_ID, tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Pocket Pilot")
            .setContentText(text)
            .setOngoing(true)
            .setContentIntent(tapPi)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        if (showPause) {
            val pauseIntent = Intent(this, RecordingService::class.java).apply {
                action = ACTION_PAUSE
            }
            val pausePi = PendingIntent.getService(
                this, 1, pauseIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(android.R.drawable.ic_media_pause, "Pause", pausePi)
        }

        if (showResume) {
            val resumeIntent = Intent(this, RecordingService::class.java).apply {
                action = ACTION_RESUME
            }
            val resumePi = PendingIntent.getService(
                this, 2, resumeIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(android.R.drawable.ic_media_play, "Resume", resumePi)
        }

        // Always show stop
        val stopIntent = Intent(this, RecordingService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPi = PendingIntent.getService(
            this, 3, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        builder.addAction(android.R.drawable.ic_delete, "Stop", stopPi)

        return builder.build()
    }

    private fun updateNotification(text: String, showPause: Boolean = false, showResume: Boolean = false) {
        val notification = buildNotification(text, showPause, showResume)
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)
    }

    override fun onDestroy() {
        amplitudeJob?.cancel()
        timerJob?.cancel()
        audioRecorder?.cancelRecording()
        audioRecorder = null
        super.onDestroy()
    }
}
