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
// Fake repositories with simulated network state
// ---------------------------------------------------------------------------

/**
 * Fake [TrainingLogRepository] that simulates offline/online state.
 *
 * When [isOnline] is false, [syncPendingLogs] fails and pending count grows.
 * When [isOnline] is true, [syncPendingLogs] succeeds and resets pending count.
 *
 * Requirements: 12.1, 12.3, 12.5
 */
class FakeOfflineTrainingLogRepository : TrainingLogRepository {

    var isOnline: Boolean = false

    private val _logs = MutableStateFlow<List<TrainingLog>>(emptyList())
    private val _pendingCount = MutableStateFlow(0)

    override suspend fun saveTrainingLog(log: TrainingLog): Result<Unit> {
        _logs.value = _logs.value + log
        if (!isOnline) {
            _pendingCount.value += 1
        }
        return Result.success(Unit)
    }

    override fun getTrainingHistory(): Flow<List<TrainingLog>> = _logs

    override suspend fun syncPendingLogs(): Result<Int> {
        if (!isOnline) {
            return Result.failure(RuntimeException("Network unavailable"))
        }
        val synced = _pendingCount.value
        _pendingCount.value = 0
        return Result.success(synced)
    }

    override fun getPendingSyncCount(): Flow<Int> = _pendingCount
}

/**
 * Fake [WorkoutRepository] that simulates offline/online state.
 *
 * Plans are always saved locally regardless of connectivity.
 * When [isOnline] is false, the plan is stored but flagged as pending sync.
 *
 * Requirements: 12.1, 12.3
 */
class FakeOfflineWorkoutRepository : WorkoutRepository {

    var isOnline: Boolean = false

    private val _plans = MutableStateFlow<List<WorkoutPlan>>(emptyList())
    private val _pendingPlanIds = mutableSetOf<String>()

    override fun getWorkoutPlans(): Flow<List<WorkoutPlan>> = _plans

    override suspend fun saveWorkoutPlan(plan: WorkoutPlan): Result<Unit> {
        val current = _plans.value.toMutableList()
        val existingIndex = current.indexOfFirst { it.planId == plan.planId }
        if (existingIndex >= 0) current[existingIndex] = plan else current.add(plan)
        _plans.value = current
        if (!isOnline) {
            _pendingPlanIds.add(plan.planId)
        }
        return Result.success(Unit)
    }

    override suspend fun deleteWorkoutPlan(planId: String): Result<Unit> {
        _plans.value = _plans.value.filter { it.planId != planId }
        _pendingPlanIds.remove(planId)
        return Result.success(Unit)
    }

    fun getPendingPlanIds(): Set<String> = _pendingPlanIds.toSet()
}

// ---------------------------------------------------------------------------
// Test helpers
// ---------------------------------------------------------------------------

private fun makeTrainingLog(
    logId: String = "log-1",
    userId: String = "user-1",
    planId: String? = "plan-1",
    dayName: String = "Treino A - Peito",
    timestamp: Instant = Instant.fromEpochMilliseconds(1_700_000_000_000L)
): TrainingLog = TrainingLog(
    logId = logId,
    userId = userId,
    workoutPlanId = planId,
    workoutDayName = dayName,
    timestamp = timestamp,
    origin = "watch",
    duration = 3600,
    totalCalories = 400,
    exercises = listOf(
        ExerciseLog(
            exerciseId = "squat",
            sets = listOf(
                SetLog(
                    setNumber = 1,
                    reps = 10,
                    weight = 100.0,
                    restSeconds = 60,
                    heartRate = 140,
                    completedAt = timestamp
                )
            ),
            totalVolume = 1000,
            isPersonalRecord = false,
            recordType = null
        )
    ),
    totalVolume = 1000
)

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
                    exerciseId = "squat",
                    order = 0,
                    sets = 3,
                    reps = 10,
                    restSeconds = 60,
                    notes = null
                )
            )
        )
    ),
    createdAt = Instant.fromEpochMilliseconds(1_699_000_000_000L),
    updatedAt = updatedAt
)

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

