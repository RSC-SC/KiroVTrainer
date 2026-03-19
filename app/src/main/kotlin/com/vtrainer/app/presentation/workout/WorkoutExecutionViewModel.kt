package com.vtrainer.app.presentation.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.vtrainer.app.data.repositories.TrainingLogRepository
import com.vtrainer.app.domain.models.ExerciseLog
import com.vtrainer.app.domain.models.SetLog
import com.vtrainer.app.domain.models.TrainingLog
import com.vtrainer.app.domain.models.WorkoutPlan
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import java.util.UUID

/**
 * UI state for the Workout Execution screen.
 *
 * Validates: Requirements 4.1, 4.2, 4.3, 4.5, 4.6
 */
data class WorkoutExecutionState(
    /** The active workout plan being executed. Requirement 4.1 */
    val currentPlan: WorkoutPlan? = null,
    /** Index of the current training day within the plan. Requirement 4.1 */
    val currentDayIndex: Int = 0,
    /** Index of the current exercise within the training day. Requirement 4.1 */
    val currentExerciseIndex: Int = 0,
    /** Index of the current set within the current exercise. Requirement 4.2 */
    val currentSetIndex: Int = 0,
    /** All sets completed so far in this session. Requirement 4.6 */
    val completedSets: List<SetLog> = emptyList(),
    /** True while the rest timer is counting down. Requirement 4.3 */
    val isRestTimerActive: Boolean = false,
    /** Remaining seconds on the rest timer. Requirement 4.3 */
    val restTimerSecondsRemaining: Int = 0,
    /** Instant when the workout session started. Used to calculate duration. */
    val workoutStartTime: Instant? = null,
    /** True when all exercises and sets have been completed. Requirement 4.6 */
    val isWorkoutComplete: Boolean = false,
    /** True while an async operation (e.g. saving) is in progress. */
    val isLoading: Boolean = false,
    /** Non-null error message when an operation fails. */
    val error: String? = null,
    /** Weight override for the current set (null = use plan default). Requirement 4.5 */
    val adjustedWeight: Double? = null,
    /** Reps override for the current set (null = use plan default). Requirement 4.5 */
    val adjustedReps: Int? = null
)

/**
 * ViewModel for the Workout Execution screen.
 *
 * Responsibilities:
 * - Initialize a workout session from a [WorkoutPlan] and day index
 * - Track progression through exercises and sets
 * - Automatically start a rest timer after each completed set (Requirement 4.3)
 * - Allow in-session weight and reps adjustments (Requirement 4.5)
 * - Build and persist a [TrainingLog] when the workout finishes (Requirement 4.6)
 *
 * Validates: Requirements 4.1, 4.2, 4.3, 4.5, 4.6
 */
