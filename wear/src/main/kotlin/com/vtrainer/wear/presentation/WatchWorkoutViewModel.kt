package com.vtrainer.wear.presentation

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.vtrainer.wear.health.CalorieTracker
import com.vtrainer.wear.health.HeartRateMonitor
import com.vtrainer.wear.health.HeartRateResult
import com.vtrainer.wear.sync.DirectSyncManager
import com.vtrainer.wear.sync.ExerciseLogRequest
import com.vtrainer.wear.sync.SetLogRequest
import com.vtrainer.wear.sync.SyncResult
import com.vtrainer.wear.sync.SyncWorkoutRequest
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Represents a planned exercise for the watch workout session.
 */
data class WatchPlannedExercise(
    val exerciseId: String,
    val sets: Int,
    val reps: Int,
    val restSeconds: Int
)

/**
 * Represents a completed set in the watch workout session.
 */
data class WatchSetLog(
    val setNumber: Int,
    val reps: Int,
    val weight: Double,
    val restSeconds: Int,
    val heartRate: Int?,
    val completedAtMs: Long
)

/**
 * Sync status for the watch workout session.
 *
 * Validates: Requirement 5.6
 */
enum class WatchSyncStatus { IDLE, SYNCING, SYNCED, FAILED }

/**
 * UI state for the Watch Workout screen.
 *
 * Validates: Requirements 5.1, 5.2, 5.3, 5.4, 5.5, 5.6
 */
data class WatchWorkoutState(
    val exercises: List<WatchPlannedExercise> = emptyList(),
    val workoutPlanId: String? = null,
    val workoutDayName: String = "",
    val currentExerciseIndex: Int = 0,
    val currentSetIndex: Int = 0,
    val completedSets: List<WatchSetLog> = emptyList(),
    val isRestTimerActive: Boolean = false,
    val restTimerSecondsRemaining: Int = 0,
    val currentHeartRate: Int? = null,
    val currentCalories: Int = 0,
    val adjustedWeight: Double? = null,
    val adjustedReps: Int? = null,
    val isWorkoutComplete: Boolean = false,
    val syncStatus: WatchSyncStatus = WatchSyncStatus.IDLE,
    val workoutStartTimeMs: Long = 0L,
    val error: String? = null,
    /** True when haptic feedback should fire for set completion. Req 5.4 */
    val triggerSetCompletionHaptic: Boolean = false,
    /** True when haptic feedback should fire for timer expiration. Req 5.4 */
    val triggerTimerExpiredHaptic: Boolean = false
)

/**
 * ViewModel for the Watch Workout screen.
 *
 * Responsibilities:
 * - Manage exercise and set progression (Req 5.1, 5.2)
 * - Integrate HeartRateMonitor and CalorieTracker (Req 9.2, 10.2)
 * - Handle set completion with automatic rest timer (Req 5.3)
 * - Signal haptic feedback on set completion and timer expiration (Req 5.4)
 * - Allow weight and reps adjustment (Req 5.5)
 * - Save and sync TrainingLog via DirectSyncManager (Req 5.6)
 *
 * Validates: Requirements 5.1, 5.2, 5.3, 5.4, 5.5, 5.6
 */
