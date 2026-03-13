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
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Fires once a day at the user's configured morning time.
 * Posts a "What's your capacity today?" notification with quick capacity action buttons.
 */
class MorningCheckInWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        const val WORK_NAME = "morning_checkin"
        const val NOTIFICATION_ID = 9000
        private const val REVIEW_SUMMARY_NOTIFICATION_ID = 9099
        private const val REVIEW_NOTIFICATION_BASE_ID = 9100
        private const val REVIEW_GROUP = "pocket_pilot_review"

        /** Schedule (or reschedule) the daily morning worker. */
        fun schedule(context: Context) {
            val prefs = MorningPreferences(context)
            val workManager = WorkManager.getInstance(context)
            if (!prefs.enabled) {
                workManager.cancelUniqueWork(WORK_NAME)
                return
            }

            val initialDelay = computeInitialDelayMillis(prefs.hourOfDay, prefs.minute)
            val request = PeriodicWorkRequestBuilder<MorningCheckInWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
                .build()

            workManager.enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }

        /** Compute ms until the next occurrence of the target hour:minute. */
        fun computeInitialDelayMillis(targetHour: Int, targetMinute: Int): Long {
            val now = Calendar.getInstance()
            val target = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, targetHour)
                set(Calendar.MINUTE, targetMinute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            if (target.timeInMillis <= now.timeInMillis) {
                target.add(Calendar.DAY_OF_YEAR, 1)
            }
            return target.timeInMillis - now.timeInMillis
        }
    }

    override suspend fun doWork(): Result {
        val prefs = MorningPreferences(context)
        if (!prefs.enabled) return Result.success()

        // Guard: only fire within ±45 minutes of the configured time. WorkManager
        // periodic work can drift — if it fires at the wrong time, skip it so the
        // next 24h interval lands correctly.
        val now = Calendar.getInstance()
        val nowMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        val targetMinutes = prefs.hourOfDay * 60 + prefs.minute
        val diff = Math.abs(nowMinutes - targetMinutes)
        val adjustedDiff = minOf(diff, 24 * 60 - diff) // handle midnight wrap
        if (adjustedDiff > 45) return Result.success()

        val channel = MorningNotificationHelper.ensureChannel(context)

        // Build capacity option action buttons
        val capacities = listOf(30 to "30m", 60 to "1h", 90 to "90m", 120 to "2h", 180 to "3h")
        val builder = NotificationCompat.Builder(context, channel)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("Good morning! What's your capacity today?")
            .setContentText("Tap a time block to see your recommended tasks.")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("Tap a time block below and I'll pick the best tasks to fit your day."))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)

        for ((minutes, label) in capacities) {
            val intent = Intent(context, MorningActionReceiver::class.java).apply {
                action = MorningActionReceiver.ACTION_CAPACITY
                putExtra(MorningActionReceiver.EXTRA_CAPACITY_MINUTES, minutes)
            }
            val pi = PendingIntent.getBroadcast(
                context,
                minutes, // unique request code per capacity
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(0, label, pi)
        }

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, builder.build())

        // Post stale / waiting-for review notifications
        val app = context.applicationContext as? com.example.aicompanion.AICompanionApplication
        if (app != null) {
            val repo = app.container.taskRepository

            val staleTasks = repo.getStaleItems(staleDaysThreshold = 14, limit = 2)
            val waitingTasks = repo.getWaitingForItems(limit = 2)

            val reviewItems = (staleTasks.map { it to ReviewType.STALE } + waitingTasks.map { it to ReviewType.WAITING })
                .distinctBy { it.first.id }
                .take(3)

            if (reviewItems.isNotEmpty()) {
                val summaryNotif = NotificationCompat.Builder(context, channel)
                    .setSmallIcon(android.R.drawable.ic_popup_reminder)
                    .setContentTitle("Quick review")
                    .setGroupSummary(true)
                    .setGroup(REVIEW_GROUP)
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .setAutoCancel(true)
                    .build()
                manager.notify(REVIEW_SUMMARY_NOTIFICATION_ID, summaryNotif)

                reviewItems.forEachIndexed { index, (task, reviewType) ->
                    val notifId = REVIEW_NOTIFICATION_BASE_ID + index

                    fun buildReviewIntent(action: String, slot: Int) =
                        PendingIntent.getBroadcast(
                            context,
                            notifId * 10 + slot,
                            Intent(context, MorningActionReceiver::class.java).apply {
                                this.action = action
                                putExtra(MorningActionReceiver.EXTRA_TASK_ID, task.id)
                                putExtra(MorningActionReceiver.EXTRA_NOTIFICATION_ID, notifId)
                                putExtra(MorningActionReceiver.EXTRA_REVIEW_TYPE, reviewType.name)
                            },
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )

                    val reviewNotif = NotificationCompat.Builder(context, channel)
                        .setSmallIcon(android.R.drawable.ic_popup_reminder)
                        .setContentTitle(task.text)
                        .setContentText(reviewType.subtitle)
                        .setStyle(NotificationCompat.BigTextStyle().bigText(reviewType.subtitle))
                        .setGroup(REVIEW_GROUP)
                        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                        .setAutoCancel(true)
                        .addAction(0, "Done", buildReviewIntent(MorningActionReceiver.ACTION_REVIEW_DONE, 0))
                        .addAction(0, reviewType.secondaryLabel, buildReviewIntent(MorningActionReceiver.ACTION_REVIEW_SECONDARY, 1))
                        .addAction(0, "Skip", buildReviewIntent(MorningActionReceiver.ACTION_REVIEW_SKIP, 2))
                        .build()

                    manager.notify(notifId, reviewNotif)
                }
            }
        }

        return Result.success()
    }
}
