package com.vtrainer.app.presentation.plan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.vtrainer.app.data.repositories.WorkoutRepository
import com.vtrainer.app.domain.models.PlannedExercise
import com.vtrainer.app.domain.models.TrainingDay
import com.vtrainer.app.domain.models.WorkoutPlan
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import java.util.UUID

/**
 * UI state for the Workout Plan creation and editing screen.
 *
 * Validates: Requirements 3.1, 3.2, 3.3, 3.4, 3.5, 3.6
 */
data class WorkoutPlanState(
    /** All workout plans belonging to the current user. Requirement 3.5 */
    val plans: List<WorkoutPlan> = emptyList(),
    /** The plan currently being created or edited; null when not editing. Requirements 3.1, 3.5 */
    val editingPlan: WorkoutPlan? = null,
    /** True while plans are being loaded from the repository. */
    val isLoading: Boolean = true,
    /** True while a save operation is in progress. Requirement 3.4 */
    val isSaving: Boolean = false,
    /** Non-null error message when an operation fails. */
    val error: String? = null,
    /** True after a plan has been successfully saved. Requirement 3.4 */
    val saveSuccess: Boolean = false
)

/**
 * ViewModel for the Workout Plan Editor screen.
 *
 * Responsibilities:
 * - Load all workout plans from [WorkoutRepository]
 * - Handle plan creation with a blank [WorkoutPlan] (Req 3.1)
 * - Handle plan editing by setting [WorkoutPlanState.editingPlan] (Req 3.5)
 * - Allow adding/removing training days and exercises (Req 3.2)
 * - Allow configuring sets, reps, and rest time per exercise (Req 3.3)
 * - Save plans to the repository (Req 3.4)
 * - Delete plans from the repository (Req 3.6)
 *
 * Uses constructor injection (no Hilt), following the same pattern as DashboardViewModel.
 *
 * Validates: Requirements 3.1, 3.2, 3.3, 3.4, 3.5, 3.6
 */
