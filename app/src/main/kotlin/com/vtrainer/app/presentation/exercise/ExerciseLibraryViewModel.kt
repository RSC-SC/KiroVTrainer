package com.vtrainer.app.presentation.exercise

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vtrainer.app.data.repositories.ExerciseRepository
import com.vtrainer.app.domain.models.Exercise
import com.vtrainer.app.domain.models.MuscleGroup
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

/**
 * UI state for the Exercise Library screen.
 *
 * Validates: Requirements 2.1, 2.2, 2.3, 2.4
 */
data class ExerciseLibraryState(
    /** All exercises after applying search and muscle group filters. Requirement 2.1, 2.2 */
    val exercises: List<Exercise> = emptyList(),
    /** Current search query string. Requirement 2.2 */
    val searchQuery: String = "",
    /** Currently selected muscle group filter; null means show all. Requirement 2.2 */
    val selectedMuscleGroup: MuscleGroup? = null,
    /** Exercise selected for detail display. Requirement 2.3 */
    val selectedExercise: Exercise? = null,
    /** True while exercises are being loaded. */
    val isLoading: Boolean = true,
    /** Non-null error message when a load operation fails. */
    val error: String? = null
)

/**
 * ViewModel for the Exercise Library screen.
 *
 * Responsibilities:
 * - Load exercises from [ExerciseRepository] as a Flow (Req 2.1)
 * - Filter exercises by name search query, case-insensitive (Req 2.2)
 * - Filter exercises by muscle group; null = show all (Req 2.2)
 * - Expose selected exercise for detail display (Req 2.3)
 * - Expose [ExerciseLibraryState] as a [StateFlow]
 *
 * Validates: Requirements 2.1, 2.2, 2.3, 2.4
 */
class ExerciseLibraryViewModel(
    private val exerciseRepository: ExerciseRepository
) : ViewModel() {

    private val _state = MutableStateFlow(ExerciseLibraryState())
    val state: StateFlow<ExerciseLibraryState> = _state.asStateFlow()

    /** Full list of exercises from the repository, before client-side filtering. */
    private var allExercises: List<Exercise> = emptyList()

    init {
        loadExercises()
    }

    /**
     * Starts collecting exercises from the repository and applies current filters reactively.
     *
     * Validates: Requirement 2.1
     */
    private fun loadExercises() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            exerciseRepository.getExercises()
                .catch { e ->
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to load exercises"
                    )
                }
                .collect { exercises ->
                    allExercises = exercises
                    applyFilters()
                }
        }
    }

    /**
     * Updates the search query and re-applies filters.
     * Filtering is case-insensitive and matches exercise names.
     *
     * Validates: Requirement 2.2
     *
     * @param query The search string entered by the user.
     */
    fun search(query: String) {
        _state.value = _state.value.copy(searchQuery = query)
        applyFilters()
    }

    /**
     * Updates the muscle group filter and re-applies filters.
     * Pass null to show all muscle groups.
     *
     * Validates: Requirement 2.2
     *
     * @param muscleGroup The muscle group to filter by, or null for no filter.
     */
    fun filterByMuscleGroup(muscleGroup: MuscleGroup?) {
        _state.value = _state.value.copy(selectedMuscleGroup = muscleGroup)
        applyFilters()
    }

    /**
     * Sets the exercise to display in the detail view.
     * Pass null to dismiss the detail view.
     *
     * Validates: Requirement 2.3
     *
     * @param exercise The exercise to show details for, or null to clear selection.
     */
    fun selectExercise(exercise: Exercise?) {
        _state.value = _state.value.copy(selectedExercise = exercise)
    }

    /**
     * Applies the current [searchQuery] and [selectedMuscleGroup] filters to [allExercises]
     * and updates the state with the filtered result.
     *
     * Validates: Requirements 2.2, 2.4
     */
    private fun applyFilters() {
        val query = _state.value.searchQuery.trim()
        val muscleGroup = _state.value.selectedMuscleGroup

        val filtered = allExercises
            .filter { exercise ->
                query.isEmpty() || exercise.name.contains(query, ignoreCase = true)
            }
            .filter { exercise ->
                muscleGroup == null ||
                    exercise.muscleGroup == muscleGroup ||
                    exercise.secondaryMuscles.contains(muscleGroup)
            }

        _state.value = _state.value.copy(
            exercises = filtered,
            isLoading = false
        )
    }
}
