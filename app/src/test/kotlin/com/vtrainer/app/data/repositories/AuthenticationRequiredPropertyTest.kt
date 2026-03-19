package com.vtrainer.app.data.repositories

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.vtrainer.app.data.local.dao.TrainingLogDao
import com.vtrainer.app.data.local.dao.WorkoutPlanDao
import com.vtrainer.app.domain.models.*
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.property.Arb
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import io.mockk.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import java.util.UUID

/**
 * Property-based test for authentication requirement.
 *
 * **Validates: Requirements 6.2, 17.1, 17.2**
 *
 * Property 20: Authentication Required for Data Access
 *
 * For any API request to Cloud Functions or Firestore query, if the request lacks
 * a valid Firebase Auth token, the system SHALL reject it with an authentication error.
 *
 * This property ensures:
 * 1. Any attempt to access training logs without authentication is rejected
 * 2. Any attempt to access workout plans without authentication is rejected
 * 3. Any attempt to sync workout data without a valid auth token fails
 * 4. Authenticated requests with valid user IDs are permitted to access only their own data
 */
class AuthenticationRequiredPropertyTest : FunSpec({

    test("Feature: v-trainer, Property 20: Authentication Required - unauthenticated saveTrainingLog is rejected").config(
        invocations = 1
    ) {
        checkAll(1, Arb.authTrainingLog()) { log ->
            // Arrange: FirebaseAuth with NO current user (unauthenticated)
            val mockAuth = mockk<FirebaseAuth>(relaxed = true)
            val mockDao = mockk<TrainingLogDao>(relaxed = true)
            val mockFirestore = mockk<com.google.firebase.firestore.FirebaseFirestore>(relaxed = true)

            every { mockAuth.currentUser } returns null

            val repository = TrainingLogRepositoryImpl(mockFirestore, mockAuth, mockDao)

            // Act: Attempt to save a training log without authentication
            val result = repository.saveTrainingLog(log)

            // Assert: The operation must fail with an authentication error
            result.isFailure shouldBe true
            val exception = result.exceptionOrNull()
            exception shouldBe (exception as? IllegalStateException)
            exception?.message?.shouldContain("authenticated")

            // Assert: DAO must never be called (no data access without auth)
            coVerify(exactly = 0) { mockDao.insert(any()) }
        }
    }

    test("Feature: v-trainer, Property 20: Authentication Required - unauthenticated getTrainingHistory is rejected").config(
        invocations = 1
    ) {
        checkAll(1, Arb.string(1..36)) { _ ->
            // Arrange: FirebaseAuth with NO current user (unauthenticated)
            val mockAuth = mockk<FirebaseAuth>(relaxed = true)
            val mockDao = mockk<TrainingLogDao>(relaxed = true)
            val mockFirestore = mockk<com.google.firebase.firestore.FirebaseFirestore>(relaxed = true)

            every { mockAuth.currentUser } returns null

            val repository = TrainingLogRepositoryImpl(mockFirestore, mockAuth, mockDao)

            // Act & Assert: Accessing training history without auth must throw
            shouldThrow<IllegalStateException> {
                repository.getTrainingHistory().first()
            }

            // Assert: DAO must never be queried (no data access without auth)
            coVerify(exactly = 0) { mockDao.getTrainingLogs(any()) }
        }
    }

    test("Feature: v-trainer, Property 20: Authentication Required - unauthenticated syncPendingLogs is rejected").config(
        invocations = 1
    ) {
        checkAll(1, Arb.string(1..36)) { _ ->
            // Arrange: FirebaseAuth with NO current user (unauthenticated)
            val mockAuth = mockk<FirebaseAuth>(relaxed = true)
            val mockDao = mockk<TrainingLogDao>(relaxed = true)
            val mockFirestore = mockk<com.google.firebase.firestore.FirebaseFirestore>(relaxed = true)

            every { mockAuth.currentUser } returns null

            val repository = TrainingLogRepositoryImpl(mockFirestore, mockAuth, mockDao)

            // Act: Attempt to sync pending logs without authentication
            val result = repository.syncPendingLogs()

            // Assert: The operation must fail
            result.isFailure shouldBe true
            val exception = result.exceptionOrNull()
            exception shouldBe (exception as? IllegalStateException)
            exception?.message?.shouldContain("authenticated")

            // Assert: DAO must never be queried for pending logs
            coVerify(exactly = 0) { mockDao.getPendingSyncLogs(any(), any()) }
        }
    }

    test("Feature: v-trainer, Property 20: Authentication Required - unauthenticated saveWorkoutPlan is rejected").config(
        invocations = 1
    ) {
        checkAll(1, Arb.authWorkoutPlan()) { plan ->
            // Arrange: FirebaseAuth with NO current user (unauthenticated)
            val mockAuth = mockk<FirebaseAuth>(relaxed = true)
            val mockDao = mockk<WorkoutPlanDao>(relaxed = true)
            val mockFirestore = mockk<com.google.firebase.firestore.FirebaseFirestore>(relaxed = true)

            every { mockAuth.currentUser } returns null

            val repository = WorkoutRepositoryImpl(mockFirestore, mockAuth, mockDao)

            // Act: Attempt to save a workout plan without authentication
            val result = repository.saveWorkoutPlan(plan)

            // Assert: The operation must fail with an authentication error
            result.isFailure shouldBe true
            val exception = result.exceptionOrNull()
            exception shouldBe (exception as? IllegalStateException)
            exception?.message?.shouldContain("authenticated")

            // Assert: DAO must never be called (no data access without auth)
            coVerify(exactly = 0) { mockDao.insert(any()) }
        }
    }

    test("Feature: v-trainer, Property 20: Authentication Required - authenticated requests with valid userId are permitted").config(
        invocations = 1
    ) {
        checkAll(1, Arb.authUserId(), Arb.authTrainingLog()) { userId, log ->
            // Arrange: FirebaseAuth WITH a valid current user (authenticated)
            val mockAuth = mockk<FirebaseAuth>(relaxed = true)
            val mockUser = mockk<FirebaseUser>(relaxed = true)
            val mockDao = mockk<TrainingLogDao>(relaxed = true)
            val mockFirestore = mockk<com.google.firebase.firestore.FirebaseFirestore>(relaxed = true)
            val mockDocRef = mockk<com.google.firebase.firestore.DocumentReference>(relaxed = true)

            every { mockAuth.currentUser } returns mockUser
            every { mockUser.uid } returns userId

            every { mockFirestore.collection(any()) } returns mockk(relaxed = true) {
                every { document(any()) } returns mockDocRef
            }
            every { mockDocRef.set(any()) } returns com.google.android.gms.tasks.Tasks.forResult(null)

            coEvery { mockDao.insert(any()) } just Runs
            coEvery { mockDao.updateSyncStatus(any(), any(), any()) } just Runs

            val repository = TrainingLogRepositoryImpl(mockFirestore, mockAuth, mockDao)

            // Act: Save a training log with valid authentication
            val result = repository.saveTrainingLog(log)

            // Assert: The operation must succeed for authenticated users
            result.isSuccess shouldBe true

            // Assert: DAO was called — data access was permitted
            coVerify(atLeast = 1) { mockDao.insert(any()) }
        }
    }

    test("Feature: v-trainer, Property 20: Authentication Required - authenticated user can only access their own data").config(
        invocations = 1
    ) {
        checkAll(1, Arb.authUserId(), Arb.authUserId()) { userIdA, userIdB ->
            // Only test when the two user IDs are different
            if (userIdA == userIdB) return@checkAll

            // Arrange: User A is authenticated
            val mockAuth = mockk<FirebaseAuth>(relaxed = true)
            val mockUser = mockk<FirebaseUser>(relaxed = true)
            val mockDao = mockk<TrainingLogDao>(relaxed = true)
            val mockFirestore = mockk<com.google.firebase.firestore.FirebaseFirestore>(relaxed = true)

            every { mockAuth.currentUser } returns mockUser
            every { mockUser.uid } returns userIdA

            // Simulate DAO returning only logs belonging to userIdA
            val userALogs = listOf(
                com.vtrainer.app.data.local.entities.TrainingLogEntity(
                    logId = UUID.randomUUID().toString(),
                    userId = userIdA,
                    workoutPlanId = null,
                    workoutDayName = "Treino A",
                    timestamp = Clock.System.now().toEpochMilliseconds(),
                    origin = "Galaxy_Watch_4",
                    exercisesJson = "[]",
                    totalVolume = 1000,
                    totalCalories = null,
                    duration = 3600,
                    syncStatus = SyncStatus.SYNCED,
                    lastSyncAttempt = null
                )
            )
            coEvery { mockDao.getTrainingLogs(userIdA) } returns flowOf(userALogs)

            val repository = TrainingLogRepositoryImpl(mockFirestore, mockAuth, mockDao)

            // Act: Get training history for authenticated user A
            val history = repository.getTrainingHistory().first()

            // Assert: All returned logs belong to userIdA, not userIdB
            history.forEach { log ->
                log.userId shouldBe userIdA
                (log.userId == userIdB) shouldBe false
            }

            // Assert: DAO was queried with userIdA's UID (not userIdB's)
            coVerify { mockDao.getTrainingLogs(userIdA) }
            coVerify(exactly = 0) { mockDao.getTrainingLogs(userIdB) }
        }
    }
})

