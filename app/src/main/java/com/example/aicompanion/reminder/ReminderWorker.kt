package com.example.aicompanion.reminder

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.aicompanion.AICompanionApplication
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ReminderWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? AICompanionApplication ?: return Result.failure()
        val repository = app.container.taskRepository
        val notificationHelper = NotificationHelper(applicationContext)

        val now = System.currentTimeMillis()
        val windowEnd = now + 24 * 60 * 60 * 1000 // 24 hours from now

        val upcomingItems = repository.getUpcomingUnfiredItems(now - 60 * 60 * 1000, windowEnd)

        for (item in upcomingItems) {
            val dueDateStr = item.dueDate?.let {
                SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(it))
            } ?: "soon"

            notificationHelper.showReminderNotification(
                actionItemId = item.id,
                text = item.text,
                dueDateStr = dueDateStr
            )
            repository.markReminderFired(item.id)
        }

        return Result.success()
    }
}