class WorkoutExecutionViewModel(
    private val trainingLogRepository: TrainingLogRepository,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) : ViewModel() {

    private val _state = MutableStateFlow(WorkoutExecutionState())
    val state: StateFlow<WorkoutExecutionState> = _state.asStateFlow()

    private var restTimerJob: Job? = null

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Initializes a new workout session.
     *
     * Requirement 4.1 – display the current exercise with configured sets, reps, and weight.
     *
     * @param plan     The workout plan to execute.
     * @param dayIndex The index of the training day within [plan].
     */
    fun startWorkout(plan: WorkoutPlan, dayIndex: Int) {
        _state.value = WorkoutExecutionState(
            currentPlan = plan,
            currentDayIndex = dayIndex,
            currentExerciseIndex = 0,
            currentSetIndex = 0,
            completedSets = emptyList(),
            workoutStartTime = Clock.System.now(),
            isLoading = false,
            error = null
        )
    }

    /**
     * Records a completed set and automatically starts the rest timer.
     *
     * Requirement 4.2 – mark sets as complete.
     * Requirement 4.3 – automatically start Rest_Timer after set completion.
     *
     * @param weight Actual weight used for this set (may differ from plan default).
     * @param reps   Actual reps performed for this set.
     */
    fun completeSet(weight: Double, reps: Int) {
        val current = _state.value
        val plan = current.currentPlan ?: return
        val day = plan.trainingDays.getOrNull(current.currentDayIndex) ?: return
        val exercise = day.exercises.getOrNull(current.currentExerciseIndex) ?: return

        val setLog = SetLog(
            setNumber = current.currentSetIndex + 1,
            reps = reps,
            weight = weight,
            restSeconds = exercise.restSeconds,
            heartRate = null,
            completedAt = Clock.System.now()
        )

        val updatedSets = current.completedSets + setLog

        _state.value = current.copy(
            completedSets = updatedSets,
            isRestTimerActive = true,
            restTimerSecondsRemaining = exercise.restSeconds,
            adjustedWeight = null,
            adjustedReps = null
        )

        startRestTimer(exercise.restSeconds)
    }

    /**
     * Adjusts the weight for the current set during the session.
     *
     * Requirement 4.5 – allow weight adjustment during training session.
     *
     * @param weight New weight value.
     */
    fun adjustWeight(weight: Double) {
        _state.value = _state.value.copy(adjustedWeight = weight)
    }

    /**
     * Adjusts the reps for the current set during the session.
     *
     * Requirement 4.5 – allow reps adjustment during training session.
     *
     * @param reps New reps value.
     */
    fun adjustReps(reps: Int) {
        _state.value = _state.value.copy(adjustedReps = reps)
    }

    /**
     * Skips the active rest timer and immediately advances to the next set or exercise.
     *
     * Requirement 4.3 – rest timer can be skipped by the user.
     */
    fun skipRest() {
        restTimerJob?.cancel()
        restTimerJob = null
        _state.value = _state.value.copy(
            isRestTimerActive = false,
            restTimerSecondsRemaining = 0
        )
        advanceToNextSet()
    }

    /**
     * Finalizes the workout session: builds a [TrainingLog] from all completed sets
     * and persists it via [TrainingLogRepository].
     *
     * Requirement 4.6 – save Training_Log to Firestore when session completes.
     */
    fun finishWorkout() {
        val current = _state.value
        val plan = current.currentPlan ?: return
        val userId = auth.currentUser?.uid ?: return

        restTimerJob?.cancel()
        restTimerJob = null

        _state.value = current.copy(isLoading = true, error = null)

        viewModelScope.launch {
            val endTime = Clock.System.now()
            val startTime = current.workoutStartTime ?: endTime
            val durationSeconds = ((endTime - startTime).inWholeSeconds).toInt()

            val trainingLog = buildTrainingLog(
                plan = plan,
                dayIndex = current.currentDayIndex,
                userId = userId,
                completedSets = current.completedSets,
                durationSeconds = durationSeconds,
                timestamp = startTime
            )

            val result = trainingLogRepository.saveTrainingLog(trainingLog)

            _state.value = if (result.isSuccess) {
                _state.value.copy(
                    isLoading = false,
                    isWorkoutComplete = true
                )
            } else {
                _state.value.copy(
                    isLoading = false,
                    error = result.exceptionOrNull()?.message ?: "Failed to save workout"
                )
            }
        }
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    /**
     * Starts a coroutine-based countdown timer.
     * When the timer reaches zero it automatically advances to the next set/exercise.
     *
     * Requirement 4.3 – Rest_Timer counts down and auto-advances on expiry.
     */
    private fun startRestTimer(seconds: Int) {
        restTimerJob?.cancel()
        restTimerJob = viewModelScope.launch {
            var remaining = seconds
            while (remaining > 0) {
                delay(1_000L)
                remaining--
                _state.value = _state.value.copy(restTimerSecondsRemaining = remaining)
            }
            _state.value = _state.value.copy(
                isRestTimerActive = false,
                restTimerSecondsRemaining = 0
            )
            advanceToNextSet()
        }
    }

    /**
     * Advances the session to the next set, or to the next exercise when all sets
     * for the current exercise are done. Marks the workout complete when all
     * exercises are finished.
     */
    private fun advanceToNextSet() {
        val current = _state.value
        val plan = current.currentPlan ?: return
        val day = plan.trainingDays.getOrNull(current.currentDayIndex) ?: return
        val exercise = day.exercises.getOrNull(current.currentExerciseIndex) ?: return

        val nextSetIndex = current.currentSetIndex + 1

        if (nextSetIndex < exercise.sets) {
            // More sets remaining for the current exercise
            _state.value = current.copy(currentSetIndex = nextSetIndex)
            return
        }

        // All sets for this exercise done – move to next exercise
        val nextExerciseIndex = current.currentExerciseIndex + 1

        if (nextExerciseIndex < day.exercises.size) {
            _state.value = current.copy(
                currentExerciseIndex = nextExerciseIndex,
                currentSetIndex = 0
            )
            return
        }

        // All exercises done – workout is complete
        _state.value = current.copy(isWorkoutComplete = true)
    }

    /**
     * Builds a [TrainingLog] from the completed sets, grouping them by exercise.
     */
    private fun buildTrainingLog(
        plan: WorkoutPlan,
        dayIndex: Int,
        userId: String,
        completedSets: List<SetLog>,
        durationSeconds: Int,
        timestamp: Instant
    ): TrainingLog {
        val day = plan.trainingDays.getOrNull(dayIndex)
        val exercises = day?.exercises ?: emptyList()

        // Group completed sets by their position in the exercise list.
        // SetLog.setNumber is 1-based; we track which exercise each set belongs to
        // by replaying the same progression logic used during the session.
        val exerciseLogs = buildExerciseLogs(exercises, completedSets)

        val totalVolume = completedSets.sumOf { (it.weight * it.reps).toInt() }

        return TrainingLog(
            logId = UUID.randomUUID().toString(),
            userId = userId,
            workoutPlanId = plan.planId,
            workoutDayName = day?.dayName ?: "",
            timestamp = timestamp,
            origin = "mobile",
            duration = durationSeconds,
            totalCalories = null,
            exercises = exerciseLogs,
            totalVolume = totalVolume
        )
    }

    /**
     * Groups [completedSets] into [ExerciseLog] entries by replaying the same
     * set-progression logic used during the session.
     */
    private fun buildExerciseLogs(
        exercises: List<com.vtrainer.app.domain.models.PlannedExercise>,
        completedSets: List<SetLog>
    ): List<ExerciseLog> {
        if (exercises.isEmpty() || completedSets.isEmpty()) return emptyList()

        // Assign each completed set to an exercise by simulating progression
        data class Assignment(val exerciseIndex: Int, val set: SetLog)

        val assignments = mutableListOf<Assignment>()
        var exerciseIdx = 0
        var setIdx = 0

        for (setLog in completedSets) {
            if (exerciseIdx >= exercises.size) break
            assignments.add(Assignment(exerciseIdx, setLog))
            setIdx++
            if (setIdx >= exercises[exerciseIdx].sets) {
                exerciseIdx++
                setIdx = 0
            }
        }

        return assignments
            .groupBy { it.exerciseIndex }
            .map { (exIdx, assigned) ->
                val exercise = exercises[exIdx]
                val sets = assigned.map { it.set }
                val volume = sets.sumOf { (it.weight * it.reps).toInt() }
                ExerciseLog(
                    exerciseId = exercise.exerciseId,
                    sets = sets,
                    totalVolume = volume
                )
            }
    }
}
