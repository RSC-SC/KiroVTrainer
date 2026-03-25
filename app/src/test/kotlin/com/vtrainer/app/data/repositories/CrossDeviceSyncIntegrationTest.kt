package com.vtrainer.app.data.repositories

import com.vtrainer.app.domain.models.ExerciseLog
import com.vtrainer.app.domain.models.PlannedExercise
import com.vtrainer.app.domain.models.SetLog
import com.vtrainer.app.domain.models.TrainingDay
import com.vtrainer.app.domain.models.TrainingLog
import com.vtrainer.app.domain.models.WorkoutPlan
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Instant

// ---------------------------------------------------------------------------
// Shared backend repository — simulates Firestore as the common backend
// ---------------------------------------------------------------------------

/**
 * Simulates a shared Firestore backend. Both "mobile" and "watch" instances
 * of [SharedBackendWorkoutRepository] and [SharedBackendTrainingLogRepository]
 * read from and write to the same [SharedBackendState], replicating the
 * cross-device synchronization that Firestore provides in production.
 */
class SharedBackendState {
    val plans = MutableStateFlow<List<WorkoutPlan>>(emptyList())
    val logs = MutableStateFlow<List<TrainingLog>>(emptyList())
}

/**
 * [WorkoutRepository] backed by a shared [SharedBackendState].
 * Both mobile and watch instances share the same state object, simulating
 * Firestore real-time sync.
 *
 * Requirements: 3.4, 6.4
 */
class SharedBackendWorkoutRepository(
    private val state: SharedBackendState
) : WorkoutRepository {

    override fun getWorkoutPlans(): Flow<List<WorkoutPlan>> = state.plans

    override suspend fun saveWorkoutPlan(plan: WorkoutPlan): Result<Unit> {
        val current = state.plans.value.toMutableList()
        val idx = current.indexOfFirst { it.planId == plan.planId }
        if (idx >= 0) current[idx] = plan else current.add(plan)
        state.plans.value = current
        return Result.success(Unit)
    }

    override suspend fun deleteWorkoutPlan(planId: String): Result<Unit> {
        state.plans.value = state.plans.value.filter { it.planId != planId }
        return Result.success(Unit)
    }
}

/**
 * [TrainingLogRepository] backed by a shared [SharedBackendState].
 * Both mobile and watch instances share the same state object, simulating
 * Firestore real-time sync.
 *
 * Requirements: 6.4, 1.2
 */
class SharedBackendTrainingLogRepository(
    private val state: SharedBackendState
) : TrainingLogRepository {

    override suspend fun saveTrainingLog(log: TrainingLog): Result<Unit> {
        state.logs.value = state.logs.value + log
        return Result.success(Unit)
    }

    override fun getTrainingHistory(): Flow<List<TrainingLog>> = state.logs

    override suspend fun syncPendingLogs(): Result<Int> = Result.success(0)

    override fun getPendingSyncCount(): Flow<Int> =
        MutableStateFlow(0)
}

// ---------------------------------------------------------------------------
// Test helpers
// ---------------------------------------------------------------------------

private fun makeWorkoutPlan(
    planId: String = "plan-1",
    userId: String = "user-1",
    name: String = "Treino Full Body",
    updatedAt: Instant = Instant.fromEpochMilliseconds(1_700_000_000_000L)
): WorkoutPlan = WorkoutPlan(
    planId = planId,
    userId = userId,
    name = name,
    description = "Plano de teste",
    trainingDays = listOf(
        TrainingDay(
            dayName = "Dia A",
            exercises = listOf(
                PlannedExercise(
                    exerciseId = "bench-press",
                    order = 0,
                    sets = 4,
                    reps = 8,
                    restSeconds = 90,
                    notes = null
                )
            )
        )
    ),
    createdAt = Instant.fromEpochMilliseconds(1_699_000_000_000L),
    updatedAt = updatedAt
)

