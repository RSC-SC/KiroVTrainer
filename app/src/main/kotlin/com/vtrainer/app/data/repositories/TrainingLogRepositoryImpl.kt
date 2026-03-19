package com.vtrainer.app.data.repositories

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.vtrainer.app.data.local.dao.TrainingLogDao
import com.vtrainer.app.data.mappers.toDomain
import com.vtrainer.app.data.mappers.toEntity
import com.vtrainer.app.domain.models.SyncStatus
import com.vtrainer.app.domain.models.TrainingLog
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.datetime.Clock
import kotlin.math.pow

/**
 * Implementation of TrainingLogRepository with offline-first strategy.
 * 
 * This repository follows the offline-first pattern:
 * 1. All writes go to Room database first (local-first)
 * 2. Firestore sync happens in background with retry logic
 * 3. Failed syncs are marked PENDING_SYNC for later retry
 * 4. Exponential backoff retry strategy for failed syncs
 * 
 * Requirements:
 * - 4.6: Save TrainingLog when workout completes (mobile)
 * - 5.6: Save TrainingLog when workout completes (watch)
 * - 6.5: Retry synchronization when connectivity restored
 * - 12.3: Offline operation with automatic sync
 * - 12.5: Automatically synchronize cached logs
 */
class TrainingLogRepositoryImpl(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val trainingLogDao: TrainingLogDao
) : TrainingLogRepository {
    
    private companion object {
        const val COLLECTION_TRAINING_LOGS = "training_logs"
        const val MAX_RETRY_ATTEMPTS = 3
    }
    
    /**
     * Get current user ID from Firebase Auth.
     * @throws IllegalStateException if user is not authenticated
     */
    private fun getCurrentUserId(): String {
        return auth.currentUser?.uid 
            ?: throw IllegalStateException("User must be authenticated")
    }
    
    /**
     * Save training log with offline-first strategy and background sync.
     * 
     * Steps:
     * 1. Save to Room with PENDING_SYNC status
     * 2. Attempt Firestore sync in background
     * 3. Update status to SYNCED on success, SYNC_FAILED on failure
     * 
     * Requirements: 4.6, 5.6, 12.3
     */
    override suspend fun saveTrainingLog(log: TrainingLog): Result<Unit> {
        return try {
            val userId = getCurrentUserId()
            
            // Ensure log belongs to current user
            val userLog = log.copy(userId = userId)
            
            // Step 1: Save locally first (always succeeds)
            trainingLogDao.insert(userLog.toEntity(SyncStatus.PENDING_SYNC))
            
            // Step 2: Attempt Firestore sync in background
            try {
                val firestoreData = userLog.toFirestoreMap()
                firestore.collection(COLLECTION_TRAINING_LOGS)
                    .document(userLog.logId)
                    .set(firestoreData)
                    .await()
                
                // Step 3: Update sync status to SYNCED
                trainingLogDao.updateSyncStatus(
                    logId = userLog.logId,
                    syncStatus = SyncStatus.SYNCED,
                    lastSyncAttempt = Clock.System.now().toEpochMilliseconds()
                )
                
                Result.success(Unit)
            } catch (e: Exception) {
                // Sync failed, but local save succeeded
                trainingLogDao.updateSyncStatus(
                    logId = userLog.logId,
                    syncStatus = SyncStatus.SYNC_FAILED,
                    lastSyncAttempt = Clock.System.now().toEpochMilliseconds()
                )
                
                // Return success because local save worked (offline-first)
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Get training history with reactive updates from Room.
     * 
     * Flow behavior:
     * 1. Immediately emits cached data from Room
     * 2. Emits updates when new logs are added
     * 3. Logs ordered by timestamp descending (most recent first)
     * 
     * Requirements: 7.1, 7.2, 12.1
     */
    override fun getTrainingHistory(): Flow<List<TrainingLog>> {
        val userId = getCurrentUserId()
        
        return trainingLogDao.getTrainingLogs(userId)
            .map { entities -> entities.map { it.toDomain() } }
    }
    
    /**
     * Synchronize all pending training logs with exponential backoff retry.
     * 
     * Strategy:
     * 1. Query logs with PENDING_SYNC or SYNC_FAILED status
     * 2. For each log, attempt sync with exponential backoff
     * 3. Update sync status based on result
     * 4. Return count of successfully synced logs
     * 
     * Exponential backoff delays:
     * - Attempt 1: 0ms (immediate)
     * - Attempt 2: 1000ms (1 second)
     * - Attempt 3: 2000ms (2 seconds)
     * 
     * Requirements: 6.5, 12.5
     */
    override suspend fun syncPendingLogs(): Result<Int> {
        return try {
            val userId = getCurrentUserId()
            
            // Get all logs that need syncing
            val pendingLogs = trainingLogDao.getPendingSyncLogs(
                userId = userId,
                syncStatuses = listOf(SyncStatus.PENDING_SYNC, SyncStatus.SYNC_FAILED)
            )
            
            var successCount = 0
            
            // Attempt to sync each log with exponential backoff
            for (logEntity in pendingLogs) {
                val log = logEntity.toDomain()
                val synced = syncLogWithRetry(log)
                
                if (synced) {
                    successCount++
                    trainingLogDao.updateSyncStatus(
                        logId = log.logId,
                        syncStatus = SyncStatus.SYNCED,
                        lastSyncAttempt = Clock.System.now().toEpochMilliseconds()
                    )
                } else {
                    trainingLogDao.updateSyncStatus(
                        logId = log.logId,
                        syncStatus = SyncStatus.SYNC_FAILED,
                        lastSyncAttempt = Clock.System.now().toEpochMilliseconds()
                    )
                }
            }
            
            Result.success(successCount)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Attempt to sync a single log with exponential backoff retry.
     * 
     * @param log The training log to sync
     * @return true if sync succeeded, false otherwise
     */
    private suspend fun syncLogWithRetry(log: TrainingLog): Boolean {
        var attempt = 0
        
        while (attempt < MAX_RETRY_ATTEMPTS) {
            try {
                // Apply exponential backoff delay (except for first attempt)
                if (attempt > 0) {
                    val delayMs = getBackoffDelay(attempt)
                    delay(delayMs)
                }
                
                // Attempt Firestore sync
                val firestoreData = log.toFirestoreMap()
                firestore.collection(COLLECTION_TRAINING_LOGS)
                    .document(log.logId)
                    .set(firestoreData)
                    .await()
                
                // Success!
                return true
            } catch (e: Exception) {
                attempt++
                println("Sync attempt $attempt failed for log ${log.logId}: ${e.message}")
                
                // If max attempts reached, give up
                if (attempt >= MAX_RETRY_ATTEMPTS) {
                    return false
                }
            }
        }
        
        return false
    }
    
    /**
     * Calculate exponential backoff delay in milliseconds.
     * 
     * Formula: 2^(attempt-1) * 1000ms
     * - Attempt 1: 0ms (no delay)
     * - Attempt 2: 1000ms (1 second)
     * - Attempt 3: 2000ms (2 seconds)
     * 
     * @param attempt The current attempt number (1-based)
     * @return Delay in milliseconds
     */
    private fun getBackoffDelay(attempt: Int): Long {
        return (2.0.pow(attempt - 1) * 1000).toLong()
    }
}

/**
 * Extension function to convert TrainingLog to Firestore map.
 */
private fun TrainingLog.toFirestoreMap(): Map<String, Any> {
    val timestampDate = java.util.Date(timestamp.toEpochMilliseconds())
    
    return mapOf(
        "logId" to logId,
        "userId" to userId,
        "workoutPlanId" to (workoutPlanId ?: ""),
        "workoutDayName" to workoutDayName,
        "timestamp" to com.google.firebase.Timestamp(timestampDate),
        "origin" to origin,
        "duration" to duration,
        "totalCalories" to (totalCalories ?: 0),
        "exercises" to exercises.map { exercise ->
            mapOf(
                "exerciseId" to exercise.exerciseId,
                "sets" to exercise.sets.map { set ->
                    mapOf(
                        "setNumber" to set.setNumber,
                        "reps" to set.reps,
                        "weight" to set.weight,
                        "restSeconds" to set.restSeconds,
                        "heartRate" to (set.heartRate ?: 0),
                        "completedAt" to com.google.firebase.Timestamp(
                            java.util.Date(set.completedAt.toEpochMilliseconds())
                        )
                    )
                },
                "totalVolume" to exercise.totalVolume,
                "isPersonalRecord" to exercise.isPersonalRecord,
                "recordType" to (exercise.recordType?.name ?: "")
            )
        },
        "totalVolume" to totalVolume,
        "syncStatus" to "synced",
        "createdAt" to com.google.firebase.Timestamp(timestampDate)
    )
}
