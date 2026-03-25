package com.vtrainer.app.data.repositories

import com.vtrainer.app.domain.models.ExerciseLog
import com.vtrainer.app.domain.models.PlannedExercise
import com.vtrainer.app.domain.models.SetLog
import com.vtrainer.app.domain.models.TrainingDay
import com.vtrainer.app.domain.models.TrainingLog
import com.vtrainer.app.domain.models.WorkoutPlan
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Instant

// ---------------------------------------------------------------------------
// UserScopedRepository — simulates Firestore per-user data isolation
// ---------------------------------------------------------------------------

/**
 * Simulates Firestore security rules that enforce `request.auth.uid == resource.data.userId`.
 *
 * Each instance is scoped to a single [currentUserId]:
 * - Only returns data belonging to [currentUserId]
 * - Rejects saves where the data's userId differs from [currentUserId]
 * - When [isTokenExpired] is true, all operations fail with a SecurityException
 *
 * Requirements: 17.1, 17.2, 17.3
 */
class UserScopedWorkoutRepository(
    val currentUserId: String,
    private val sharedStore: MutableStateFlow<List<WorkoutPlan>> = MutableStateFlow(emptyList()),
    var isTokenExpired: Boolean = false
) : WorkoutRepository {

    override fun getWorkoutPlans(): Flow<List<WorkoutPlan>> {
        if (isTokenExpired) {
            return MutableStateFlow(emptyList())
        }
        return sharedStore.map { plans -> plans.filter { it.userId == currentUserId } }
    }

    override suspend fun saveWorkoutPlan(plan: WorkoutPlan): Result<Unit> {
        if (isTokenExpired) {
            return Result.failure(SecurityException("Token expired"))
        }
        if (plan.userId != currentUserId) {
            return Result.failure(SecurityException("Permission denied: userId mismatch"))
        }
        val current = sharedStore.value.toMutableList()
        val idx = current.indexOfFirst { it.planId == plan.planId }
        if (idx >= 0) current[idx] = plan else current.add(plan)
        sharedStore.value = current
        return Result.success(Unit)
    }

    override suspend fun deleteWorkoutPlan(planId: String): Result<Unit> {
        if (isTokenExpired) {
            return Result.failure(SecurityException("Token expired"))
        }
        sharedStore.value = sharedStore.value.filter { it.planId != planId }
        return Result.success(Unit)
    }
}

/**
 * Simulates Firestore security rules for training logs scoped to [currentUserId].
 *
 * Requirements: 17.1, 17.2, 17.3
 */
class UserScopedTrainingLogRepository(
    val currentUserId: String,
    private val sharedStore: MutableStateFlow<List<TrainingLog>> = MutableStateFlow(emptyList()),
    var isTokenExpired: Boolean = false
) : TrainingLogRepository {

    override suspend fun saveTrainingLog(log: TrainingLog): Result<Unit> {
        if (isTokenExpired) {
            return Result.failure(SecurityException("Token expired"))
        }
        if (log.userId != currentUserId) {
            return Result.failure(SecurityException("Permission denied: userId mismatch"))
        }
        sharedStore.value = sharedStore.value + log
        return Result.success(Unit)
    }

    override fun getTrainingHistory(): Flow<List<TrainingLog>> {
        if (isTokenExpired) {
            return MutableStateFlow(emptyList())
        }
        return sharedStore.map { logs -> logs.filter { it.userId == currentUserId } }
    }

    override suspend fun syncPendingLogs(): Result<Int> = Result.success(0)

    override fun getPendingSyncCount(): Flow<Int> = MutableStateFlow(0)
}

// ---------------------------------------------------------------------------
// Test helpers
// ---------------------------------------------------------------------------

private val BASE_TIMESTAMP = Instant.fromEpochMilliseconds(1_700_000_000_000L)

private fun makeWorkoutPlan(
    planId: String = "plan-1",
    userId: String = "user-a",
    name: String = "Treino Full Body"
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
    createdAt = BASE_TIMESTAMP,
    updatedAt = BASE_TIMESTAMP
)

private fun makeTrainingLog(
    logId: String = "log-1",
    userId: String = "user-a",
    planId: String? = "plan-1",
    dayName: String = "Dia A"
): TrainingLog = TrainingLog(
    logId = logId,
    userId = userId,
    workoutPlanId = planId,
    workoutDayName = dayName,
    timestamp = BASE_TIMESTAMP,
    origin = "mobile",
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
                    completedAt = BASE_TIMESTAMP
                )
            ),
            totalVolume = 1000,
            isPersonalRecord = false,
            recordType = null
        )
    ),
    totalVolume = 1000
)

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