private fun makeTrainingLog(
    logId: String = "log-1",
    userId: String = "user-1",
    planId: String? = "plan-1",
    dayName: String = "Dia A",
    origin: String = "Galaxy_Watch_4",
    timestamp: Instant = Instant.fromEpochMilliseconds(1_700_000_000_000L)
): TrainingLog = TrainingLog(
    logId = logId,
    userId = userId,
    workoutPlanId = planId,
    workoutDayName = dayName,
    timestamp = timestamp,
    origin = origin,
    duration = 3600,
    totalCalories = 350,
    exercises = listOf(
        ExerciseLog(
            exerciseId = "bench-press",
            sets = listOf(
                SetLog(
                    setNumber = 1,
                    reps = 8,
                    weight = 80.0,
                    restSeconds = 90,
                    heartRate = 145,
                    completedAt = timestamp
                )
            ),
            totalVolume = 640,
            isPersonalRecord = false,
            recordType = null
        )
    ),
    totalVolume = 640
)

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

/**
 * Integration tests for cross-device synchronization using a shared in-memory
 * repository that simulates Firestore as the common backend.
 *
 * The "cross-device" simulation works by having both mobile and watch use the
 * same [SharedBackendState] instance. This tests the data contract between
 * devices without requiring a real Firestore connection.
 *
 * Requirements: 1.2, 3.4, 6.4
 */
