package com.vtrainer.app.services

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Schedules and manages the periodic background sync WorkManager task.
 *
 * Sync policy:
 * - Runs every 15 minutes
 * - Requires network connectivity
 * - Uses exponential backoff on failure
 * - Replaces existing work if already scheduled (KEEP policy avoids duplicate runs)
 *
 * Requirements: 6.5, 12.5
 */
object BackgroundSyncScheduler {

    /**
     * Schedule the periodic sync task.
     * Safe to call multiple times — uses KEEP policy to avoid duplicates.
     */
    fun schedule(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val syncRequest = PeriodicWorkRequestBuilder<SyncWorker>(
            repeatInterval = 15,
            repeatIntervalTimeUnit = TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                10,
                TimeUnit.SECONDS
            )
            .addTag(SyncWorker.TAG)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            SyncWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            syncRequest
        )
    }

    /**
     * Cancel the periodic sync task.
     */
    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(SyncWorker.WORK_NAME)
    }
}
