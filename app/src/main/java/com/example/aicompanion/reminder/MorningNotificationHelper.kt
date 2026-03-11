package com.example.aicompanion.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

object MorningNotificationHelper {
    const val CHANNEL_ID = "pocket_pilot_morning"

    fun ensureChannel(context: Context): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Morning Check-In",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Daily capacity check-in and task recommendations"
            }
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
        return CHANNEL_ID
    }
}
