package com.vtrainer.app.data.repositories

import com.vtrainer.app.domain.models.WorkoutPlan
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for workout plan operations.
 * Implements offline-first strategy with local Room cache and Firestore sync.
 * 
 * Requirements:
 * - 3.4: Store workout plans in Firestore
 * - 3.6: Allow editing and deletion of workout plans
 * - 12.1: Cache workout plans locally using Room database
 * - 12.3: Allow offline operation with automatic sync when connectivity restored
 */
interface WorkoutRepository {
    
    /**
     * Get all workout plans for the current user.
     * Returns a Flow that emits updates from both Room and Firestore.
     * 
     * Strategy:
     * 1. Emit cached data from Room immediately
     * 2. Listen to Firestore for real-time updates
     * 3. Update Room cache when Firestore data changes
     * 
     * @return Flow of workout plan lists
     */
    fun getWorkoutPlans(): Flow<List<WorkoutPlan>>
    
    /**
     * Save a workout plan with offline-first strategy.
     * 
     * Strategy:
     * 1. Save to Room database first (always succeeds)
     * 2. Attempt to sync with Firestore
     * 3. If sync fails, mark as PENDING_SYNC for later retry
     * 
     * Requirements: 3.4, 12.3
     * 
     * @param plan The workout plan to save
     * @return Result indicating success or failure
     */
    suspend fun saveWorkoutPlan(plan: WorkoutPlan): Result<Unit>
    
    /**
     * Delete a workout plan with offline-first strategy.
     * 
     * Strategy:
     * 1. Delete from Room database first
     * 2. Attempt to delete from Firestore
     * 3. If sync fails, queue deletion for retry
     * 
     * Requirements: 3.6, 12.3
     * 
     * @param planId The ID of the workout plan to delete
     * @return Result indicating success or failure
     */
    suspend fun deleteWorkoutPlan(planId: String): Result<Unit>
}
