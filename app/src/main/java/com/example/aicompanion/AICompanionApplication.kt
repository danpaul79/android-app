package com.example.aicompanion

import android.app.Application
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.aicompanion.data.sync.SyncWorker
import com.example.aicompanion.di.AppContainer
import com.example.aicompanion.reminder.MorningCheckInWorker
import com.example.aicompanion.reminder.ReminderWorker
import com.google.firebase.crashlytics.FirebaseCrashlytics
import java.util.concurrent.TimeUnit

class AICompanionApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        initCrashlytics()
        scheduleReminderWorker()
        scheduleSyncWorker()
        MorningCheckInWorker.schedule(this)
    }

    private fun initCrashlytics() {
        try {
            val crashlytics = FirebaseCrashlytics.getInstance()
            crashlytics.setCrashlyticsCollectionEnabled(true)
            Log.i("AICompanion", "Crashlytics initialized")
        } catch (e: Exception) {
            // Firebase not configured (no google-services.json) — skip silently
            Log.w("AICompanion", "Crashlytics not available: ${e.message}")
        }
    }

    private fun scheduleReminderWorker() {
        val workRequest = PeriodicWorkRequestBuilder<ReminderWorker>(
            1, TimeUnit.HOURS
        ).build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "reminder_check",
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }

    private fun scheduleSyncWorker() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val workRequest = PeriodicWorkRequestBuilder<SyncWorker>(
            30, TimeUnit.MINUTES
        ).setConstraints(constraints).build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "google_tasks_sync",
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }
}
