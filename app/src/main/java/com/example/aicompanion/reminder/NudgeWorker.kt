package com.example.aicompanion.reminder

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.aicompanion.AICompanionApplication
import com.example.aicompanion.MainActivity
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Fires every 30 minutes. If the current hour matches a configured nudge time
 * and we haven't already nudged this hour, posts a notification with the count
 * of tasks due today (including overdue).
 */
class NudgeWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        const val WORK_NAME = "task_nudge"
        private const val NOTIFICATION_ID = 9500

        fun schedule(context: Context) {
            val prefs = NudgePreferences(context)
            val workManager = WorkManager.getInstance(context)
            if (!prefs.enabled || prefs.hours.isEmpty()) {
                workManager.cancelUniqueWork(WORK_NAME)
                return
            }

            val request = PeriodicWorkRequestBuilder<NudgeWorker>(30, TimeUnit.MINUTES)
                .build()

            workManager.enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }

    override suspend fun doWork(): Result {
        val prefs = NudgePreferences(context)
        if (!prefs.enabled) return Result.success()

        val now = Calendar.getInstance()
        val currentHour = now.get(Calendar.HOUR_OF_DAY)

        // Only fire if current hour is in the configured set
        if (currentHour !in prefs.hours) return Result.success()

        // Deduplicate: don't nudge more than once per hour
        val lastNudge = Calendar.getInstance().apply { timeInMillis = prefs.lastNudgeTimestamp }
        val sameHour = lastNudge.get(Calendar.HOUR_OF_DAY) == currentHour &&
                lastNudge.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR) &&
                lastNudge.get(Calendar.YEAR) == now.get(Calendar.YEAR)
        if (sameHour) return Result.success()

        // Count tasks due today + overdue
        val app = context.applicationContext as? AICompanionApplication ?: return Result.success()
        val repo = app.container.taskRepository

        val dayEnd = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }.timeInMillis

        val count = repo.countDueTodayAndOverdue(dayEnd)
        if (count == 0) return Result.success()

        // Post notification
        val channel = MorningNotificationHelper.ensureChannel(context)

        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val tapPi = PendingIntent.getActivity(
            context, NOTIFICATION_ID, tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (count == 1) "1 task still due today" else "$count tasks still due today"
        val text = "Tap to see your dashboard"

        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(tapPi)
            .build()

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)

        prefs.lastNudgeTimestamp = System.currentTimeMillis()

        return Result.success()
    }
}
