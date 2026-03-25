package com.vtrainer.app.presentation.dashboard

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModel
import com.vtrainer.app.domain.models.PersonalRecord
import com.vtrainer.app.domain.models.TrainingDay
import com.vtrainer.app.domain.models.WorkoutPlan
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.Instant
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * UI tests for [DashboardScreen].
 *
 * Validates: Requirements 14.5
 */
class DashboardScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // ---------------------------------------------------------------------------
    // Fake ViewModel helpers
    // ---------------------------------------------------------------------------

    private fun fakeViewModel(state: DashboardState): DashboardViewModel {
        val flow = MutableStateFlow(state)
        return object : DashboardViewModel(
            workoutRepository = FakeWorkoutRepository(),
            trainingLogRepository = FakeTrainingLogRepository()
        ) {
            override val state: StateFlow<DashboardState> = flow.asStateFlow()
        }
    }

    private fun sampleWorkoutPlan() = WorkoutPlan(
        planId = "plan-1",
        userId = "user-1",
        name = "Full Body A",
        description = "Push/Pull/Legs",
        trainingDays = emptyList<TrainingDay>(),
        createdAt = Instant.fromEpochMilliseconds(0),
        updatedAt = Instant.fromEpochMilliseconds(0)
    )

    // ---------------------------------------------------------------------------
    // Test 1: "Start Workout" button is displayed when a next workout is available
    // ---------------------------------------------------------------------------

    @Test
    fun startWorkoutButton_isDisplayed_whenNextWorkoutAvailable() {
        val state = DashboardState(
            nextWorkout = sampleWorkoutPlan(),
            isLoading = false
        )

        composeTestRule.setContent {
            DashboardScreen(viewModel = fakeViewModel(state))
        }

        composeTestRule.onNodeWithText("Iniciar Treino").assertIsDisplayed()
    }

    // ---------------------------------------------------------------------------
    // Test 2: "Start Workout" button triggers onStartWorkout callback (Req 14.5)
    // ---------------------------------------------------------------------------

    @Test
    fun startWorkoutButton_triggersCallback_whenTapped() {
        val plan = sampleWorkoutPlan()
        val state = DashboardState(nextWorkout = plan, isLoading = false)
        var callbackPlan: WorkoutPlan? = null

        composeTestRule.setContent {
            DashboardScreen(
                viewModel = fakeViewModel(state),
                onStartWorkout = { callbackPlan = it }
            )
        }

        composeTestRule.onNodeWithText("Iniciar Treino").performClick()

        assertTrue("onStartWorkout callback should have been invoked", callbackPlan != null)
        assertTrue("Callback should receive the correct plan", callbackPlan?.planId == plan.planId)
    }

    // ---------------------------------------------------------------------------
    // Test 3: Weekly stats section is displayed
    // ---------------------------------------------------------------------------

    @Test
    fun weeklyStatsSection_isDisplayed() {
        val state = DashboardState(
            weeklyWorkoutCount = 3,
            weeklyTotalVolume = 1500L,
            isLoading = false
        )

        composeTestRule.setContent {
            DashboardScreen(viewModel = fakeViewModel(state))
        }

        composeTestRule.onNodeWithText("Resumo Semanal").assertIsDisplayed()
    }

    // ---------------------------------------------------------------------------
    // Test 4: Loading indicator is shown when isLoading=true
    // ---------------------------------------------------------------------------

    @Test
    fun loadingIndicator_isShown_whenIsLoadingTrue() {
        val state = DashboardState(isLoading = true)

        composeTestRule.setContent {
            DashboardScreen(viewModel = fakeViewModel(state))
        }

        // When loading, the lazy column content (e.g. "Resumo Semanal") should NOT be visible
        composeTestRule.onNodeWithText("Resumo Semanal").assertDoesNotExist()
        // The "Iniciar Treino" button should also not be visible
        composeTestRule.onNodeWithText("Iniciar Treino").assertDoesNotExist()
    }

    // ---------------------------------------------------------------------------
    // Test 5: Error message is shown when error is non-null
    // ---------------------------------------------------------------------------

    @Test
    fun errorMessage_isShown_whenErrorIsNonNull() {
        val errorMsg = "Failed to load dashboard data"
        val state = DashboardState(isLoading = false, error = errorMsg)

        composeTestRule.setContent {
            DashboardScreen(viewModel = fakeViewModel(state))
        }

        composeTestRule.onNodeWithText(errorMsg).assertIsDisplayed()
    }
}
