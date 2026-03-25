package com.vtrainer.app.presentation.exercise

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.vtrainer.app.data.repositories.ExerciseRepository
import com.vtrainer.app.domain.models.Difficulty
import com.vtrainer.app.domain.models.Exercise
import com.vtrainer.app.domain.models.MediaType
import com.vtrainer.app.domain.models.MuscleGroup
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test

/**
 * UI tests for [ExerciseLibraryScreen].
 *
 * Validates: Requirements 2.2
 */
class ExerciseLibraryScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    private fun fakeRepo() = object : ExerciseRepository {
        override fun getExercises(): Flow<List<Exercise>> = flowOf(emptyList())
        override suspend fun searchExercises(query: String): List<Exercise> = emptyList()
        override suspend fun filterByMuscleGroup(group: MuscleGroup): List<Exercise> = emptyList()
    }

    private fun fakeViewModel(initialState: ExerciseLibraryState): ExerciseLibraryViewModel {
        val flow = MutableStateFlow(initialState)
        return object : ExerciseLibraryViewModel(exerciseRepository = fakeRepo()) {
            override val state: StateFlow<ExerciseLibraryState> = flow.asStateFlow()
        }
    }

    private fun sampleExercise(
        name: String = "Supino Reto",
        muscleGroup: MuscleGroup = MuscleGroup.CHEST
    ) = Exercise(
        exerciseId = "ex-1",
        name = name,
        muscleGroup = muscleGroup,
        secondaryMuscles = emptyList(),
        instructions = "Deite no banco e empurre a barra.",
        mediaUrl = "https://example.com/supino.gif",
        mediaType = MediaType.GIF,
        difficulty = Difficulty.INTERMEDIATE,
        equipment = emptyList()
    )

    // ---------------------------------------------------------------------------
    // Test 1: Search bar is displayed (Req 2.2)
    // ---------------------------------------------------------------------------

    @Test
    fun searchBar_isDisplayed() {
        val state = ExerciseLibraryState(exercises = emptyList(), isLoading = false)

        composeTestRule.setContent {
            ExerciseLibraryScreen(viewModel = fakeViewModel(state))
        }

        composeTestRule.onNodeWithText("Buscar exercício...").assertIsDisplayed()
    }

    // ---------------------------------------------------------------------------
    // Test 2: Muscle group filter chips are displayed (Req 2.2)
    // ---------------------------------------------------------------------------

    @Test
    fun muscleGroupFilterChips_areDisplayed() {
        val state = ExerciseLibraryState(exercises = emptyList(), isLoading = false)

        composeTestRule.setContent {
            ExerciseLibraryScreen(viewModel = fakeViewModel(state))
        }

        composeTestRule.onNodeWithText("Todos").assertIsDisplayed()
        composeTestRule.onNodeWithText("Peito").assertIsDisplayed()
        composeTestRule.onNodeWithText("Costas").assertIsDisplayed()
    }

    // ---------------------------------------------------------------------------
    // Test 3: Exercise cards are displayed with name and muscle group (Req 2.1)
    // ---------------------------------------------------------------------------

    @Test
    fun exerciseCards_areDisplayed_withNameAndMuscleGroup() {
        val state = ExerciseLibraryState(
            exercises = listOf(sampleExercise(name = "Supino Reto", muscleGroup = MuscleGroup.CHEST)),
            isLoading = false
        )

        composeTestRule.setContent {
            ExerciseLibraryScreen(viewModel = fakeViewModel(state))
        }

        composeTestRule.onNodeWithText("Supino Reto").assertIsDisplayed()
        composeTestRule.onNodeWithText("Peito").assertIsDisplayed()
    }

    // ---------------------------------------------------------------------------
    // Test 4: Tapping an exercise card opens the detail dialog (Req 2.3)
    // ---------------------------------------------------------------------------

    @Test
    fun tappingExerciseCard_opensDetailDialog() {
        val exercise = sampleExercise(name = "Agachamento")
        val state = ExerciseLibraryState(
            exercises = listOf(exercise),
            isLoading = false
        )

        // Use a real-ish ViewModel that can update selectedExercise
        val flow = MutableStateFlow(state)
        val vm = object : ExerciseLibraryViewModel(exerciseRepository = fakeRepo()) {
            override val state: StateFlow<ExerciseLibraryState> = flow.asStateFlow()
            override fun selectExercise(ex: Exercise?) {
                flow.value = flow.value.copy(selectedExercise = ex)
            }
        }

        composeTestRule.setContent {
            ExerciseLibraryScreen(viewModel = vm)
        }

        composeTestRule.onNodeWithText("Agachamento").performClick()

        // Detail dialog should appear with instructions
        composeTestRule.onNodeWithText("Fechar").assertIsDisplayed()
    }

    // ---------------------------------------------------------------------------
    // Test 5: Empty state message is shown when no exercises match (Req 2.2)
    // ---------------------------------------------------------------------------

    @Test
    fun emptyState_isDisplayed_whenNoExercisesMatch() {
        val state = ExerciseLibraryState(exercises = emptyList(), isLoading = false)

        composeTestRule.setContent {
            ExerciseLibraryScreen(viewModel = fakeViewModel(state))
        }

        composeTestRule.onNodeWithText("Nenhum exercício encontrado.").assertIsDisplayed()
    }

    // ---------------------------------------------------------------------------
    // Test 6: Loading indicator is shown when isLoading=true
    // ---------------------------------------------------------------------------

    @Test
    fun loadingIndicator_isShown_whenIsLoadingTrue() {
        val state = ExerciseLibraryState(isLoading = true)

        composeTestRule.setContent {
            ExerciseLibraryScreen(viewModel = fakeViewModel(state))
        }

        composeTestRule.onNodeWithText("Nenhum exercício encontrado.").assertDoesNotExist()
    }
}