/**
 * Integration tests for security and data isolation using user-scoped fake repositories.
 *
 * Each [UserScopedWorkoutRepository] / [UserScopedTrainingLogRepository] instance is
 * scoped to a single user, simulating Firestore security rules that enforce
 * `request.auth.uid == resource.data.userId`.
 *
 * Tests cover:
 * - User data isolation for plans (Req 17.3)
 * - User data isolation for logs (Req 17.3)
 * - Cross-user save rejected for plans (Req 17.3)
 * - Cross-user save rejected for logs (Req 17.3)
 * - Token expiration blocks reads (Req 17.1, 17.2)
 * - Token expiration blocks writes (Req 17.1, 17.2)
 * - Each user sees only their own plans (Req 17.3)
 * - Each user sees only their own history (Req 17.3)
 *
 * Requirements: 17.1, 17.2, 17.3
 */
class SecurityDataIsolationIntegrationTest : FunSpec({

    // -----------------------------------------------------------------------
    // Test 1: User data isolation for plans — User A cannot read User B's plans
    // -----------------------------------------------------------------------

    /**
     * User A's repository only returns plans belonging to User A.
     * User B's plans stored in the same backing store are invisible to User A.
     *
     * Requirements: 17.3
     */
    test("user data isolation for plans: User A cannot read User B's workout plans") {
        val sharedStore = MutableStateFlow<List<WorkoutPlan>>(emptyList())

        val repoA = UserScopedWorkoutRepository("user-a", sharedStore)
        val repoB = UserScopedWorkoutRepository("user-b", sharedStore)

        // User B saves a plan
        val planB = makeWorkoutPlan(planId = "plan-b", userId = "user-b", name = "Treino B")
        repoB.saveWorkoutPlan(planB)

        // User A queries plans — should see nothing
        val plansSeenByA = repoA.getWorkoutPlans().first()
        plansSeenByA.size shouldBe 0
        plansSeenByA.none { it.userId == "user-b" } shouldBe true
    }

    // -----------------------------------------------------------------------
    // Test 2: User data isolation for logs — User A cannot read User B's logs
    // -----------------------------------------------------------------------

    /**
     * User A's repository only returns training logs belonging to User A.
     * User B's logs stored in the same backing store are invisible to User A.
     *
     * Requirements: 17.3
     */
    test("user data isolation for logs: User A cannot read User B's training logs") {
        val sharedStore = MutableStateFlow<List<TrainingLog>>(emptyList())

        val repoA = UserScopedTrainingLogRepository("user-a", sharedStore)
        val repoB = UserScopedTrainingLogRepository("user-b", sharedStore)

        // User B saves a log
        val logB = makeTrainingLog(logId = "log-b", userId = "user-b")
        repoB.saveTrainingLog(logB)

        // User A queries history — should see nothing
        val historySeenByA = repoA.getTrainingHistory().first()
        historySeenByA.size shouldBe 0
        historySeenByA.none { it.userId == "user-b" } shouldBe true
    }

    // -----------------------------------------------------------------------
    // Test 3: Cross-user save rejected — plan with different userId is rejected
    // -----------------------------------------------------------------------

    /**
     * Attempting to save a workout plan whose userId differs from the repository's
     * currentUserId is rejected with a SecurityException.
     *
     * Requirements: 17.3
     */
    test("cross-user save rejected: saving a plan with a different userId returns failure") {
        val repoA = UserScopedWorkoutRepository("user-a")

        // Attempt to save a plan that claims to belong to user-b
        val planForB = makeWorkoutPlan(planId = "plan-b", userId = "user-b")
        val result = repoA.saveWorkoutPlan(planForB)

        result.isFailure shouldBe true
        (result.exceptionOrNull() is SecurityException) shouldBe true

        // Verify nothing was stored
        val plans = repoA.getWorkoutPlans().first()
        plans.size shouldBe 0
    }

    // -----------------------------------------------------------------------
    // Test 4: Cross-user log save rejected — log with different userId is rejected
    // -----------------------------------------------------------------------

    /**
     * Attempting to save a training log whose userId differs from the repository's
     * currentUserId is rejected with a SecurityException.
     *
     * Requirements: 17.3
     */
    test("cross-user log save rejected: saving a log with a different userId returns failure") {
        val repoA = UserScopedTrainingLogRepository("user-a")

        // Attempt to save a log that claims to belong to user-b
        val logForB = makeTrainingLog(logId = "log-b", userId = "user-b")
        val result = repoA.saveTrainingLog(logForB)

        result.isFailure shouldBe true
        (result.exceptionOrNull() is SecurityException) shouldBe true

        // Verify nothing was stored
        val history = repoA.getTrainingHistory().first()
        history.size shouldBe 0
    }

    // -----------------------------------------------------------------------
    // Test 5: Token expiration blocks reads — expired token returns empty list
    // -----------------------------------------------------------------------

    /**
     * When the auth token is expired, reading workout plans returns an empty list,
     * simulating Firestore rejecting the request due to invalid credentials.
     *
     * Requirements: 17.1, 17.2
     */
    test("token expiration blocks reads: expired token causes getWorkoutPlans to return empty") {
        val repoA = UserScopedWorkoutRepository("user-a")

        // Save a valid plan while token is valid
        val plan = makeWorkoutPlan(planId = "plan-a", userId = "user-a")
        repoA.saveWorkoutPlan(plan)

        // Confirm plan is visible before expiry
        val plansBefore = repoA.getWorkoutPlans().first()
        plansBefore.size shouldBe 1

        // Expire the token
        repoA.isTokenExpired = true

        // Now reads should return empty (access denied)
        val plansAfter = repoA.getWorkoutPlans().first()
        plansAfter.size shouldBe 0
    }

    // -----------------------------------------------------------------------
    // Test 6: Token expiration blocks writes — expired token causes save to fail
    // -----------------------------------------------------------------------

    /**
     * When the auth token is expired, saving a workout plan returns a failure,
     * simulating Firestore rejecting the write due to invalid credentials.
     *
     * Requirements: 17.1, 17.2
     */
    test("token expiration blocks writes: expired token causes saveWorkoutPlan to return failure") {
        val repoA = UserScopedWorkoutRepository("user-a")
        repoA.isTokenExpired = true

        val plan = makeWorkoutPlan(planId = "plan-a", userId = "user-a")
        val result = repoA.saveWorkoutPlan(plan)

        result.isFailure shouldBe true
        val exception = result.exceptionOrNull()
        (exception is SecurityException) shouldBe true
        exception?.message shouldBe "Token expired"
    }

    // -----------------------------------------------------------------------
    // Test 7: Each user sees only their own plans
    // -----------------------------------------------------------------------

    /**
     * When two users each save their own plans to a shared backing store,
     * each user's repository only returns their own plans.
     *
     * Requirements: 17.3
     */
    test("each user sees only their own plans: two users save plans and each sees only theirs") {
        val sharedStore = MutableStateFlow<List<WorkoutPlan>>(emptyList())

        val repoA = UserScopedWorkoutRepository("user-a", sharedStore)
        val repoB = UserScopedWorkoutRepository("user-b", sharedStore)

        val planA1 = makeWorkoutPlan(planId = "plan-a1", userId = "user-a", name = "Treino A1")
        val planA2 = makeWorkoutPlan(planId = "plan-a2", userId = "user-a", name = "Treino A2")
        val planB1 = makeWorkoutPlan(planId = "plan-b1", userId = "user-b", name = "Treino B1")

        repoA.saveWorkoutPlan(planA1)
        repoA.saveWorkoutPlan(planA2)
        repoB.saveWorkoutPlan(planB1)

        val plansA = repoA.getWorkoutPlans().first()
        val plansB = repoB.getWorkoutPlans().first()

        // User A sees exactly their 2 plans
        plansA.size shouldBe 2
        plansA.all { it.userId == "user-a" } shouldBe true
        plansA.none { it.userId == "user-b" } shouldBe true

        // User B sees exactly their 1 plan
        plansB.size shouldBe 1
        plansB.all { it.userId == "user-b" } shouldBe true
        plansB.none { it.userId == "user-a" } shouldBe true
    }

    // -----------------------------------------------------------------------
    // Test 8: Each user sees only their own history
    // -----------------------------------------------------------------------

    /**
     * When two users each save their own training logs to a shared backing store,
     * each user's repository only returns their own logs.
     *
     * Requirements: 17.3
     */
    test("each user sees only their own history: two users save logs and each sees only theirs") {
        val sharedStore = MutableStateFlow<List<TrainingLog>>(emptyList())

        val repoA = UserScopedTrainingLogRepository("user-a", sharedStore)
        val repoB = UserScopedTrainingLogRepository("user-b", sharedStore)

        val logA1 = makeTrainingLog(logId = "log-a1", userId = "user-a")
        val logA2 = makeTrainingLog(logId = "log-a2", userId = "user-a")
        val logB1 = makeTrainingLog(logId = "log-b1", userId = "user-b")

        repoA.saveTrainingLog(logA1)
        repoA.saveTrainingLog(logA2)
        repoB.saveTrainingLog(logB1)

        val historyA = repoA.getTrainingHistory().first()
        val historyB = repoB.getTrainingHistory().first()

        // User A sees exactly their 2 logs
        historyA.size shouldBe 2
        historyA.all { it.userId == "user-a" } shouldBe true
        historyA.none { it.userId == "user-b" } shouldBe true

        // User B sees exactly their 1 log
        historyB.size shouldBe 1
        historyB.all { it.userId == "user-b" } shouldBe true
        historyB.none { it.userId == "user-a" } shouldBe true
    }
})