/**
 * Custom Arbitrary generator for valid Firebase user IDs.
 * Firebase UIDs are alphanumeric strings of 28 characters.
 */
fun Arb.Companion.authUserId(): Arb<String> = arbitrary {
    UUID.randomUUID().toString().replace("-", "").take(28)
}

/**
 * Custom Arbitrary generator for TrainingLog used in authentication tests.
 */
fun Arb.Companion.authTrainingLog(): Arb<TrainingLog> = arbitrary {
    val now = Clock.System.now()
    val offsetMillis = Arb.long(-30L * 24 * 60 * 60 * 1000, 0).bind()
    val timestamp = Instant.fromEpochMilliseconds(now.toEpochMilliseconds() + offsetMillis)

    TrainingLog(
        logId = UUID.randomUUID().toString(),
        userId = Arb.authUserId().bind(),
        workoutPlanId = Arb.string(10..36).orNull(0.3).bind(),
        workoutDayName = Arb.stringPattern("Treino [A-Z] - [A-Za-z ]{5,20}").bind(),
        timestamp = timestamp,
        origin = Arb.choice(
            Arb.constant("Galaxy_Watch_4"),
            Arb.constant("Galaxy_Watch_5"),
            Arb.constant("Samsung_Galaxy_S23")
        ).bind(),
        duration = Arb.int(600..7200).bind(),
        totalCalories = Arb.int(100..800).orNull(0.2).bind(),
        exercises = Arb.list(Arb.authExerciseLog(), 1..5).bind(),
        totalVolume = Arb.int(1000..10000).bind()
    )
}

