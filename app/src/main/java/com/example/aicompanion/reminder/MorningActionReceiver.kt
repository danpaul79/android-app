package com.example.aicompanion.reminder

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.example.aicompanion.AICompanionApplication
import com.example.aicompanion.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Handles taps on morning check-in capacity action buttons.
 * Picks tasks for the requested capacity and posts a follow-up notification.
 */
class MorningActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_CAPACITY = "com.example.aicompanion.ACTION_CAPACITY"
        const val EXTRA_CAPACITY_MINUTES = "capacity_minutes"
        private const val PLAN_NOTIFICATION_ID = 9001

        const val ACTION_STALE_DONE = "com.example.aicompanion.ACTION_STALE_DONE"
        const val ACTION_STALE_NOT_NEEDED = "com.example.aicompanion.ACTION_STALE_NOT_NEEDED"
        const val ACTION_STALE_SKIP = "com.example.aicompanion.ACTION_STALE_SKIP"
        const val ACTION_WAITING_DONE = "com.example.aicompanion.ACTION_WAITING_DONE"
        const val ACTION_WAITING_REMOVE = "com.example.aicompanion.ACTION_WAITING_REMOVE"
        const val ACTION_WAITING_SKIP = "com.example.aicompanion.ACTION_WAITING_SKIP"
        const val EXTRA_TASK_ID = "task_id"
        const val EXTRA_NOTIFICATION_ID = "notification_id"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return

        // Handle review notification actions
        if (action in setOf(
                ACTION_STALE_DONE, ACTION_STALE_NOT_NEEDED, ACTION_STALE_SKIP,
                ACTION_WAITING_DONE, ACTION_WAITING_REMOVE, ACTION_WAITING_SKIP
            )
        ) {
            val taskId = intent.getLongExtra(EXTRA_TASK_ID, -1L)
            val notifId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)

            val manager = context.getSystemService(NotificationManager::class.java)
            if (notifId >= 0) manager.cancel(notifId)

            val app = context.applicationContext as? AICompanionApplication ?: return
            val repo = app.container.taskRepository

            CoroutineScope(Dispatchers.IO).launch {
                when (action) {
                    ACTION_STALE_DONE -> repo.toggleCompleted(taskId, true)
                    ACTION_STALE_NOT_NEEDED -> repo.trashTask(taskId)
                    ACTION_STALE_SKIP -> { /* notification already cancelled above */ }
                    ACTION_WAITING_DONE -> repo.toggleCompleted(taskId, true)
                    ACTION_WAITING_REMOVE -> repo.removeTagFromNotes(taskId, "waiting-for")
                    ACTION_WAITING_SKIP -> { /* notification already cancelled above */ }
                }
            }
            return
        }

        if (action != ACTION_CAPACITY) return
        val capacityMinutes = intent.getIntExtra(EXTRA_CAPACITY_MINUTES, 60)

        // Dismiss the check-in notification
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.cancel(MorningCheckInWorker.NOTIFICATION_ID)

        val app = context.applicationContext as? AICompanionApplication ?: return
        val repo = app.container.taskRepository
        val channel = MorningNotificationHelper.ensureChannel(context)

        CoroutineScope(Dispatchers.IO).launch {
            val tasks = repo.pickTasksForCapacity(capacityMinutes)

            // Persist the plan for Dashboard card + Settings history
            val planStore = MorningPlanStore(context)
            planStore.savePlan(
                capacityMinutes = capacityMinutes,
                tasks = tasks.map { t ->
                    MorningPlanStore.PlanTask(t.id, t.text, t.estimatedMinutes)
                }
            )

            val openAppIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(MainActivity.EXTRA_OPEN_PLAN, true)
            }
            val openAppPi = PendingIntent.getActivity(
                context, 0, openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val capacityLabel = when {
                capacityMinutes < 60 -> "${capacityMinutes}m"
                capacityMinutes % 60 == 0 -> "${capacityMinutes / 60}h"
                else -> "${capacityMinutes / 60}h ${capacityMinutes % 60}m"
            }

            val body = if (tasks.isEmpty()) {
                "No tasks scheduled — enjoy some free time!"
            } else {
                val dateStr = SimpleDateFormat("MMM d", Locale.getDefault()).format(Date())
                val taskLines = tasks.joinToString("\n") { task ->
                    val mins = if (task.estimatedMinutes > 0) task.estimatedMinutes else 30
                    val timeLabel = if (mins < 60) "${mins}m" else "${mins / 60}h${if (mins % 60 > 0) "${mins % 60}m" else ""}"
                    "• ${task.text} ($timeLabel)"
                }
                "Your $capacityLabel plan for $dateStr:\n$taskLines"
            }

            val notification = NotificationCompat.Builder(context, channel)
                .setSmallIcon(android.R.drawable.ic_popup_reminder)
                .setContentTitle(if (tasks.isEmpty()) "You're all clear!" else "Today's task plan (${capacityLabel})")
                .setContentText(body.lines().firstOrNull() ?: "")
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setContentIntent(openAppPi)
                .build()

            manager.notify(PLAN_NOTIFICATION_ID, notification)
        }
    }
}
