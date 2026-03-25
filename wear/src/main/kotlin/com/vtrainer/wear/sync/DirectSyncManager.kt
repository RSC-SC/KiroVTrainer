package com.vtrainer.wear.sync

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import com.google.gson.Gson
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await

/**
 * Request model for the syncWorkout Cloud Function.
 *
 * Validates: Requirements 6.1, 6.2
 */
data class SyncWorkoutRequest(
    val logId: String,
    val userId: String,
    val workoutPlanId: String?,
    val workoutDayName: String,
    val timestamp: Long,           // epoch milliseconds
    val origin: String,
    val duration: Int,
    val totalCalories: Int?,
    val totalVolume: Int,
    val exercises: List<ExerciseLogRequest>
)

data class ExerciseLogRequest(
    val exerciseId: String,
    val totalVolume: Int,
    val sets: List<SetLogRequest>
)

data class SetLogRequest(
    val setNumber: Int,
    val reps: Int,
    val weight: Double,
    val restSeconds: Int,
    val heartRate: Int?,
    val completedAt: Long          // epoch milliseconds
)

/**
 * Response model from the syncWorkout Cloud Function.
 */
data class SyncWorkoutResponse(
    val success: Boolean,
    val logId: String?,
    val error: String?
)

/**
 * Sealed class representing the result of a sync attempt.
 *
 * Validates: Requirements 6.1, 6.5
 */
sealed class SyncResult {
    /** Sync completed successfully. */
    data class Success(val logId: String) : SyncResult()
    /** Sync failed after all retries; the log should be cached locally. */
    data class Failure(val error: String, val shouldCache: Boolean = true) : SyncResult()
}

/**
 * Manages direct watch-to-cloud synchronization via Firebase Cloud Functions.
 *
 * Responsibilities:
 * - Inject Firebase Auth token into each request (Req 6.2)
 * - Call the syncWorkout HTTPS callable function (Req 6.1)
 * - Implement exponential backoff retry logic with max 3 attempts (Req 6.5)
 * - Return [SyncResult.Failure] with shouldCache=true when all retries fail (Req 6.5)
 *
 * Validates: Requirements 6.1, 6.2, 6.5
 *
 * @param functions [FirebaseFunctions] instance for calling Cloud Functions.
 * @param auth [FirebaseAuth] instance for obtaining the current user's ID token.
 * @param gson [Gson] instance for JSON serialization.
 */
class DirectSyncManager(
    private val functions: FirebaseFunctions = FirebaseFunctions.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val gson: Gson = Gson()
) {

    companion object {
        private const val MAX_RETRIES = 3
        private const val INITIAL_BACKOFF_MS = 1_000L
        private const val BACKOFF_MULTIPLIER = 2.0
    }

    /**
     * Syncs a training log to the Cloud Function with exponential backoff retry.
     *
     * Retry schedule:
     * - Attempt 1: immediate
     * - Attempt 2: after 1 second
     * - Attempt 3: after 2 seconds
     *
     * If all attempts fail, returns [SyncResult.Failure] with [SyncResult.Failure.shouldCache] = true
     * so the caller can persist the log locally for later retry.
     *
     * Validates: Requirements 6.1, 6.2, 6.5
     *
     * @param request The [SyncWorkoutRequest] to send.
     * @return [SyncResult.Success] on success, [SyncResult.Failure] after all retries exhausted.
     */
    suspend fun syncWorkout(request: SyncWorkoutRequest): SyncResult {
        var lastError = "Unknown error"
        var backoffMs = INITIAL_BACKOFF_MS

        repeat(MAX_RETRIES) { attempt ->
            if (attempt > 0) {
                delay(backoffMs)
                backoffMs = (backoffMs * BACKOFF_MULTIPLIER).toLong()
            }

            val result = trySyncOnce(request)
            if (result is SyncResult.Success) return result
            if (result is SyncResult.Failure) lastError = result.error
        }

        return SyncResult.Failure(
            error = "Sync failed after $MAX_RETRIES attempts: $lastError",
            shouldCache = true
        )
    }

    /**
     * Makes a single attempt to call the syncWorkout Cloud Function.
     *
     * Validates: Requirements 6.1, 6.2
     */
    private suspend fun trySyncOnce(request: SyncWorkoutRequest): SyncResult {
        return try {
            // Req 6.2 — Firebase Auth token is automatically injected by the callable function SDK
            val currentUser = auth.currentUser
                ?: return SyncResult.Failure("User not authenticated", shouldCache = true)

            val data = mapOf(
                "logId" to request.logId,
                "userId" to request.userId,
                "workoutPlanId" to request.workoutPlanId,
                "workoutDayName" to request.workoutDayName,
                "timestamp" to request.timestamp,
                "origin" to request.origin,
                "duration" to request.duration,
                "totalCalories" to request.totalCalories,
                "totalVolume" to request.totalVolume,
                "exercises" to request.exercises.map { exercise ->
                    mapOf(
                        "exerciseId" to exercise.exerciseId,
                        "totalVolume" to exercise.totalVolume,
                        "sets" to exercise.sets.map { set ->
                            mapOf(
                                "setNumber" to set.setNumber,
                                "reps" to set.reps,
                                "weight" to set.weight,
                                "restSeconds" to set.restSeconds,
                                "heartRate" to set.heartRate,
                                "completedAt" to set.completedAt
                            )
                        }
                    )
                }
            )

            val result = functions
                .getHttpsCallable("syncWorkout")
                .call(data)
                .await()

            @Suppress("UNCHECKED_CAST")
            val responseMap = result.data as? Map<String, Any>
            val success = responseMap?.get("success") as? Boolean ?: false
            val logId = responseMap?.get("logId") as? String ?: request.logId
            val error = responseMap?.get("error") as? String

            if (success) {
                SyncResult.Success(logId = logId)
            } else {
                SyncResult.Failure(error = error ?: "Server returned failure", shouldCache = false)
            }
        } catch (e: Exception) {
            SyncResult.Failure(error = e.message ?: "Network error", shouldCache = true)
        }
    }
}
