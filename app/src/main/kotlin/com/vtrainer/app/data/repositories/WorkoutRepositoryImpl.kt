package com.vtrainer.app.data.repositories

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.vtrainer.app.data.local.dao.WorkoutPlanDao
import com.vtrainer.app.data.mappers.toDomain
import com.vtrainer.app.data.mappers.toEntity
import com.vtrainer.app.domain.models.SyncStatus
import com.vtrainer.app.domain.models.WorkoutPlan
import com.vtrainer.app.util.VTrainerLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.datetime.Clock

/**
 * Implementation of WorkoutRepository with offline-first strategy.
 * 
 * This repository follows the offline-first pattern:
 * 1. All writes go to Room database first (local-first)
 * 2. Firestore sync happens in background
 * 3. Failed syncs are marked PENDING_SYNC for retry
 * 4. Firestore listener updates Room cache for real-time sync
 * 
 * Requirements:
 * - 3.4: Store workout plans in Firestore
 * - 3.6: Edit and delete workout plans
 * - 12.1: Room database for local caching
 * - 12.3: Offline operation with automatic sync
 */
class WorkoutRepositoryImpl(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val workoutPlanDao: WorkoutPlanDao
) : WorkoutRepository {
    
    private companion object {
        const val COLLECTION_WORKOUT_PLANS = "workout_plans"
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
     * Get all workout plans with real-time updates from both Room and Firestore.
     * 
     * Flow behavior:
     * 1. Immediately emits cached data from Room
     * 2. Sets up Firestore listener for real-time updates
     * 3. When Firestore data changes, updates Room and emits new data
     * 4. Continues emitting Room data even if Firestore is unavailable
     * 
     * Requirements: 3.4, 12.1
     */
    override fun getWorkoutPlans(): Flow<List<WorkoutPlan>> {
        val userId = auth.currentUser?.uid ?: return kotlinx.coroutines.flow.flowOf(emptyList())
        return callbackFlow {
        
        // Set up Firestore listener for real-time updates
        val listenerRegistration: ListenerRegistration = firestore
            .collection(COLLECTION_WORKOUT_PLANS)
            .whereEqualTo("userId", userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    // Log error but don't fail - continue with cached data
                    VTrainerLogger.logNetworkError(
                        context = "WorkoutRepository.getWorkoutPlans",
                        errorCode = error.code.toString(),
                        message = "Firestore listener error"
                    )
                    return@addSnapshotListener
                }
                
                snapshot?.documents?.forEach { document ->
                    try {
                        val plan = document.toWorkoutPlan()
                        // Update Room cache with Firestore data
                        CoroutineScope(Dispatchers.IO).launch {
                            workoutPlanDao.insert(plan.toEntity(SyncStatus.SYNCED))
                        }
                    } catch (e: Exception) {
                        VTrainerLogger.logNetworkError(
                            context = "WorkoutRepository.parseWorkoutPlan",
                            errorCode = e.javaClass.simpleName,
                            message = "Error parsing workout plan document"
                        )
                    }
                }
            }
        
        // Emit data from Room (reactive to both local and Firestore updates)
        workoutPlanDao.getWorkoutPlans(userId)
            .map { entities -> entities.map { it.toDomain() } }
            .collect { plans ->
                trySend(plans)
            }
        
        // Clean up listener when Flow is cancelled
        awaitClose {
            listenerRegistration.remove()
        }
        }
    }
    
    /**
     * Save workout plan with offline-first strategy.
     * 
     * Steps:
     * 1. Save to Room with PENDING_SYNC status
     * 2. Attempt Firestore sync
     * 3. Update status to SYNCED on success, SYNC_FAILED on failure
     * 
     * Requirements: 3.4, 12.3
     */
    override suspend fun saveWorkoutPlan(plan: WorkoutPlan): Result<Unit> {
        return try {
            val userId = getCurrentUserId()
            
            // Ensure plan belongs to current user
            val userPlan = plan.copy(userId = userId)
            
            // Step 1: Save locally first (always succeeds)
            workoutPlanDao.insert(userPlan.toEntity(SyncStatus.PENDING_SYNC))
            
            // Step 2: Attempt Firestore sync
            val trace = VTrainerLogger.startTrace("workout_plan_sync")
            try {
                val firestoreData = userPlan.toFirestoreMap()
                firestore.collection(COLLECTION_WORKOUT_PLANS)
                    .document(userPlan.planId)
                    .set(firestoreData)
                    .await()
                
                // Step 3: Update sync status to SYNCED
                workoutPlanDao.updateSyncStatus(
                    planId = userPlan.planId,
                    syncStatus = SyncStatus.SYNCED,
                    lastSyncAttempt = Clock.System.now().toEpochMilliseconds()
                )
                VTrainerLogger.logSyncSuccess("workout_plan_sync")
                
                Result.success(Unit)
            } catch (e: Exception) {
                // Sync failed, but local save succeeded
                workoutPlanDao.updateSyncStatus(
                    planId = userPlan.planId,
                    syncStatus = SyncStatus.SYNC_FAILED,
                    lastSyncAttempt = Clock.System.now().toEpochMilliseconds()
                )
                VTrainerLogger.logSyncException("workout_plan_sync", e)
                
                // Return success because local save worked (offline-first)
                Result.success(Unit)
            } finally {
                VTrainerLogger.stopTrace(trace)
            }
        } catch (e: Exception) {
            VTrainerLogger.logSyncException("workout_plan_save", e)
            Result.failure(e)
        }
    }
    
    /**
     * Delete workout plan with offline-first strategy.
     * 
     * Steps:
     * 1. Delete from Room first
     * 2. Attempt Firestore deletion
     * 3. If Firestore fails, log but don't fail operation (already deleted locally)
     * 
     * Requirements: 3.6, 12.3
     */
    override suspend fun deleteWorkoutPlan(planId: String): Result<Unit> {
        return try {
            // Step 1: Delete locally first
            workoutPlanDao.deleteById(planId)
            
            // Step 2: Attempt Firestore deletion
            try {
                firestore.collection(COLLECTION_WORKOUT_PLANS)
                    .document(planId)
                    .delete()
                    .await()
            } catch (e: Exception) {
                // Log error but don't fail - local deletion succeeded
                VTrainerLogger.logNetworkError(
                    context = "WorkoutRepository.deleteWorkoutPlan",
                    errorCode = e.javaClass.simpleName,
                    message = "Firestore deletion failed"
                )
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

/**
 * Extension function to convert Firestore document to WorkoutPlan.
 */
private fun com.google.firebase.firestore.DocumentSnapshot.toWorkoutPlan(): WorkoutPlan {
    val data = this.data ?: throw IllegalArgumentException("Document data is null")
    
    @Suppress("UNCHECKED_CAST")
    val trainingDaysData = data["trainingDays"] as? List<Map<String, Any>> ?: emptyList()
    
    val trainingDays = trainingDaysData.map { dayData ->
        val exercisesData = dayData["exercises"] as? List<Map<String, Any>> ?: emptyList()
        
        com.vtrainer.app.domain.models.TrainingDay(
            dayName = dayData["dayName"] as? String ?: "",
            exercises = exercisesData.map { exerciseData ->
                com.vtrainer.app.domain.models.PlannedExercise(
                    exerciseId = exerciseData["exerciseId"] as? String ?: "",
                    order = (exerciseData["order"] as? Long)?.toInt() ?: 0,
                    sets = (exerciseData["sets"] as? Long)?.toInt() ?: 0,
                    reps = (exerciseData["reps"] as? Long)?.toInt() ?: 0,
                    restSeconds = (exerciseData["restSeconds"] as? Long)?.toInt() ?: 0,
                    notes = exerciseData["notes"] as? String
                )
            }
        )
    }
    
    return WorkoutPlan(
        planId = this.id,
        userId = data["userId"] as? String ?: "",
        name = data["name"] as? String ?: "",
        description = data["description"] as? String,
        trainingDays = trainingDays,
        createdAt = kotlinx.datetime.Instant.fromEpochMilliseconds(
            (data["createdAt"] as? com.google.firebase.Timestamp)?.toDate()?.time ?: 0
        ),
        updatedAt = kotlinx.datetime.Instant.fromEpochMilliseconds(
            (data["updatedAt"] as? com.google.firebase.Timestamp)?.toDate()?.time ?: 0
        )
    )
}

/**
 * Extension function to convert WorkoutPlan to Firestore map.
 */
private fun WorkoutPlan.toFirestoreMap(): Map<String, Any> {
    val createdAtDate = java.util.Date(createdAt.toEpochMilliseconds())
    val updatedAtDate = java.util.Date(updatedAt.toEpochMilliseconds())
    
    return mapOf(
        "planId" to planId,
        "userId" to userId,
        "name" to name,
        "description" to (description ?: ""),
        "trainingDays" to trainingDays.map { day ->
            mapOf(
                "dayName" to day.dayName,
                "exercises" to day.exercises.map { exercise ->
                    mapOf(
                        "exerciseId" to exercise.exerciseId,
                        "order" to exercise.order,
                        "sets" to exercise.sets,
                        "reps" to exercise.reps,
                        "restSeconds" to exercise.restSeconds,
                        "notes" to (exercise.notes ?: "")
                    )
                }
            )
        },
        "createdAt" to com.google.firebase.Timestamp(createdAtDate),
        "updatedAt" to com.google.firebase.Timestamp(updatedAtDate)
    )
}
