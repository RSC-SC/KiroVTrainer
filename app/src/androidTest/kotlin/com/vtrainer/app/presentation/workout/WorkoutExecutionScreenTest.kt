package com.vtrainer.app.presentation.workout

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.vtrainer.app.data.repositories.TrainingLogRepository
import com.vtrainer.app.domain.models.PlannedExercise
import com.vtrainer.app.domain.models.TrainingDay
import com.vtrainer.app.domain.models.TrainingLog
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
 * UI tests for [WorkoutExecutionScreen].
 *
 * Validates: Requirements 4.2, 4.3, 4.5
 */
class WorkoutExecutionScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    private fun samplePlan(sets: Int = 3, restSeconds: Int = 60) = WorkoutPlan(
        planId = "plan-1",
        userId = "user-1",
        name = "Treino A",
        description = null,
        trainingDays = listOf(
            TrainingDay(
                dayName = "Dia 1",
                exercises = listOf(
                    PlannedExercise(
                        exerciseId = "Supino Reto",
                        order = 0,
                        sets = sets,
                        reps = 10,
                        restSeconds = restSeconds,
                        notes = null
                    )
                )
            )
        ),
        createdAt = Instant.fromEpochMilliseconds(0),
        updatedAt = Instant.fromEpochMilliseconds(0)
    )

    private fun fakeViewModel(initialState: WorkoutExecutionState): WorkoutExecutionViewModel {
        val flow = MutableStateFlow(initialState)
        val fakeRepo = object : TrainingLogRepository {
            override suspend fun saveTrainingLog(log: TrainingLog): Result<Unit> = Result.success(Unit)
            override fun getTrainingHistory(): Flow<List<TrainingLog>> = flowOf(emptyList())
            override suspend fun syncPendingLogs(): Result<Int> = Result.success(0)
        }
        return object : WorkoutExecutionViewModel(trainingLogRepository = fakeRepo) {
            override val state: StateFlow<WorkoutExecutionState> = flow.asStateFlow()
        }
    }

    // ---------------------------------------------------------------------------
    // Test 1: Exercise name and set progression are displayed (Req 4.1, 4.2)
    // ---------------------------------------------------------------------------

    @Test
    fun exerciseNameAndSetProgression_areDisplayed() {
        val plan = samplePlan()
        val state = WorkoutExecutionState(
            currentPlan = plan,
            currentDayIndex = 0,
            currentExerciseIndex = 0,
            currentSetIndex = 0
        )

        composeTestRule.setContent {
            WorkoutExecutionScreen(
                viewModel = fakeViewModel(state),
                workoutPlan = plan
            )
        }

        // Exercise name should be visible
        composeTestRule.onNodeWithText("Supino Reto").assertIsDisplayed()
        // Set progression label
        composeTestRule.onNodeWithText("Série 1 de 3").assertIsDisplayed()
    }

    // ---------------------------------------------------------------------------
    // Test 2: "Complete Set" button is displayed and tappable (Req 4.2)
    // ---------------------------------------------------------------------------

    @Test
    fun completeSetButton_isDisplayed_whenNotResting() {
        val plan = samplePlan()
        val state = WorkoutExecutionState(
            currentPlan = plan,
            currentDayIndex = 0,
            currentExerciseIndex = 0,
            currentSetIndex = 0,
            isRestTimerActive = false
        )

        composeTestRule.setContent {
            WorkoutExecutionScreen(
                viewModel = fakeViewModel(state),
                workoutPlan = plan
            )
        }

        composeTestRule.onNodeWithText("Completar Série 1 de 3").assertIsDisplayed()
    }

    // ---------------------------------------------------------------------------
    // Test 3: Rest timer card is displayed when rest is active (Req 4.3)
    // ---------------------------------------------------------------------------

    @Test
    fun restTimerCard_isDisplayed_whenRestIsActive() {
        val plan = samplePlan(restSeconds = 60)
        val state = WorkoutExecutionState(
            currentPlan = plan,
            currentDayIndex = 0,
            currentExerciseIndex = 0,
            currentSetIndex = 1,
            isRestTimerActive = true,
            restTimerSecondsRemaining = 45
        )

        composeTestRule.setContent {
            WorkoutExecutionScreen(
                viewModel = fakeViewModel(state),
                workoutPlan = plan
            )
        }

        composeTestRule.onNodeWithText("Descanso").assertIsDisplayed()
        composeTestRule.onNodeWithText("45s").assertIsDisplayed()
        composeTestRule.onNodeWithText("Pular Descanso").assertIsDisplayed()
    }

    // ---------------------------------------------------------------------------
    // Test 4: Weight and reps adjustment controls are visible when not resting (Req 4.5)
    // ---------------------------------------------------------------------------

    @Test
    fun weightAndRepsAdjustment_isDisplayed_whenNotResting() {
        val plan = samplePlan()
        val state = WorkoutExecutionState(
            currentPlan = plan,
            currentDayIndex = 0,
            currentExerciseIndex = 0,
            currentSetIndex = 0,
            isRestTimerActive = false
        )

        composeTestRule.setContent {
            WorkoutExecutionScreen(
                viewModel = fakeViewModel(state),
                workoutPlan = plan
            )
        }

        composeTestRule.onNodeWithText("Ajustar Série").assertIsDisplayed()
        composeTestRule.onNodeWithText("Peso (kg)").assertIsDisplayed()
        composeTestRule.onNodeWithText("Reps").assertIsDisplayed()
    }

    // ---------------------------------------------------------------------------
    // Test 5: Workout completion summary is shown when workout is complete (Req 4.4)
    // ---------------------------------------------------------------------------

    @Test
    fun completionSummary_isDisplayed_whenWorkoutIsComplete() {
        val plan = samplePlan()
        val state = WorkoutExecutionState(
            currentPlan = plan,
            isWorkoutComplete = true
        )

        composeTestRule.setContent {
            WorkoutExecutionScreen(
                viewModel = fakeViewModel(state),
                workoutPlan = plan
            )
        }

        composeTestRule.onNodeWithText("Treino Concluído!", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("Voltar ao Início").assertIsDisplayed()
    }

    // ---------------------------------------------------------------------------
    // Test 6: "Voltar ao Início" button triggers onWorkoutFinished callback
    // ---------------------------------------------------------------------------

    @Test
    fun backToHomeButton_triggersCallback_onWorkoutComplete() {
        val plan = samplePlan()
        val state = WorkoutExecutionState(
            currentPlan = plan,
            isWorkoutComplete = true
        )
        var finished = false

        composeTestRule.setContent {
            WorkoutExecutionScreen(
                viewModel = fakeViewModel(state),
                workoutPlan = plan,
                onWorkoutFinished = { finished = true }
            )
        }

        composeTestRule.onNodeWithText("Voltar ao Início").performClick()

        assertTrue("onWorkoutFinished should have been called", finished)
    }
}