/**
 * Custom Arbitrary generator for ExerciseLog used in authentication tests.
 */
fun Arb.Companion.authExerciseLog(): Arb<ExerciseLog> = arbitrary {
    val sets = Arb.list(Arb.authSetLog(), 1..4).bind()
    val totalVolume = sets.sumOf { (it.weight * it.reps).toInt() }

    ExerciseLog(
        exerciseId = UUID.randomUUID().toString(),
        sets = sets,
        totalVolume = totalVolume,
        isPersonalRecord = false,
        recordType = null
    )
}

/**
 * Custom Arbitrary generator for SetLog used in authentication tests.
 */
fun Arb.Companion.authSetLog(): Arb<SetLog> = arbitrary {
    val now = Clock.System.now()
    val offsetMillis = Arb.long(-30L * 24 * 60 * 60 * 1000, 0).bind()

    SetLog(
        setNumber = Arb.int(1..5).bind(),
        reps = Arb.int(1..20).bind(),
        weight = Arb.double(5.0..200.0).bind(),
        restSeconds = Arb.int(30..180).bind(),
        heartRate = Arb.int(100..180).orNull(0.3).bind(),
        completedAt = Instant.fromEpochMilliseconds(now.toEpochMilliseconds() + offsetMillis)
    )
}

/**
 * Custom Arbitrary generator for WorkoutPlan used in authentication tests.
 */
fun Arb.Companion.authWorkoutPlan(): Arb<WorkoutPlan> = arbitrary {
    val now = Clock.System.now()

    WorkoutPlan(
        planId = UUID.randomUUID().toString(),
        userId = Arb.authUserId().bind(),
        name = Arb.string(3..30).filter { it.isNotBlank() }.bind(),
        description = Arb.string(5..100).orNull(0.4).bind(),
        trainingDays = Arb.list(Arb.authTrainingDay(), 1..5).bind(),
        createdAt = now,
        updatedAt = now
    )
}

/**
 * Custom Arbitrary generator for TrainingDay used in authentication tests.
 */
fun Arb.Companion.authTrainingDay(): Arb<TrainingDay> = arbitrary {
    TrainingDay(
        dayName = Arb.stringPattern("Treino [A-Z]").bind(),
        exercises = Arb.list(Arb.authPlannedExercise(), 1..6).bind()
    )
}

/**
 * Custom Arbitrary generator for PlannedExercise used in authentication tests.
 */
fun Arb.Companion.authPlannedExercise(): Arb<PlannedExercise> = arbitrary {
    PlannedExercise(
        exerciseId = UUID.randomUUID().toString(),
        order = Arb.int(1..10).bind(),
        sets = Arb.int(1..5).bind(),
        reps = Arb.int(1..20).bind(),
        restSeconds = Arb.int(30..180).bind(),
        notes = Arb.string(5..50).orNull(0.5).bind()
    )
}
