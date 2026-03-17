package com.vtrainer.app.data.local.dao

import androidx.room.*
import com.vtrainer.app.data.local.entities.WorkoutPlanEntity
import com.vtrainer.app.domain.models.SyncStatus
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for WorkoutPlanEntity.
 * Provides queries for CRUD operations and sync status filtering.
 * 
 * Requirements: 12.1 - Room database for local caching with offline-first strategy
 */
@Dao
interface WorkoutPlanDao {
    
    /**
     * Get all workout plans for a specific user.
     * Returns a Flow for reactive updates.
     */
    @Query("SELECT * FROM workout_plans WHERE userId = :userId ORDER BY updatedAt DESC")
    fun getWorkoutPlans(userId: String): Flow<List<WorkoutPlanEntity>>
    
    /**
     * Get a specific workout plan by ID.
     */
    @Query("SELECT * FROM workout_plans WHERE planId = :planId")
    suspend fun getWorkoutPlanById(planId: String): WorkoutPlanEntity?
    
    /**
     * Get all workout plans with a specific sync status.
     * Used for filtering plans that need synchronization.
     */
    @Query("SELECT * FROM workout_plans WHERE syncStatus = :syncStatus")
    suspend fun getWorkoutPlansBySyncStatus(syncStatus: SyncStatus): List<WorkoutPlanEntity>
    
    /**
     * Get all workout plans pending synchronization for a user.
     * Combines userId and sync status filtering.
     */
    @Query("SELECT * FROM workout_plans WHERE userId = :userId AND syncStatus IN (:syncStatuses)")
    suspend fun getPendingSyncPlans(userId: String, syncStatuses: List<SyncStatus>): List<WorkoutPlanEntity>
    
    /**
     * Insert a new workout plan.
     * If a plan with the same ID exists, it will be replaced.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(workoutPlan: WorkoutPlanEntity)
    
    /**
     * Insert multiple workout plans.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(workoutPlans: List<WorkoutPlanEntity>)
    
    /**
     * Update an existing workout plan.
     */
    @Update
    suspend fun update(workoutPlan: WorkoutPlanEntity)
    
    /**
     * Update only the sync status and last sync attempt timestamp.
     * Useful for updating sync state without modifying plan data.
     */
    @Query("UPDATE workout_plans SET syncStatus = :syncStatus, lastSyncAttempt = :lastSyncAttempt WHERE planId = :planId")
    suspend fun updateSyncStatus(planId: String, syncStatus: SyncStatus, lastSyncAttempt: Long)
    
    /**
     * Delete a specific workout plan.
     */
    @Delete
    suspend fun delete(workoutPlan: WorkoutPlanEntity)
    
    /**
     * Delete a workout plan by ID.
     */
    @Query("DELETE FROM workout_plans WHERE planId = :planId")
    suspend fun deleteById(planId: String)
    
    /**
     * Delete all workout plans for a specific user.
     * Used for cleanup operations.
     */
    @Query("DELETE FROM workout_plans WHERE userId = :userId")
    suspend fun deleteAllForUser(userId: String)
    
    /**
     * Get the count of workout plans for a user.
     */
    @Query("SELECT COUNT(*) FROM workout_plans WHERE userId = :userId")
    suspend fun getWorkoutPlanCount(userId: String): Int
}
