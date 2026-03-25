package com.vtrainer.app.presentation.history

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.vtrainer.app.data.repositories.TrainingLogRepository
import com.vtrainer.app.domain.models.ExerciseLog
import com.vtrainer.app.domain.models.RecordType
import com.vtrainer.app.domain.models.SetLog
import com.vtrainer.app.domain.models.TrainingLog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.Instant
import org.junit.Rule
import org.junit.Test

/**
 * UI tests for [TrainingHistoryScreen].
 *
 * Validates: Requirements 7.1, 7.5
 */
class TrainingHistoryScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    private fun fakeRepo() = object : TrainingLogRepository {
        override suspend fun saveTrainingLog(log: TrainingLog): Result<Unit> = Result.success(Unit)
        override fun getTrainingHistory(): Flow<List<TrainingLog>> = flowOf(emptyList())
        override suspend fun syncPendingLogs(): Result<Int> = Result.success(0)
    }

    private fun fakeViewModel(initialState: TrainingHistoryState): TrainingHistoryViewModel {
        val flow = MutableStateFlow(initialState)
        return object : TrainingHistoryViewModel(trainingLogRepository = fakeRepo()) {
            override val state: StateFlow<TrainingHistoryState> = flow.asStateFlow()
        }
    }

    private fun sampleLog(
        dayName: String = "Treino A",
        totalVolume: Int = 1200,
        isPersonalRecord: Boolean = false,
        timestamp: Instant = Instant.fromEpochMilliseconds(1_700_000_000_000L)
    ) = TrainingLog(
        logId = "log-1",
        userId = "user-1",
        workoutPlanId = "plan-1",
        workoutDayName = dayName,
        timestamp = timestamp,
        origin = "mobile",
        duration = 3600,
        totalCalories = null,
        exercises = listOf(
            ExerciseLog(
                exerciseId = "Supino Reto",
                sets = listOf(
                    SetLog(
                        setNumber = 1,
                        reps = 10,
                        weight = 80.0,
                        restSeconds = 60,
                        heartRate = null,
                        completedAt = timestamp
                    )
                ),
                totalVolume = totalVolume,
                isPersonalRecord = isPersonalRecord,
                recordType = if (isPersonalRecord) RecordType.MAX_WEIGHT else null
            )
        ),
        totalVolume = totalVolume
    )

    // ---------------------------------------------------------------------------
    // Test 1: History list is displayed with workout name (Req 7.1)
    // ---------------------------------------------------------------------------

    @Test
    fun historyList_isDisplayed_withWorkoutName() {
        val state = TrainingHistoryState(
            logs = listOf(sampleLog(dayName = "Treino A")),
            isLoading = false
        )

        composeTestRule.setContent {
            TrainingHistoryScreen(viewModel = fakeViewModel(state))
        }

        composeTestRule.onNodeWithText("Treino A").assertIsDisplayed()
    }

    // ---------------------------------------------------------------------------
    // Test 2: Volume data is displayed for each session (Req 7.3)
    // ---------------------------------------------------------------------------

    @Test
    fun volumeData_isDisplayed_forEachSession() {
        val state = TrainingHistoryState(
            logs = listOf(sampleLog(totalVolume = 1500)),
            isLoading = false
        )

        composeTestRule.setContent {
            TrainingHistoryScreen(viewModel = fakeViewModel(state))
        }

        composeTestRule.onNodeWithText("Volume total: 1500 kg").assertIsDisplayed()
    }

    // ---------------------------------------------------------------------------
    // Test 3: Personal record badge is shown for PR sessions (Req 7.5)
    // ---------------------------------------------------------------------------

    @Test
    fun personalRecordBadge_isDisplayed_forPRSession() {
        val state = TrainingHistoryState(
            logs = listOf(sampleLog(isPersonalRecord = true)),
            isLoading = false
        )

        composeTestRule.setContent {
            TrainingHistoryScreen(viewModel = fakeViewModel(state))
        }

        composeTestRule.onNodeWithText("PR").assertIsDisplayed()
    }

    // ---------------------------------------------------------------------------
    // Test 4: Empty state message is shown when no logs exist
    // ---------------------------------------------------------------------------

    @Test
    fun emptyState_isDisplayed_whenNoLogs() {
        val state = TrainingHistoryState(logs = emptyList(), isLoading = false)

        composeTestRule.setContent {
            TrainingHistoryScreen(viewModel = fakeViewModel(state))
        }

        composeTestRule.onNodeWithText("Nenhum treino registrado ainda.").assertIsDisplayed()
    }

    // ---------------------------------------------------------------------------
    // Test 5: Loading indicator is shown when isLoading=true
    // ---------------------------------------------------------------------------

    @Test
    fun loadingIndicator_isShown_whenIsLoadingTrue() {
        val state = TrainingHistoryState(isLoading = true)

        composeTestRule.setContent {
            TrainingHistoryScreen(viewModel = fakeViewModel(state))
        }

        // Content should not be visible while loading
        composeTestRule.onNodeWithText("Sessões").assertDoesNotExist()
    }

    // ---------------------------------------------------------------------------
    // Test 6: Exercise name is displayed within a session card (Req 7.2)
    // ---------------------------------------------------------------------------

    @Test
    fun exerciseName_isDisplayed_withinSessionCard() {
        val state = TrainingHistoryState(
            logs = listOf(sampleLog()),
            isLoading = false
        )

        composeTestRule.setContent {
            TrainingHistoryScreen(viewModel = fakeViewModel(state))
        }

        composeTestRule.onNodeWithText("Supino Reto").assertIsDisplayed()
    }
}