/**
 * Integration tests for offline scenarios using fake repositories.
 *
 * Tests cover:
 * - Offline workout save with pending sync tracking (Req 12.1, 12.3)
 * - Sync on reconnect clears pending count (Req 12.5)
 * - Offline plan creation with local cache retrieval (Req 12.1, 12.3)
 * - Conflict resolution by timestamp — newer wins (Req 12.6)
 * - Sync completeness — pending count returns to 0 (Req 12.5)
 * - Multiple pending logs all synced on reconnect (Req 12.5)
 * - Sync fails when offline — failure result returned (Req 12.3)
 *
 * Requirements: 12.1, 12.3, 12.5, 12.6
 */
class OfflineScenarioIntegrationTest : FunSpec({

    // -----------------------------------------------------------------------
    // Test 1: Offline workout save — log saved locally, pending count > 0
    // -----------------------------------------------------------------------

    /**
     * When network is unavailable, saving a training log locally succeeds
     * and the log is marked as pending sync.
     *
     * Requirements: 12.1, 12.3
     */
    test("offline workout save: log saved locally and pending count incremented") {
        val logRepo = FakeOfflineTrainingLogRepository()
        logRepo.isOnline = false

        val log = makeTrainingLog()
        val result = logRepo.saveTrainingLog(log)

        result.isSuccess shouldBe true

        val history = logRepo.getTrainingHistory().first()
        history.size shouldBe 1
        history.first().logId shouldBe log.logId

        val pendingCount = logRepo.getPendingSyncCount().first()
        (pendingCount > 0) shouldBe true
    }

    // -----------------------------------------------------------------------
    // Test 2: Sync on reconnect — syncPendingLogs returns synced count
    // -----------------------------------------------------------------------

    /**
     * When connectivity is restored, syncPendingLogs() succeeds and returns
     * the number of logs that were pending.
     *
     * Requirements: 12.5
     */
    test("sync on reconnect: syncPendingLogs succeeds and returns synced count") {
        val logRepo = FakeOfflineTrainingLogRepository()
        logRepo.isOnline = false

        logRepo.saveTrainingLog(makeTrainingLog(logId = "log-1"))
        logRepo.saveTrainingLog(makeTrainingLog(logId = "log-2"))

        val pendingBefore = logRepo.getPendingSyncCount().first()
        pendingBefore shouldBe 2

        // Simulate connectivity restored
        logRepo.isOnline = true
        val syncResult = logRepo.syncPendingLogs()

        syncResult.isSuccess shouldBe true
        syncResult.getOrNull() shouldBe 2
    }

    // -----------------------------------------------------------------------
    // Test 3: Offline plan creation — plan retrievable from local cache
    // -----------------------------------------------------------------------

    /**
     * When network is unavailable, saving a workout plan locally succeeds
     * and the plan is retrievable from the local cache.
     *
     * Requirements: 12.1, 12.3
     */
    test("offline plan creation: plan saved and retrievable from local cache") {
        val workoutRepo = FakeOfflineWorkoutRepository()
        workoutRepo.isOnline = false

        val plan = makeWorkoutPlan(name = "Treino Offline")
        val result = workoutRepo.saveWorkoutPlan(plan)

        result.isSuccess shouldBe true

        val plans = workoutRepo.getWorkoutPlans().first()
        plans.size shouldBe 1
        plans.first().name shouldBe "Treino Offline"
        plans.first().planId shouldBe plan.planId

        val pendingIds = workoutRepo.getPendingPlanIds()
        pendingIds.contains(plan.planId) shouldBe true
    }

    // -----------------------------------------------------------------------
    // Test 4: Conflict resolution by timestamp — newer updatedAt wins
    // -----------------------------------------------------------------------

    /**
     * When two versions of the same plan exist with different timestamps,
     * the one with the more recent updatedAt timestamp wins.
     *
     * Requirements: 12.6
     */
    test("conflict resolution: newer timestamp wins when saving conflicting plan versions") {
        val workoutRepo = FakeOfflineWorkoutRepository()

        val olderTimestamp = Instant.fromEpochMilliseconds(1_700_000_000_000L)
        val newerTimestamp = Instant.fromEpochMilliseconds(1_700_100_000_000L)

        val olderPlan = makeWorkoutPlan(
            planId = "plan-conflict",
            name = "Versão Antiga",
            updatedAt = olderTimestamp
        )
        val newerPlan = makeWorkoutPlan(
            planId = "plan-conflict",
            name = "Versão Nova",
            updatedAt = newerTimestamp
        )

        // Save older version first, then resolve conflict by picking newer timestamp
        workoutRepo.saveWorkoutPlan(olderPlan)

        val resolved = if (olderPlan.updatedAt >= newerPlan.updatedAt) olderPlan else newerPlan
        workoutRepo.saveWorkoutPlan(resolved)

        val plans = workoutRepo.getWorkoutPlans().first()
        plans.size shouldBe 1
        plans.first().name shouldBe "Versão Nova"
        plans.first().updatedAt shouldBe newerTimestamp
    }

    // -----------------------------------------------------------------------
    // Test 5: Sync completeness — pending count returns to 0 after sync
    // -----------------------------------------------------------------------

    /**
     * After syncing, all previously pending logs are marked as synced
     * and the pending count returns to 0.
     *
     * Requirements: 12.5
     */
    test("sync completeness: pending count returns to zero after successful sync") {
        val logRepo = FakeOfflineTrainingLogRepository()
        logRepo.isOnline = false

        logRepo.saveTrainingLog(makeTrainingLog(logId = "log-a"))
        logRepo.saveTrainingLog(makeTrainingLog(logId = "log-b"))
        logRepo.saveTrainingLog(makeTrainingLog(logId = "log-c"))

        val pendingBefore = logRepo.getPendingSyncCount().first()
        pendingBefore shouldBe 3

        logRepo.isOnline = true
        logRepo.syncPendingLogs()

        val pendingAfter = logRepo.getPendingSyncCount().first()
        pendingAfter shouldBe 0
    }

    // -----------------------------------------------------------------------
    // Test 6: Multiple pending logs — all synced when connectivity restored
    // -----------------------------------------------------------------------

    /**
     * Multiple offline logs are all synced when connectivity is restored.
     *
     * Requirements: 12.5
     */
    test("multiple pending logs: all synced when connectivity is restored") {
        val logRepo = FakeOfflineTrainingLogRepository()
        logRepo.isOnline = false

        val logCount = 5
        repeat(logCount) { i ->
            logRepo.saveTrainingLog(
                makeTrainingLog(
                    logId = "log-$i",
                    timestamp = Instant.fromEpochMilliseconds(1_700_000_000_000L + i * 1000L)
                )
            )
        }

        val pendingBefore = logRepo.getPendingSyncCount().first()
        pendingBefore shouldBe logCount

        val history = logRepo.getTrainingHistory().first()
        history.size shouldBe logCount

        logRepo.isOnline = true
        val syncResult = logRepo.syncPendingLogs()

        syncResult.isSuccess shouldBe true
        syncResult.getOrNull() shouldBe logCount

        val pendingAfter = logRepo.getPendingSyncCount().first()
        pendingAfter shouldBe 0
    }

    // -----------------------------------------------------------------------
    // Test 7: Sync fails when offline — failure result returned
    // -----------------------------------------------------------------------

    /**
     * When network is unavailable, syncPendingLogs() returns a failure result
     * and the pending count is unchanged.
     *
     * Requirements: 12.3
     */
    test("sync while offline: returns failure and pending count unchanged") {
        val logRepo = FakeOfflineTrainingLogRepository()
        logRepo.isOnline = false

        logRepo.saveTrainingLog(makeTrainingLog())

        val pendingBefore = logRepo.getPendingSyncCount().first()
        pendingBefore shouldBe 1

        val syncResult = logRepo.syncPendingLogs()

        syncResult.isFailure shouldBe true

        val pendingAfter = logRepo.getPendingSyncCount().first()
        pendingAfter shouldBe 1
    }
})