class CrossDeviceSyncIntegrationTest : FunSpec({

    // -----------------------------------------------------------------------
    // Test 1: Plan created on mobile appears on watch
    // -----------------------------------------------------------------------

    /**
     * When a workout plan is saved via the mobile repository, the watch
     * repository (sharing the same backend) can immediately read it.
     *
     * Requirements: 3.4
     */
    test("plan created on mobile appears on watch") {
        val sharedState = SharedBackendState()
        val mobileWorkoutRepo = SharedBackendWorkoutRepository(sharedState)
        val watchWorkoutRepo = SharedBackendWorkoutRepository(sharedState)

        val plan = makeWorkoutPlan(name = "Treino Peito e Tríceps")
        mobileWorkoutRepo.saveWorkoutPlan(plan)

        val watchPlans = watchWorkoutRepo.getWorkoutPlans().first()
        watchPlans.size shouldBe 1
        watchPlans.first().planId shouldBe plan.planId
        watchPlans.first().name shouldBe "Treino Peito e Tríceps"
    }

    // -----------------------------------------------------------------------
    // Test 2: Workout completed on watch appears in mobile history
    // -----------------------------------------------------------------------

    /**
     * When a training log is saved via the watch repository, the mobile
     * repository (sharing the same backend) can immediately read it in history.
     *
     * Requirements: 6.4
     */
    test("workout completed on watch appears in mobile history") {
        val sharedState = SharedBackendState()
        val mobileLogRepo = SharedBackendTrainingLogRepository(sharedState)
        val watchLogRepo = SharedBackendTrainingLogRepository(sharedState)

        val log = makeTrainingLog(logId = "watch-log-1", origin = "Galaxy_Watch_4")
        watchLogRepo.saveTrainingLog(log)

        val mobileHistory = mobileLogRepo.getTrainingHistory().first()
        mobileHistory.size shouldBe 1
        mobileHistory.first().logId shouldBe "watch-log-1"
    }

    // -----------------------------------------------------------------------
    // Test 3: Watch origin is preserved in training log
    // -----------------------------------------------------------------------

    /**
     * When the watch saves a training log with origin = "Galaxy_Watch_4",
     * the origin field is preserved when mobile reads it.
     *
     * Requirements: 6.4
     */
    test("watch origin is preserved in training log when mobile reads it") {
        val sharedState = SharedBackendState()
        val mobileLogRepo = SharedBackendTrainingLogRepository(sharedState)
        val watchLogRepo = SharedBackendTrainingLogRepository(sharedState)

        val watchLog = makeTrainingLog(origin = "Galaxy_Watch_4")
        watchLogRepo.saveTrainingLog(watchLog)

        val mobileHistory = mobileLogRepo.getTrainingHistory().first()
        mobileHistory.first().origin shouldBe "Galaxy_Watch_4"
    }

    // -----------------------------------------------------------------------
    // Test 4: Plan update on mobile is visible on watch
    // -----------------------------------------------------------------------

    /**
     * When an existing plan is updated on mobile, the watch sees the updated
     * version (same planId, new name and updatedAt).
     *
     * Requirements: 3.4, 1.2
     */
    test("plan update on mobile is visible on watch") {
        val sharedState = SharedBackendState()
        val mobileWorkoutRepo = SharedBackendWorkoutRepository(sharedState)
        val watchWorkoutRepo = SharedBackendWorkoutRepository(sharedState)

        val originalPlan = makeWorkoutPlan(
            planId = "plan-update",
            name = "Treino Original",
            updatedAt = Instant.fromEpochMilliseconds(1_700_000_000_000L)
        )
        mobileWorkoutRepo.saveWorkoutPlan(originalPlan)

        val updatedPlan = originalPlan.copy(
            name = "Treino Atualizado",
            updatedAt = Instant.fromEpochMilliseconds(1_700_100_000_000L)
        )
        mobileWorkoutRepo.saveWorkoutPlan(updatedPlan)

        val watchPlans = watchWorkoutRepo.getWorkoutPlans().first()
        watchPlans.size shouldBe 1
        watchPlans.first().name shouldBe "Treino Atualizado"
        watchPlans.first().updatedAt shouldBe Instant.fromEpochMilliseconds(1_700_100_000_000L)
    }

    // -----------------------------------------------------------------------
    // Test 5: Multiple devices see same training history
    // -----------------------------------------------------------------------

    /**
     * Both mobile and watch read from the same log repository and see
     * identical training history.
     *
     * Requirements: 6.4
     */
    test("multiple devices see same training history") {
        val sharedState = SharedBackendState()
        val mobileLogRepo = SharedBackendTrainingLogRepository(sharedState)
        val watchLogRepo = SharedBackendTrainingLogRepository(sharedState)

        val log1 = makeTrainingLog(logId = "log-a", origin = "mobile",
            timestamp = Instant.fromEpochMilliseconds(1_700_000_000_000L))
        val log2 = makeTrainingLog(logId = "log-b", origin = "Galaxy_Watch_4",
            timestamp = Instant.fromEpochMilliseconds(1_700_100_000_000L))

        mobileLogRepo.saveTrainingLog(log1)
        watchLogRepo.saveTrainingLog(log2)

        val mobileHistory = mobileLogRepo.getTrainingHistory().first()
        val watchHistory = watchLogRepo.getTrainingHistory().first()

        mobileHistory.size shouldBe 2
        watchHistory.size shouldBe 2
        mobileHistory.map { it.logId }.toSet() shouldBe watchHistory.map { it.logId }.toSet()
    }

    // -----------------------------------------------------------------------
    // Test 6: Plan deletion on mobile removes it from watch
    // -----------------------------------------------------------------------

    /**
     * When a plan is deleted via the mobile repository, the watch repository
     * (sharing the same backend) no longer sees the plan.
     *
     * Requirements: 3.4
     */
    test("plan deletion on mobile removes it from watch") {
        val sharedState = SharedBackendState()
        val mobileWorkoutRepo = SharedBackendWorkoutRepository(sharedState)
        val watchWorkoutRepo = SharedBackendWorkoutRepository(sharedState)

        val plan = makeWorkoutPlan(planId = "plan-to-delete", name = "Treino Temporário")
        mobileWorkoutRepo.saveWorkoutPlan(plan)

        val watchPlansBefore = watchWorkoutRepo.getWorkoutPlans().first()
        watchPlansBefore.size shouldBe 1

        mobileWorkoutRepo.deleteWorkoutPlan(plan.planId)

        val watchPlansAfter = watchWorkoutRepo.getWorkoutPlans().first()
        watchPlansAfter.size shouldBe 0
        watchPlansAfter.none { it.planId == plan.planId } shouldBe true
    }
})
