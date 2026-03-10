package com.example.aicompanion.data.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.aicompanion.AICompanionApplication

class SyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "SyncWorker"
    }

    override suspend fun doWork(): Result {
        val app = applicationContext as? AICompanionApplication ?: return Result.failure()
        val syncEngine = app.container.syncEngine

        return try {
            syncEngine.sync()
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Sync worker failed: ${e.message}", e)
            Result.retry()
        }
    }
}