class WatchWorkoutViewModel(
    private val heartRateMonitor: HeartRateMonitor,
    private val calorieTracker: CalorieTracker,
    private val syncManager: DirectSyncManager,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) : ViewModel() {

    private val _state = MutableStateFlow(WatchWorkoutState())
    val state: StateFlow<WatchWorkoutState> = _state.asStateFlow()

    private var restTimerJob: Job? = null
    private var heartRateJob: Job? = null
    private val heartRateReadings = mutableListOf<Int>()

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Starts a workout session with the given exercises.
     *
     * Validates: Requirements 5.1, 9.2, 10.1
     */
    fun startWorkout(
        exercises: List<WatchPlannedExercise>,
        workoutPlanId: String? = null,
        workoutDayName: String = ""
    ) {
        _state.value = WatchWorkoutState(
            exercises = exercises,
            workoutPlanId = workoutPlanId,
            workoutDayName = workoutDayName,
            workoutStartTimeMs = System.currentTimeMillis()
        )
        heartRateReadings.clear()
        startHeartRateMonitoring()
        viewModelScope.launch { calorieTracker.startTracking() }
    }

    /**
     * Records a completed set, starts the rest timer, and signals haptic feedback.
     *
     * Also pauses heart rate sensor processing during the rest period to reduce
     * battery drain (Req 9.2 - battery optimization).
     *
     * Validates: Requirements 5.2, 5.3, 5.4
     */
    fun completeSet(weight: Double, reps: Int) {
        val current = _state.value
        val exercise = current.exercises.getOrNull(current.currentExerciseIndex) ?: return

        val avgHeartRate = heartRateMonitor.calculateAverage(heartRateReadings.toList())
        heartRateReadings.clear()

        val setLog = WatchSetLog(
            setNumber = current.currentSetIndex + 1,
            reps = reps,
            weight = weight,
            restSeconds = exercise.restSeconds,
            heartRate = avgHeartRate,
            completedAtMs = System.currentTimeMillis()
        )

        _state.value = current.copy(
            completedSets = current.completedSets + setLog,
            isRestTimerActive = true,
            restTimerSecondsRemaining = exercise.restSeconds,
            adjustedWeight = null,
            adjustedReps = null,
            // Req 5.4 — signal haptic for set completion
            triggerSetCompletionHaptic = true
        )

        // Battery optimization: pause HR sensor processing during rest (Req 9.2)
        heartRateMonitor.setPollingActive(false)

        startRestTimer(exercise.restSeconds)
    }

    /** Clears the set completion haptic trigger after the UI has consumed it. */
    fun onSetCompletionHapticConsumed() {
        _state.value = _state.value.copy(triggerSetCompletionHaptic = false)
    }

    /** Clears the timer expired haptic trigger after the UI has consumed it. */
    fun onTimerExpiredHapticConsumed() {
        _state.value = _state.value.copy(triggerTimerExpiredHaptic = false)
    }

    /**
     * Adjusts the weight for the current set.
     *
     * Validates: Requirement 5.5
     */
    fun adjustWeight(weight: Double) {
        _state.value = _state.value.copy(adjustedWeight = weight)
    }

    /**
     * Adjusts the reps for the current set.
     *
     * Validates: Requirement 5.5
     */
    fun adjustReps(reps: Int) {
        _state.value = _state.value.copy(adjustedReps = reps)
    }

    /**
     * Skips the active rest timer and advances to the next set.
     *
     * Re-enables heart rate sensor processing for the upcoming active set.
     *
     * Validates: Requirement 5.3
     */
    fun skipRest() {
        restTimerJob?.cancel()
        _state.value = _state.value.copy(isRestTimerActive = false, restTimerSecondsRemaining = 0)
        // Battery optimization: re-enable HR polling for the next active set (Req 9.2)
        heartRateMonitor.setPollingActive(true)
        advanceToNextSet()
    }

    /**
     * Finalizes the workout, stops health tracking, and syncs via DirectSyncManager.
     *
     * Validates: Requirement 5.6
     */
    fun finishWorkout() {
        restTimerJob?.cancel()
        heartRateJob?.cancel()

        viewModelScope.launch {
            val totalCalories = calorieTracker.stopTracking()
            val current = _state.value
            val userId = auth.currentUser?.uid ?: return@launch

            _state.value = current.copy(
                isWorkoutComplete = true,
                syncStatus = WatchSyncStatus.SYNCING,
                currentCalories = totalCalories ?: 0
            )

            val request = buildSyncRequest(current, userId, totalCalories)
            val result = syncManager.syncWorkout(request)

            _state.value = _state.value.copy(
                syncStatus = when (result) {
                    is SyncResult.Success -> WatchSyncStatus.SYNCED
                    is SyncResult.Failure -> WatchSyncStatus.FAILED
                },
                error = (result as? SyncResult.Failure)?.error
            )
        }
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private fun startHeartRateMonitoring() {
        heartRateJob = viewModelScope.launch {
            heartRateMonitor.heartRateFlow().collect { result ->
                if (result is HeartRateResult.Reading) {
                    heartRateReadings.add(result.bpm)
                    _state.value = _state.value.copy(currentHeartRate = result.bpm)
                }
            }
        }
    }

    private fun startRestTimer(seconds: Int) {
        restTimerJob?.cancel()
        restTimerJob = viewModelScope.launch {
            var remaining = seconds
            while (remaining > 0) {
                delay(1_000L)
                remaining--
                _state.value = _state.value.copy(restTimerSecondsRemaining = remaining)
            }
            // Req 5.4 — signal haptic for timer expiration
            _state.value = _state.value.copy(
                isRestTimerActive = false,
                restTimerSecondsRemaining = 0,
                triggerTimerExpiredHaptic = true
            )
            // Battery optimization: re-enable HR polling for the next active set (Req 9.2)
            heartRateMonitor.setPollingActive(true)
            advanceToNextSet()
        }
    }

    private fun advanceToNextSet() {
        val current = _state.value
        val exercise = current.exercises.getOrNull(current.currentExerciseIndex) ?: return
        val nextSet = current.currentSetIndex + 1

        if (nextSet < exercise.sets) {
            _state.value = current.copy(currentSetIndex = nextSet)
            return
        }

        val nextExercise = current.currentExerciseIndex + 1
        if (nextExercise < current.exercises.size) {
            _state.value = current.copy(currentExerciseIndex = nextExercise, currentSetIndex = 0)
            return
        }

        _state.value = current.copy(isWorkoutComplete = true)
    }

    private fun buildSyncRequest(
        state: WatchWorkoutState,
        userId: String,
        totalCalories: Int?
    ): SyncWorkoutRequest {
        val durationSeconds = ((System.currentTimeMillis() - state.workoutStartTimeMs) / 1000).toInt()

        // Group completed sets by exercise index
        val exerciseLogs = buildExerciseLogs(state)

        return SyncWorkoutRequest(
            logId = UUID.randomUUID().toString(),
            userId = userId,
            workoutPlanId = state.workoutPlanId,
            workoutDayName = state.workoutDayName,
            timestamp = state.workoutStartTimeMs,
            origin = "watch",
            duration = durationSeconds,
            totalCalories = totalCalories,
            totalVolume = state.completedSets.sumOf { (it.weight * it.reps).toInt() },
            exercises = exerciseLogs
        )
    }

    private fun buildExerciseLogs(state: WatchWorkoutState): List<ExerciseLogRequest> {
        if (state.exercises.isEmpty() || state.completedSets.isEmpty()) return emptyList()

        val assignments = mutableListOf<Pair<Int, WatchSetLog>>()
        var exerciseIdx = 0
        var setIdx = 0

        for (setLog in state.completedSets) {
            if (exerciseIdx >= state.exercises.size) break
            assignments.add(exerciseIdx to setLog)
            setIdx++
            if (setIdx >= state.exercises[exerciseIdx].sets) {
                exerciseIdx++
                setIdx = 0
            }
        }

        return assignments
            .groupBy { it.first }
            .map { (exIdx, pairs) ->
                val exercise = state.exercises[exIdx]
                val sets = pairs.map { (_, s) ->
                    SetLogRequest(
                        setNumber = s.setNumber,
                        reps = s.reps,
                        weight = s.weight,
                        restSeconds = s.restSeconds,
                        heartRate = s.heartRate,
                        completedAt = s.completedAtMs
                    )
                }
                ExerciseLogRequest(
                    exerciseId = exercise.exerciseId,
                    totalVolume = sets.sumOf { (it.weight * it.reps).toInt() },
                    sets = sets
                )
            }
    }
}
