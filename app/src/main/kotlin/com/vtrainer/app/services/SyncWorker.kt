package com.vtrainer.app.services

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.vtrainer.app.VTrainerApplication

/**
 * WorkManager worker responsible for syncing pending training logs to Firestore.
 *
 * Runs periodically (every 15 minutes) when the device has network connectivity.
 * Uses exponential backoff retry logic provided by WorkManager.
 *
 * Requirements: 6.5, 12.5
 */
class SyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    // Obtain repository from Application class to support default WorkManager instantiation
    private val trainingLogRepository by lazy {
        (applicationContext as VTrainerApplication).trainingLogRepository
    }

    override suspend fun doWork(): Result {
        return try {
            val syncResult = trainingLogRepository.syncPendingLogs()
            syncResult.fold(
                onSuccess = { count ->
                    android.util.Log.d(TAG, "Synced $count pending training logs")
                    Result.success()
                },
                onFailure = { error ->
                    android.util.Log.w(TAG, "Sync failed: ${error.message}")
                    // Retry if we haven't exceeded max attempts
                    if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
                }
            )
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Unexpected error during sync", e)
            if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
        }
    }

    companion object {
        const val TAG = "SyncWorker"
        const val WORK_NAME = "vtrainer_background_sync"
        private const val MAX_ATTEMPTS = 3
    }
}