class WorkoutPlanViewModel(
    private val workoutRepository: WorkoutRepository,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) : ViewModel() {

    private val _state = MutableStateFlow(WorkoutPlanState())
    val state: StateFlow<WorkoutPlanState> = _state.asStateFlow()

    init {
        loadPlans()
    }

    /**
     * Loads all workout plans for the current user from [WorkoutRepository] as a Flow.
     * Emits updates reactively whenever the underlying data changes.
     *
     * Validates: Requirements 3.5
     */
    fun loadPlans() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            workoutRepository.getWorkoutPlans()
                .catch { e ->
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to load workout plans"
                    )
                }
                .collect { plans ->
                    _state.value = _state.value.copy(
                        plans = plans,
                        isLoading = false
                    )
                }
        }
    }

    /**
     * Initializes a blank [WorkoutPlan] for creation.
     * Sets [WorkoutPlanState.editingPlan] to a new empty plan owned by the current user.
     *
     * Validates: Requirement 3.1
     */
    fun startNewPlan() {
        val userId = auth.currentUser?.uid ?: ""
        val now = Clock.System.now()
        _state.value = _state.value.copy(
            editingPlan = WorkoutPlan(
                planId = UUID.randomUUID().toString(),
                userId = userId,
                name = "",
                description = null,
                trainingDays = emptyList(),
                createdAt = now,
                updatedAt = now
            ),
            saveSuccess = false,
            error = null
        )
    }

    /**
     * Sets [WorkoutPlanState.editingPlan] to the given plan for editing.
     *
     * Validates: Requirement 3.5
     *
     * @param plan The plan to edit.
     */
    fun editPlan(plan: WorkoutPlan) {
        _state.value = _state.value.copy(
            editingPlan = plan,
            saveSuccess = false,
            error = null
        )
    }

    /**
     * Updates the name of the plan currently being edited.
     *
     * Validates: Requirement 3.1
     *
     * @param name The new plan name.
     */
    fun updatePlanName(name: String) {
        val editing = _state.value.editingPlan ?: return
        _state.value = _state.value.copy(
            editingPlan = editing.copy(name = name, updatedAt = Clock.System.now())
        )
    }

    /**
     * Updates the description of the plan currently being edited.
     *
     * @param description The new description, or null to clear it.
     */
    fun updatePlanDescription(description: String?) {
        val editing = _state.value.editingPlan ?: return
        _state.value = _state.value.copy(
            editingPlan = editing.copy(description = description, updatedAt = Clock.System.now())
        )
    }

    /**
     * Adds a new [TrainingDay] with the given name to the plan being edited.
     *
     * Validates: Requirement 3.2
     *
     * @param dayName The name of the new training day.
     */
    fun addTrainingDay(dayName: String) {
        val editing = _state.value.editingPlan ?: return
        val updatedDays = editing.trainingDays + TrainingDay(dayName = dayName, exercises = emptyList())
        _state.value = _state.value.copy(
            editingPlan = editing.copy(trainingDays = updatedDays, updatedAt = Clock.System.now())
        )
    }

    /**
     * Removes the [TrainingDay] at [dayIndex] from the plan being edited.
     *
     * @param dayIndex Zero-based index of the day to remove.
     */
    fun removeTrainingDay(dayIndex: Int) {
        val editing = _state.value.editingPlan ?: return
        if (dayIndex !in editing.trainingDays.indices) return
        val updatedDays = editing.trainingDays.toMutableList().also { it.removeAt(dayIndex) }
        _state.value = _state.value.copy(
            editingPlan = editing.copy(trainingDays = updatedDays, updatedAt = Clock.System.now())
        )
    }

    /**
     * Adds a [PlannedExercise] to the training day at [dayIndex].
     *
     * Validates: Requirement 3.2
     *
     * @param dayIndex Zero-based index of the target training day.
     * @param exercise The exercise to add.
     */
    fun addExercise(dayIndex: Int, exercise: PlannedExercise) {
        val editing = _state.value.editingPlan ?: return
        if (dayIndex !in editing.trainingDays.indices) return
        val updatedDays = editing.trainingDays.mapIndexed { index, day ->
            if (index == dayIndex) day.copy(exercises = day.exercises + exercise) else day
        }
        _state.value = _state.value.copy(
            editingPlan = editing.copy(trainingDays = updatedDays, updatedAt = Clock.System.now())
        )
    }

    /**
     * Removes the exercise at [exerciseIndex] from the training day at [dayIndex].
     *
     * @param dayIndex Zero-based index of the training day.
     * @param exerciseIndex Zero-based index of the exercise within the day.
     */
    fun removeExercise(dayIndex: Int, exerciseIndex: Int) {
        val editing = _state.value.editingPlan ?: return
        if (dayIndex !in editing.trainingDays.indices) return
        val day = editing.trainingDays[dayIndex]
        if (exerciseIndex !in day.exercises.indices) return
        val updatedExercises = day.exercises.toMutableList().also { it.removeAt(exerciseIndex) }
        val updatedDays = editing.trainingDays.mapIndexed { index, d ->
            if (index == dayIndex) d.copy(exercises = updatedExercises) else d
        }
        _state.value = _state.value.copy(
            editingPlan = editing.copy(trainingDays = updatedDays, updatedAt = Clock.System.now())
        )
    }

    /**
     * Updates the sets, reps, and rest time for the exercise at [exerciseIndex]
     * within the training day at [dayIndex].
     *
     * Validates: Requirement 3.3
     *
     * @param dayIndex Zero-based index of the training day.
     * @param exerciseIndex Zero-based index of the exercise within the day.
     * @param sets Number of sets.
     * @param reps Number of reps per set.
     * @param restSeconds Rest time in seconds between sets.
     */
    fun updateExerciseConfig(dayIndex: Int, exerciseIndex: Int, sets: Int, reps: Int, restSeconds: Int) {
        val editing = _state.value.editingPlan ?: return
        if (dayIndex !in editing.trainingDays.indices) return
        val day = editing.trainingDays[dayIndex]
        if (exerciseIndex !in day.exercises.indices) return
        val updatedExercises = day.exercises.mapIndexed { index, exercise ->
            if (index == exerciseIndex) exercise.copy(sets = sets, reps = reps, restSeconds = restSeconds)
            else exercise
        }
        val updatedDays = editing.trainingDays.mapIndexed { index, d ->
            if (index == dayIndex) d.copy(exercises = updatedExercises) else d
        }
        _state.value = _state.value.copy(
            editingPlan = editing.copy(trainingDays = updatedDays, updatedAt = Clock.System.now())
        )
    }

    /**
     * Saves the plan currently being edited via [WorkoutRepository].
     * Sets [WorkoutPlanState.isSaving] during the operation and
     * [WorkoutPlanState.saveSuccess] on success.
     *
     * Validates: Requirement 3.4
     */
    fun savePlan() {
        val editing = _state.value.editingPlan ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(isSaving = true, error = null, saveSuccess = false)
            val result = workoutRepository.saveWorkoutPlan(editing)
            _state.value = if (result.isSuccess) {
                _state.value.copy(isSaving = false, saveSuccess = true, editingPlan = null)
            } else {
                _state.value.copy(
                    isSaving = false,
                    error = result.exceptionOrNull()?.message ?: "Failed to save workout plan"
                )
            }
        }
    }

    /**
     * Deletes the plan with the given [planId] via [WorkoutRepository].
     *
     * Validates: Requirement 3.6
     *
     * @param planId The ID of the plan to delete.
     */
    fun deletePlan(planId: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(error = null)
            val result = workoutRepository.deleteWorkoutPlan(planId)
            if (result.isFailure) {
                _state.value = _state.value.copy(
                    error = result.exceptionOrNull()?.message ?: "Failed to delete workout plan"
                )
            }
        }
    }

    /**
     * Clears [WorkoutPlanState.editingPlan], discarding any unsaved changes.
     */
    fun cancelEdit() {
        _state.value = _state.value.copy(editingPlan = null, error = null, saveSuccess = false)
    }

    /**
     * Parses a JSON string and sets it as the plan currently being edited.
     * On failure, sets [WorkoutPlanState.error] with a descriptive message.
     *
     * Validates: Requirements 19.1, 19.2, 19.6
     *
     * @param json The JSON string to import.
     */
    fun importPlanFromJson(json: String) {
        val result = com.vtrainer.app.data.serialization.WorkoutPlanSerializer.importFromJson(json)
        _state.value = if (result.isSuccess) {
            _state.value.copy(editingPlan = result.getOrThrow(), error = null, saveSuccess = false)
        } else {
            _state.value.copy(error = result.exceptionOrNull()?.message ?: "Failed to import workout plan")
        }
    }
}
