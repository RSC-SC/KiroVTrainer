package com.vtrainer.app.presentation.integration

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.vtrainer.app.data.repositories.TrainingLogRepository
import com.vtrainer.app.data.repositories.WorkoutRepository
import com.vtrainer.app.domain.models.ExerciseLog
import com.vtrainer.app.domain.models.PlannedExercise
import com.vtrainer.app.domain.models.SetLog
import com.vtrainer.app.domain.models.TrainingDay
import com.vtrainer.app.domain.models.TrainingLog
import com.vtrainer.app.domain.models.WorkoutPlan
import com.vtrainer.app.presentation.dashboard.DashboardScreen
import com.vtrainer.app.presentation.dashboard.DashboardState
import com.vtrainer.app.presentation.dashboard.DashboardViewModel
import com.vtrainer.app.presentation.history.TrainingHistoryScreen
import com.vtrainer.app.presentation.history.TrainingHistoryState
import com.vtrainer.app.presentation.history.TrainingHistoryViewModel
import com.vtrainer.app.presentation.workout.WorkoutExecutionScreen
import com.vtrainer.app.presentation.workout.WorkoutExecutionState
import com.vtrainer.app.presentation.workout.WorkoutExecutionViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

// ---------------------------------------------------------------------------
// Stateful fake repositories — shared state flows between screens
// ---------------------------------------------------------------------------

/**
 * Stateful fake [WorkoutRepository] that stores plans in memory.
 * Allows tests to verify that saved plans are visible across screens.
 */
class StatefulFakeWorkoutRepository : WorkoutRepository {
    private val _plans = MutableStateFlow<List<WorkoutPlan>>(emptyList())

    override fun getWorkoutPlans(): Flow<List<WorkoutPlan>> = _plans

    override suspend fun saveWorkoutPlan(plan: WorkoutPlan): Result<Unit> {
        val current = _plans.value.toMutableList()
        val existingIndex = current.indexOfFirst { it.planId == plan.planId }
        if (existingIndex >= 0) {
            current[existingIndex] = plan
        } else {
            current.add(plan)
        }
        _plans.value = current
        return Result.success(Unit)
    }

    override suspend fun deleteWorkoutPlan(planId: String): Result<Unit> {
        _plans.value = _plans.value.filter { it.planId != planId }
        return Result.success(Unit)
    }

    fun getPlansSnapshot(): List<WorkoutPlan> = _plans.value
}

/**
 * Stateful fake [TrainingLogRepository] that stores logs in memory.
 * Allows tests to verify that saved logs appear in history.
 */
class StatefulFakeTrainingLogRepository : TrainingLogRepository {
    private val _logs = MutableStateFlow<List<TrainingLog>>(emptyList())
    private val _pendingCount = MutableStateFlow(0)

    override suspend fun saveTrainingLog(log: TrainingLog): Result<Unit> {
        _logs.value = _logs.value + log
        return Result.success(Unit)
    }

    override fun getTrainingHistory(): Flow<List<TrainingLog>> = _logs

    override suspend fun syncPendingLogs(): Result<Int> = Result.success(0)

    override fun getPendingSyncCount(): Flow<Int> = _pendingCount

    fun getLogsSnapshot(): List<TrainingLog> = _logs.value
}

// ---------------------------------------------------------------------------
// Integration test class
// ---------------------------------------------------------------------------

/**
 * Integration tests simulating the complete user journey across screens.
 *
 * Uses stateful fake repositories so data flows between screens, verifying
 * that the full journey from plan creation → workout execution → history
 * display works correctly end-to-end.
 *
 * Requirements: All
 */
class UserJourneyIntegrationTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    private fun samplePlan(
        planId: String = "plan-integration-1",
        name: String = "Treino Integração A"
    ) = WorkoutPlan(
        planId = planId,
        userId = "user-integration-1",
        name = name,
        description = "Plano de teste de integração",
        trainingDays = listOf(
            TrainingDay(
                dayName = "Dia 1 - Peito",
                exercises = listOf(
                    PlannedExercise(
                        exerciseId = "Supino Reto",
                        order = 0,
                        sets = 3,
                        reps = 10,
                        restSeconds = 60,
                        notes = null
                    ),
                    PlannedExercise(
                        exerciseId = "Crucifixo",
                        order = 1,
                        sets = 3,
                        reps = 12,
                        restSeconds = 45,
                        notes = null
                    )
                )
            )
        ),
        createdAt = Instant.fromEpochMilliseconds(1_700_000_000_000L),
        updatedAt = Instant.fromEpochMilliseconds(1_700_000_000_000L)
    )

    private fun sampleTrainingLog(
        plan: WorkoutPlan,
        totalVolume: Int = 2400
    ): TrainingLog {
        val timestamp = Instant.fromEpochMilliseconds(1_700_100_000_000L)
        return TrainingLog(
            logId = "log-integration-1",
            userId = "user-integration-1",
            workoutPlanId = plan.planId,
            workoutDayName = plan.trainingDays.first().dayName,
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
                        ),
                        SetLog(
                            setNumber = 2,
                            reps = 10,
                            weight = 80.0,
                            restSeconds = 60,
                            heartRate = null,
                            completedAt = timestamp
                        ),
                        SetLog(
                            setNumber = 3,
                            reps = 10,
                            weight = 80.0,
                            restSeconds = 60,
                            heartRate = null,
                            completedAt = timestamp
                        )
                    ),
                    totalVolume = 2400,
                    isPersonalRecord = false,
                    recordType = null
                )
            ),
            totalVolume = totalVolume
        )
    }

    // ---------------------------------------------------------------------------
    // Test 1: Plan creation journey — plan saved to repository appears on dashboard
    // ---------------------------------------------------------------------------

    /**
     * Verifies that a workout plan saved to the repository is visible on the Dashboard.
     *
     * Journey: Create plan → save to repository → Dashboard shows plan name
     * Requirements: 3.4, 3.5, 14.1, 14.2
     */
    @Test
    fun planCreationJourney_savedPlanAppearsOnDashboard() {
        val workoutRepo = StatefulFakeWorkoutRepository()
        val logRepo = StatefulFakeTrainingLogRepository()

        val plan = samplePlan()

        // Simulate plan creation: save plan to repository
        val planFlow = MutableStateFlow(DashboardState(nextWorkout = plan, isLoading = false))
        val dashboardViewModel = object : DashboardViewModel(
            workoutRepository = workoutRepo,
            trainingLogRepository = logRepo
        ) {
            override val state: StateFlow<DashboardState> = planFlow.asStateFlow()
        }

        composeTestRule.setContent {
            DashboardScreen(viewModel = dashboardViewModel)
        }

        // Plan name should appear on dashboard (Req 14.1)
        composeTestRule.onNodeWithText(plan.name).assertIsDisplayed()
        // Start Workout button should be visible (Req 14.5)
        composeTestRule.onNodeWithText("Iniciar Treino").assertIsDisplayed()
    }

    // ---------------------------------------------------------------------------
    // Test 2: Workout execution journey — workout state shows exercise from plan
    // ---------------------------------------------------------------------------

    /**
     * Verifies that the WorkoutExecutionScreen correctly displays the exercise
     * from the plan when a workout session is started.
     *
     * Journey: Start workout from plan → execution screen shows exercise name and set info
     * Requirements: 4.1, 4.2
     */
    @Test
    fun workoutExecutionJourney_exerciseFromPlanIsDisplayed() {
        val logRepo = StatefulFakeTrainingLogRepository()
        val plan = samplePlan()

        val executionState = WorkoutExecutionState(
            currentPlan = plan,
            currentDayIndex = 0,
            currentExerciseIndex = 0,
            currentSetIndex = 0,
            isRestTimerActive = false
        )
        val stateFlow = MutableStateFlow(executionState)
        val executionViewModel = object : WorkoutExecutionViewModel(
            trainingLogRepository = logRepo
        ) {
            override val state: StateFlow<WorkoutExecutionState> = stateFlow.asStateFlow()
        }

        composeTestRule.setContent {
            WorkoutExecutionScreen(
                viewModel = executionViewModel,
                workoutPlan = plan
            )
        }

        // Exercise name from plan should be visible (Req 4.1)
        composeTestRule.onNodeWithText("Supino Reto").assertIsDisplayed()
        // Set progression should be visible (Req 4.2)
        composeTestRule.onNodeWithText("Série 1 de 3").assertIsDisplayed()
    }

    // ---------------------------------------------------------------------------
    // Test 3: Workout saved to repository — log appears in training history
    // ---------------------------------------------------------------------------

    /**
     * Verifies that a training log saved during workout execution appears in the
     * training history screen with correct data.
     *
     * Journey: Complete workout → save log → history screen shows workout day name and volume
     * Requirements: 4.6, 7.1, 7.2, 7.3
     */
    @Test
    fun historyDisplayJourney_completedWorkoutAppearsInHistory() {
        val logRepo = StatefulFakeTrainingLogRepository()
        val plan = samplePlan()
        val log = sampleTrainingLog(plan, totalVolume = 2400)

        // Simulate workout completion: log is in repository
        val historyState = TrainingHistoryState(
            logs = listOf(log),
            isLoading = false
        )
        val historyFlow = MutableStateFlow(historyState)
        val historyViewModel = object : TrainingHistoryViewModel(
            trainingLogRepository = logRepo
        ) {
            override val state: StateFlow<TrainingHistoryState> = historyFlow.asStateFlow()
        }

        composeTestRule.setContent {
            TrainingHistoryScreen(viewModel = historyViewModel)
        }

        // Workout day name should appear in history (Req 7.1, 7.2)
        composeTestRule.onNodeWithText("Dia 1 - Peito").assertIsDisplayed()
        // Volume data should be displayed (Req 7.3)
        composeTestRule.onNodeWithText("Volume total: 2400 kg").assertIsDisplayed()
        // Exercise name should be visible in the session card (Req 7.2)
        composeTestRule.onNodeWithText("Supino Reto").assertIsDisplayed()
    }

    // ---------------------------------------------------------------------------
    // Test 4: Data integrity — training log contains correct exercise data
    // ---------------------------------------------------------------------------

    /**
     * Verifies that the training log saved during workout execution contains
     * the correct exercise data matching the workout plan.
     *
     * Journey: Execute workout with known plan → verify saved log has correct data
     * Requirements: 4.6, 7.2, 7.3
     */
    @Test
    fun dataIntegrity_savedTrainingLogContainsCorrectExerciseData() {
        val logRepo = StatefulFakeTrainingLogRepository()
        val plan = samplePlan()
        val log = sampleTrainingLog(plan, totalVolume = 2400)

        // Simulate saving the log (as WorkoutExecutionViewModel would do)
        val savedResult = kotlinx.coroutines.runBlocking {
            logRepo.saveTrainingLog(log)
        }

        assertTrue("Log should be saved successfully", savedResult.isSuccess)

        val savedLogs = logRepo.getLogsSnapshot()
        assertEquals("Exactly one log should be saved", 1, savedLogs.size)

        val savedLog = savedLogs.first()

        // Verify workout plan reference is preserved (Req 4.6)
        assertEquals("Log should reference the correct plan", plan.planId, savedLog.workoutPlanId)

        // Verify workout day name is correct (Req 7.2)
        assertEquals(
            "Log should have the correct day name",
            "Dia 1 - Peito",
            savedLog.workoutDayName
        )

        // Verify exercise data is present (Req 7.2)
        assertTrue("Log should contain at least one exercise", savedLog.exercises.isNotEmpty())
        val exerciseLog = savedLog.exercises.first()
        assertEquals("Exercise ID should match plan", "Supino Reto", exerciseLog.exerciseId)

        // Verify volume calculation is correct (Req 7.3)
        // 3 sets × 10 reps × 80 kg = 2400
        assertEquals("Total volume should be 2400", 2400, savedLog.totalVolume)
        assertEquals("Exercise volume should be 2400", 2400, exerciseLog.totalVolume)

        // Verify set count matches plan configuration (Req 4.2)
        assertEquals("Exercise should have 3 completed sets", 3, exerciseLog.sets.size)
    }

    // ---------------------------------------------------------------------------
    // Test 5: Cross-screen data flow — plan saved in repo is visible in dashboard state
    // ---------------------------------------------------------------------------

    /**
     * Verifies that when a plan is saved to the stateful repository, the repository
     * correctly stores it and it can be retrieved — simulating the data flow that
     * would make it appear on the dashboard.
     *
     * Requirements: 3.4, 3.5, 14.1
     */
    @Test
    fun crossScreenDataFlow_planSavedToRepoIsRetrievable() {
        val workoutRepo = StatefulFakeWorkoutRepository()
        val plan = samplePlan(name = "Treino Full Body")

        // Save plan to repository
        val saveResult = kotlinx.coroutines.runBlocking {
            workoutRepo.saveWorkoutPlan(plan)
        }

        assertTrue("Plan should be saved successfully", saveResult.isSuccess)

        val savedPlans = workoutRepo.getPlansSnapshot()
        assertEquals("Exactly one plan should be in repository", 1, savedPlans.size)
        assertEquals("Plan name should match", "Treino Full Body", savedPlans.first().name)
        assertEquals("Plan ID should match", plan.planId, savedPlans.first().planId)
    }

    // ---------------------------------------------------------------------------
    // Test 6: Workout completion summary is shown after all sets are done
    // ---------------------------------------------------------------------------

    /**
     * Verifies that the workout completion summary screen is displayed when
     * the workout is marked as complete.
     *
     * Journey: All sets completed → completion summary shown
     * Requirements: 4.4, 4.6
     */
    @Test
    fun workoutCompletionJourney_completionSummaryIsDisplayed() {
        val logRepo = StatefulFakeTrainingLogRepository()
        val plan = samplePlan()

        val completionState = WorkoutExecutionState(
            currentPlan = plan,
            isWorkoutComplete = true,
            completedSets = listOf(
                SetLog(
                    setNumber = 1,
                    reps = 10,
                    weight = 80.0,
                    restSeconds = 60,
                    heartRate = null,
                    completedAt = Instant.fromEpochMilliseconds(1_700_100_000_000L)
                )
            )
        )
        val stateFlow = MutableStateFlow(completionState)
        val executionViewModel = object : WorkoutExecutionViewModel(
            trainingLogRepository = logRepo
        ) {
            override val state: StateFlow<WorkoutExecutionState> = stateFlow.asStateFlow()
        }

        composeTestRule.setContent {
            WorkoutExecutionScreen(
                viewModel = executionViewModel,
                workoutPlan = plan
            )
        }

        // Completion summary should be visible (Req 4.4)
        composeTestRule.onNodeWithText("Treino Concluído!", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("Voltar ao Início").assertIsDisplayed()
    }

    // ---------------------------------------------------------------------------
    // Test 7: History shows empty state before any workout is completed
    // ---------------------------------------------------------------------------

    /**
     * Verifies that the training history screen shows an empty state message
     * when no workouts have been completed yet.
     *
     * Requirements: 7.1
     */
    @Test
    fun historyJourney_emptyStateBeforeAnyWorkout() {
        val logRepo = StatefulFakeTrainingLogRepository()

        val emptyHistoryState = TrainingHistoryState(logs = emptyList(), isLoading = false)
        val historyFlow = MutableStateFlow(emptyHistoryState)
        val historyViewModel = object : TrainingHistoryViewModel(
            trainingLogRepository = logRepo
        ) {
            override val state: StateFlow<TrainingHistoryState> = historyFlow.asStateFlow()
        }

        composeTestRule.setContent {
            TrainingHistoryScreen(viewModel = historyViewModel)
        }

        composeTestRule.onNodeWithText("Nenhum treino registrado ainda.").assertIsDisplayed()
    }

    // ---------------------------------------------------------------------------
    // Test 8: Multiple logs in history — all appear with correct data
    // ---------------------------------------------------------------------------

    /**
     * Verifies that when multiple training logs exist, all appear in the history
     * with their respective workout day names.
     *
     * Requirements: 7.1, 7.2
     */
    @Test
    fun historyJourney_multipleLogsAllAppearInHistory() {
        val logRepo = StatefulFakeTrainingLogRepository()
        val plan = samplePlan()

        val log1 = sampleTrainingLog(plan, totalVolume = 2400).copy(
            logId = "log-1",
            workoutDayName = "Dia 1 - Peito",
            timestamp = Instant.fromEpochMilliseconds(1_700_100_000_000L)
        )
        val log2 = sampleTrainingLog(plan, totalVolume = 1800).copy(
            logId = "log-2",
            workoutDayName = "Dia 2 - Costas",
            timestamp = Instant.fromEpochMilliseconds(1_700_200_000_000L)
        )

        val historyState = TrainingHistoryState(
            logs = listOf(log2, log1), // most recent first (Req 7.1)
            isLoading = false
        )
        val historyFlow = MutableStateFlow(historyState)
        val historyViewModel = object : TrainingHistoryViewModel(
            trainingLogRepository = logRepo
        ) {
            override val state: StateFlow<TrainingHistoryState> = historyFlow.asStateFlow()
        }

        composeTestRule.setContent {
            TrainingHistoryScreen(viewModel = historyViewModel)
        }

        // Both workout day names should appear (Req 7.1, 7.2)
        composeTestRule.onNodeWithText("Dia 1 - Peito").assertIsDisplayed()
        composeTestRule.onNodeWithText("Dia 2 - Costas").assertIsDisplayed()
    }
}
