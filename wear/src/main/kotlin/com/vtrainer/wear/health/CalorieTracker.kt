package com.vtrainer.wear.health

import android.content.Context
import androidx.health.services.client.ExerciseClient
import androidx.health.services.client.ExerciseUpdateCallback
import androidx.health.services.client.HealthServices
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.ExerciseConfig
import androidx.health.services.client.data.ExerciseLapSummary
import androidx.health.services.client.data.ExerciseType
import androidx.health.services.client.data.ExerciseUpdate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.guava.await

/**
 * Tracks calories burned during a workout using the Health Services Exercise API on Wear OS.
 *
 * Responsibilities:
 * - Start calorie tracking when a workout begins (Req 10.1)
 * - Track cumulative calories burned throughout the session (Req 10.2)
 * - Provide total calories at workout completion (Req 10.3)
 * - Handle cases where calorie tracking is unavailable gracefully
 *
 * Validates: Requirements 10.1, 10.2, 10.3
 *
 * @param context Android [Context] used to obtain the [HealthServices] client.
 */
class CalorieTracker(private val context: Context) {

    private val healthServicesClient = HealthServices.getClient(context)
    private val exerciseClient: ExerciseClient = healthServicesClient.exerciseClient

    private val _totalCalories = MutableStateFlow(0)
    /** Cumulative calories burned since tracking started. Validates: Requirement 10.2 */
    val totalCalories: StateFlow<Int> = _totalCalories.asStateFlow()

    private var isTracking = false

    private val exerciseUpdateCallback = object : ExerciseUpdateCallback {
        override fun onExerciseUpdateReceived(update: ExerciseUpdate) {
            // Extract cumulative calories from the exercise update
            try {
                @Suppress("UNCHECKED_CAST")
                val metricsMap = update.latestMetrics
                val caloriesData = metricsMap.getData(DataType.CALORIES_TOTAL)
                // CumulativeDataPoint list — use reflection-safe access
                val list = caloriesData as? List<*>
                if (!list.isNullOrEmpty()) {
                    val last = list.last()
                    val valueMethod = last?.javaClass?.getMethod("getValue")
                    val value = valueMethod?.invoke(last) as? Double
                    if (value != null) _totalCalories.value = value.toInt()
                }
            } catch (_: Exception) {
                // Calorie data unavailable in this update
            }
        }

        override fun onLapSummaryReceived(lapSummary: ExerciseLapSummary) {
            // Not used for calorie tracking
        }

        override fun onRegistered() {
            // Callback registered successfully
        }

        override fun onRegistrationFailed(throwable: Throwable) {
            // Registration failed — calorie tracking unavailable, continue without it
        }

        override fun onAvailabilityChanged(
            dataType: androidx.health.services.client.data.DataType<*, *>,
            availability: androidx.health.services.client.data.Availability
        ) {
            // Availability changes handled gracefully — no action needed
        }
    }

    /**
     * Returns true if calorie tracking is supported on this device.
     *
     * Validates: Requirement 10.1
     */
    suspend fun isCalorieTrackingSupported(): Boolean {
        return try {
            val capabilities = exerciseClient.getCapabilitiesAsync().await()
            val strengthCapabilities = capabilities.getExerciseTypeCapabilities(ExerciseType.STRENGTH_TRAINING)
            DataType.CALORIES_TOTAL in strengthCapabilities.supportedDataTypes
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Starts calorie tracking for a strength training workout.
     *
     * Registers the exercise update callback and begins an exercise session
     * configured to track calories. If calorie tracking is not supported,
     * this method returns without error.
     *
     * Validates: Requirement 10.1, 10.2
     */
    suspend fun startTracking() {
        if (isTracking) return

        try {
            // Start the exercise session with calorie tracking
            val exerciseConfig = ExerciseConfig.builder(ExerciseType.STRENGTH_TRAINING)
                .setDataTypes(setOf(DataType.CALORIES_TOTAL))
                .setIsAutoPauseAndResumeEnabled(false)
                .build()

            exerciseClient.setUpdateCallback(exerciseUpdateCallback)
            exerciseClient.startExerciseAsync(exerciseConfig).await()
            isTracking = true
            _totalCalories.value = 0
        } catch (e: Exception) {
            // Calorie tracking unavailable — continue workout without it (Req 10.3)
            isTracking = false
        }
    }

    /**
     * Stops calorie tracking and returns the total calories burned.
     *
     * Validates: Requirement 10.3
     *
     * @return Total calories burned during the session, or null if tracking was not active.
     */
    suspend fun stopTracking(): Int? {
        if (!isTracking) return null

        return try {
            exerciseClient.endExerciseAsync().await()
            exerciseClient.clearUpdateCallbackAsync(exerciseUpdateCallback).await()
            isTracking = false
            _totalCalories.value.takeIf { it > 0 }
        } catch (e: Exception) {
            isTracking = false
            _totalCalories.value.takeIf { it > 0 }
        }
    }

    /**
     * Returns the current cumulative calorie count without stopping tracking.
     *
     * Validates: Requirement 10.2
     */
    fun getCurrentCalories(): Int = _totalCalories.value
}
