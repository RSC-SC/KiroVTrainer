package com.vtrainer.app.data.local.dao

import androidx.room.*
import com.vtrainer.app.data.local.entities.TrainingLogEntity
import com.vtrainer.app.domain.models.SyncStatus
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for TrainingLogEntity.
 * Provides queries for CRUD operations and sync status filtering.
 * 
 * Requirements: 12.1 - Room database for local caching with offline-first strategy
 */
@Dao
interface TrainingLogDao {
    
    /**
     * Get all training logs for a specific user, ordered by timestamp descending.
     * Returns a Flow for reactive updates.
     */
    @Query("SELECT * FROM training_logs WHERE userId = :userId ORDER BY timestamp DESC")
    fun getTrainingLogs(userId: String): Flow<List<TrainingLogEntity>>
    
    /**
     * Get a specific training log by ID.
     */
    @Query("SELECT * FROM training_logs WHERE logId = :logId")
    suspend fun getTrainingLogById(logId: String): TrainingLogEntity?
    
    /**
     * Get training logs within a specific time range.
     * Useful for weekly/monthly statistics.
     */
    @Query("SELECT * FROM training_logs WHERE userId = :userId AND timestamp BETWEEN :startTime AND :endTime ORDER BY timestamp DESC")
    suspend fun getTrainingLogsByTimeRange(userId: String, startTime: Long, endTime: Long): List<TrainingLogEntity>
    
    /**
     * Get all training logs with a specific sync status.
     * Used for filtering logs that need synchronization.
     */
    @Query("SELECT * FROM training_logs WHERE syncStatus = :syncStatus")
    suspend fun getTrainingLogsBySyncStatus(syncStatus: SyncStatus): List<TrainingLogEntity>
    
    /**
     * Get all training logs pending synchronization for a user.
     * Combines userId and sync status filtering.
     */
    @Query("SELECT * FROM training_logs WHERE userId = :userId AND syncStatus IN (:syncStatuses)")
    suspend fun getPendingSyncLogs(userId: String, syncStatuses: List<SyncStatus>): List<TrainingLogEntity>

    /**
     * Get a reactive count of training logs with PENDING_SYNC or SYNC_FAILED status for a user.
     * Emits updates whenever sync status changes.
     */
    @Query("SELECT COUNT(*) FROM training_logs WHERE userId = :userId AND syncStatus IN ('PENDING_SYNC', 'SYNC_FAILED')")
    fun getPendingSyncCount(userId: String): Flow<Int>
    
    /**
     * Get training logs for a specific workout plan.
     */
    @Query("SELECT * FROM training_logs WHERE userId = :userId AND workoutPlanId = :workoutPlanId ORDER BY timestamp DESC")
    fun getTrainingLogsByWorkoutPlan(userId: String, workoutPlanId: String): Flow<List<TrainingLogEntity>>
    
    /**
     * Insert a new training log.
     * If a log with the same ID exists, it will be replaced.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(trainingLog: TrainingLogEntity)
    
    /**
     * Insert multiple training logs.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(trainingLogs: List<TrainingLogEntity>)
    
    /**
     * Update an existing training log.
     */
    @Update
    suspend fun update(trainingLog: TrainingLogEntity)
    
    /**
     * Update only the sync status and last sync attempt timestamp.
     * Useful for updating sync state without modifying log data.
     */
    @Query("UPDATE training_logs SET syncStatus = :syncStatus, lastSyncAttempt = :lastSyncAttempt WHERE logId = :logId")
    suspend fun updateSyncStatus(logId: String, syncStatus: SyncStatus, lastSyncAttempt: Long)
    
    /**
     * Delete a specific training log.
     */
    @Delete
    suspend fun delete(trainingLog: TrainingLogEntity)
    
    /**
     * Delete a training log by ID.
     */
    @Query("DELETE FROM training_logs WHERE logId = :logId")
    suspend fun deleteById(logId: String)
    
    /**
     * Delete all training logs for a specific user.
     * Used for cleanup operations.
     */
    @Query("DELETE FROM training_logs WHERE userId = :userId")
    suspend fun deleteAllForUser(userId: String)
    
    /**
     * Get the count of training logs for a user.
     */
    @Query("SELECT COUNT(*) FROM training_logs WHERE userId = :userId")
    suspend fun getTrainingLogCount(userId: String): Int
    
    /**
     * Get the total volume across all training logs for a user within a time range.
     * Used for progress statistics.
     */
    @Query("SELECT SUM(totalVolume) FROM training_logs WHERE userId = :userId AND timestamp BETWEEN :startTime AND :endTime")
    suspend fun getTotalVolumeByTimeRange(userId: String, startTime: Long, endTime: Long): Int?
    
    /**
     * Get the total calories burned across all training logs for a user within a time range.
     * Used for progress statistics.
     */
    @Query("SELECT SUM(totalCalories) FROM training_logs WHERE userId = :userId AND timestamp BETWEEN :startTime AND :endTime")
    suspend fun getTotalCaloriesByTimeRange(userId: String, startTime: Long, endTime: Long): Int?
}
