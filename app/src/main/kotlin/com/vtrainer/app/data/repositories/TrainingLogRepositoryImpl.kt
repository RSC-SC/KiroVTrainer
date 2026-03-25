package com.vtrainer.app.data.repositories

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.vtrainer.app.data.local.dao.TrainingLogDao
import com.vtrainer.app.data.mappers.toDomain
import com.vtrainer.app.data.mappers.toEntity
import com.vtrainer.app.domain.models.SyncStatus
import com.vtrainer.app.domain.models.TrainingLog
import com.vtrainer.app.util.VTrainerLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.datetime.Clock
import kotlin.math.pow

/**
 * Implementation of TrainingLogRepository with offline-first strategy.
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
    
    override suspend fun saveTrainingLog(log: TrainingLog): Result<Unit> {
        return try {
            val userId = getCurrentUserId()
            val userLog = log.copy(userId = userId)
            
            trainingLogDao.insert(userLog.toEntity(SyncStatus.PENDING_SYNC))
            
            val trace = VTrainerLogger.startTrace("training_log_sync")
            try {
                val firestoreData = userLog.toFirestoreMap()
                firestore.collection(COLLECTION_TRAINING_LOGS)
                    .document(userLog.logId)
                    .set(firestoreData)
                    .await()
                
                trainingLogDao.updateSyncStatus(
                    logId = userLog.logId,
                    syncStatus = SyncStatus.SYNCED,
                    lastSyncAttempt = Clock.System.now().toEpochMilliseconds()
                )
                VTrainerLogger.addTraceMetric(trace, "items_synced", 1)
                VTrainerLogger.logSyncSuccess("training_log_sync")
                
                Result.success(Unit)
            } catch (e: Exception) {
                trainingLogDao.updateSyncStatus(
                    logId = userLog.logId,
                    syncStatus = SyncStatus.SYNC_FAILED,
                    lastSyncAttempt = Clock.System.now().toEpochMilliseconds()
                )
                VTrainerLogger.logSyncException("training_log_sync", e)
                Result.success(Unit)
            } finally {
                VTrainerLogger.stopTrace(trace)
            }
        } catch (e: Exception) {
            VTrainerLogger.logSyncException("training_log_save", e)
            Result.failure(e)
        }
    }
    
    override fun getTrainingHistory(): Flow<List<TrainingLog>> {
        val userId = auth.currentUser?.uid ?: return kotlinx.coroutines.flow.flowOf(emptyList())

        CoroutineScope(Dispatchers.IO).launch {
            refreshTrainingLogsFromFirestore(userId)
        }

        return trainingLogDao.getTrainingLogs(userId)
            .map { entities -> entities.map { it.toDomain() } }
    }

    private suspend fun refreshTrainingLogsFromFirestore(userId: String) {
        try {
            firestore.collection(COLLECTION_TRAINING_LOGS)
                .whereEqualTo("userId", userId)
                .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(100)
                .get()
                .await()
        } catch (_: Exception) {
        }
    }
    
    override suspend fun syncPendingLogs(): Result<Int> {
        return try {
            // Se não houver usuário, retornamos sucesso com 0 itens sincronizados 
            // em vez de lançar exceção, para evitar loops de erro no WorkManager.
            val userId = auth.currentUser?.uid ?: return Result.success(0)
            
            val pendingLogs = trainingLogDao.getPendingSyncLogs(
                userId = userId,
                syncStatuses = listOf(SyncStatus.PENDING_SYNC, SyncStatus.SYNC_FAILED)
            )
            
            if (pendingLogs.isEmpty()) return Result.success(0)

            val trace = VTrainerLogger.startTrace("pending_logs_sync")
            VTrainerLogger.addTraceMetric(trace, "pending_count", pendingLogs.size.toLong())
            var successCount = 0
            
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
            
            VTrainerLogger.addTraceMetric(trace, "synced_count", successCount.toLong())
            VTrainerLogger.stopTrace(trace)
            VTrainerLogger.logSyncSuccess("pending_logs_sync", successCount)
            
            Result.success(successCount)
        } catch (e: Exception) {
            VTrainerLogger.logSyncException("pending_logs_sync", e)
            Result.failure(e)
        }
    }
    
    private suspend fun syncLogWithRetry(log: TrainingLog): Boolean {
        var attempt = 0
        while (attempt < MAX_RETRY_ATTEMPTS) {
            try {
                if (attempt > 0) {
                    delay(getBackoffDelay(attempt))
                }
                
                val firestoreData = log.toFirestoreMap()
                firestore.collection(COLLECTION_TRAINING_LOGS)
                    .document(log.logId)
                    .set(firestoreData)
                    .await()
                
                return true
            } catch (e: Exception) {
                attempt++
                if (attempt >= MAX_RETRY_ATTEMPTS) return false
            }
        }
        return false
    }
    
    private fun getBackoffDelay(attempt: Int): Long {
        return (2.0.pow(attempt - 1) * 1000).toLong()
    }

    override fun getPendingSyncCount(): Flow<Int> {
        val userId = auth.currentUser?.uid ?: return kotlinx.coroutines.flow.flowOf(0)
        return trainingLogDao.getPendingSyncCount(userId)
    }
}

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
