package com.vtrainer.app.data.repositories

import com.vtrainer.app.domain.models.TrainingLog
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for training log operations.
 * Implements offline-first strategy with local Room cache and background sync to Firestore.
 * 
 * Requirements:
 * - 4.6: Save TrainingLog when workout completes (mobile)
 * - 5.6: Save TrainingLog when workout completes (watch)
 * - 6.5: Retry synchronization when connectivity restored
 * - 12.3: Allow offline operation with automatic sync
 * - 12.5: Automatically synchronize cached logs when connectivity restored
 */
interface TrainingLogRepository {
    
    /**
     * Save a training log with offline-first strategy.
     * 
     * Strategy:
     * 1. Save to Room database first (always succeeds)
     * 2. Attempt to sync with Firestore in background
     * 3. If sync fails, mark as PENDING_SYNC for later retry
     * 
     * This method returns immediately after local save, sync happens asynchronously.
     * 
     * Requirements: 4.6, 5.6, 12.3
     * 
     * @param log The training log to save
     * @return Result indicating success or failure of local save
     */
    suspend fun saveTrainingLog(log: TrainingLog): Result<Unit>
    
    /**
     * Get training history for the current user.
     * Returns a Flow that emits updates from Room database.
     * 
     * The Flow emits:
     * 1. Cached data from Room immediately
     * 2. Updates when new logs are added locally
     * 3. Updates when Firestore sync completes
     * 
     * Logs are ordered by timestamp descending (most recent first).
     * 
     * Requirements: 7.1, 7.2, 12.1
     * 
     * @return Flow of training log lists
     */
    fun getTrainingHistory(): Flow<List<TrainingLog>>
    
    /**
     * Synchronize all pending training logs to Firestore.
     * 
     * Strategy:
     * 1. Query all logs with PENDING_SYNC or SYNC_FAILED status
     * 2. Attempt to sync each log with exponential backoff retry
     * 3. Update sync status based on result
     * 4. Return count of successfully synced logs
     * 
     * Exponential backoff:
     * - Attempt 1: immediate
     * - Attempt 2: 1 second delay
     * - Attempt 3: 2 seconds delay
     * - Max 3 attempts per log
     * 
     * Requirements: 6.5, 12.5
     * 
     * @return Result containing count of successfully synced logs
     */
    suspend fun syncPendingLogs(): Result<Int>

    /**
     * Returns a reactive count of training logs that are pending synchronization.
     * Emits updates whenever the sync status of any log changes.
     *
     * Requirements: 12.5
     */
    fun getPendingSyncCount(): kotlinx.coroutines.flow.Flow<Int>
}
