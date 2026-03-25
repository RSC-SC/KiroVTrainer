package com.vtrainer.app.presentation.plan

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.vtrainer.app.data.repositories.WorkoutRepository
import com.vtrainer.app.domain.models.PlannedExercise
import com.vtrainer.app.domain.models.TrainingDay
import com.vtrainer.app.domain.models.WorkoutPlan
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.Instant
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * UI tests for [WorkoutPlanEditorScreen].
 *
 * Validates: Requirements 3.2, 3.4
 */
class WorkoutPlanEditorScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    private fun fakeRepo() = object : WorkoutRepository {
        override fun getWorkoutPlans(): Flow<List<WorkoutPlan>> = flowOf(emptyList())
        override suspend fun saveWorkoutPlan(plan: WorkoutPlan): Result<Unit> = Result.success(Unit)
        override suspend fun deleteWorkoutPlan(planId: String): Result<Unit> = Result.success(Unit)
    }

    private fun fakeViewModel(initialState: WorkoutPlanState): WorkoutPlanViewModel {
        val flow = MutableStateFlow(initialState)
        return object : WorkoutPlanViewModel(workoutRepository = fakeRepo()) {
            override val state: StateFlow<WorkoutPlanState> = flow.asStateFlow()
        }
    }

    private fun blankPlan() = WorkoutPlan(
        planId = "plan-new",
        userId = "user-1",
        name = "",
        description = null,
        trainingDays = emptyList(),
        createdAt = Instant.fromEpochMilliseconds(0),
        updatedAt = Instant.fromEpochMilliseconds(0)
    )

    private fun planWithDay() = WorkoutPlan(
        planId = "plan-1",
        userId = "user-1",
        name = "Treino A",
        description = null,
        trainingDays = listOf(
            TrainingDay(
                dayName = "Segunda-feira",
                exercises = listOf(
                    PlannedExercise(
                        exerciseId = "Supino Reto",
                        order = 0,
                        sets = 3,
                        reps = 10,
                        restSeconds = 60,
                        notes = null
                    )
                )
            )
        ),
        createdAt = Instant.fromEpochMilliseconds(0),
        updatedAt = Instant.fromEpochMilliseconds(0)
    )

    // ---------------------------------------------------------------------------
    // Test 1: Plan name field is displayed (Req 3.1)
    // ---------------------------------------------------------------------------

    @Test
    fun planNameField_isDisplayed() {
        val state = WorkoutPlanState(editingPlan = blankPlan())

        composeTestRule.setContent {
            WorkoutPlanEditorScreen(viewModel = fakeViewModel(state))
        }

        composeTestRule.onNodeWithText("Nome do Plano").assertIsDisplayed()
    }

    // ---------------------------------------------------------------------------
    // Test 2: Training days section header is displayed (Req 3.2)
    // ---------------------------------------------------------------------------

    @Test
    fun trainingDaysSection_isDisplayed() {
        val state = WorkoutPlanState(editingPlan = blankPlan())

        composeTestRule.setContent {
            WorkoutPlanEditorScreen(viewModel = fakeViewModel(state))
        }

        composeTestRule.onNodeWithText("Dias de Treino").assertIsDisplayed()
        composeTestRule.onNodeWithText("Adicionar Dia").assertIsDisplayed()
    }

    // ---------------------------------------------------------------------------
    // Test 3: Existing training day and exercise are displayed (Req 3.2)
    // ---------------------------------------------------------------------------

    @Test
    fun existingDayAndExercise_areDisplayed() {
        val state = WorkoutPlanState(editingPlan = planWithDay())

        composeTestRule.setContent {
            WorkoutPlanEditorScreen(viewModel = fakeViewModel(state))
        }

        composeTestRule.onNodeWithText("Segunda-feira").assertIsDisplayed()
        composeTestRule.onNodeWithText("Supino Reto").assertIsDisplayed()
    }

    // ---------------------------------------------------------------------------
    // Test 4: Save button is disabled when plan name is blank (Req 3.4)
    // ---------------------------------------------------------------------------

    @Test
    fun saveButton_isDisabled_whenPlanNameIsBlank() {
        val state = WorkoutPlanState(editingPlan = blankPlan())

        composeTestRule.setContent {
            WorkoutPlanEditorScreen(viewModel = fakeViewModel(state))
        }

        composeTestRule.onNodeWithText("Salvar Plano").assertIsNotEnabled()
    }

    // ---------------------------------------------------------------------------
    // Test 5: Save button is enabled when plan name is non-blank (Req 3.4)
    // ---------------------------------------------------------------------------

    @Test
    fun saveButton_isEnabled_whenPlanNameIsNonBlank() {
        val state = WorkoutPlanState(editingPlan = planWithDay())

        composeTestRule.setContent {
            WorkoutPlanEditorScreen(viewModel = fakeViewModel(state))
        }

        composeTestRule.onNodeWithText("Salvar Plano").assertIsEnabled()
    }

    // ---------------------------------------------------------------------------
    // Test 6: Cancel button triggers onNavigateBack callback
    // ---------------------------------------------------------------------------

    @Test
    fun cancelButton_triggersNavigateBack() {
        val state = WorkoutPlanState(editingPlan = blankPlan())
        var navigatedBack = false

        composeTestRule.setContent {
            WorkoutPlanEditorScreen(
                viewModel = fakeViewModel(state),
                onNavigateBack = { navigatedBack = true }
            )
        }

        composeTestRule.onNodeWithText("Cancelar").performClick()

        assertTrue("onNavigateBack should have been called", navigatedBack)
    }

    // ---------------------------------------------------------------------------
    // Test 7: Exercise config fields (Séries, Reps, Descanso) are displayed (Req 3.3)
    // ---------------------------------------------------------------------------

    @Test
    fun exerciseConfigFields_areDisplayed_whenExerciseExists() {
        val state = WorkoutPlanState(editingPlan = planWithDay())

        composeTestRule.setContent {
            WorkoutPlanEditorScreen(viewModel = fakeViewModel(state))
        }

        composeTestRule.onNodeWithText("Séries").assertIsDisplayed()
        composeTestRule.onNodeWithText("Reps").assertIsDisplayed()
        composeTestRule.onNodeWithText("Descanso (s)").assertIsDisplayed()
    }
}
