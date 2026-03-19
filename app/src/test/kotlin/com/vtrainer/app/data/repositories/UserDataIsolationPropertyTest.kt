package com.vtrainer.app.data.repositories

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.vtrainer.app.data.local.dao.TrainingLogDao
import com.vtrainer.app.data.local.dao.WorkoutPlanDao
import com.vtrainer.app.data.local.entities.TrainingLogEntity
import com.vtrainer.app.data.local.entities.WorkoutPlanEntity
import com.vtrainer.app.domain.models.SyncStatus
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import io.mockk.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.Clock
import java.util.UUID

/**
 * Property-based test for user data isolation.
 *
 * **Validates: Requirements 17.3**
 *
 * Property 21: User Data Isolation
 *
 * For any two distinct user IDs (userA, userB), data saved by userA must never be
 * accessible when querying as userB. The repository must always filter data by the
 * authenticated user's UID.
 *
 * This property ensures:
 * 1. Training logs saved by userA are never returned when querying as userB
 * 2. Workout plans saved by userA are never returned when querying as userB
 * 3. The DAO is always called with the authenticated user's UID, never another user's UID
 * 4. Data saved by a user is tagged with their UID, not any other user's UID
 */
class UserDataIsolationPropertyTest : FunSpec({

    test("Feature: v-trainer, Property 21: User Data Isolation - training logs of userA are not accessible by userB").config(
        invocations = 1
    ) {
        checkAll(1, Arb.isolationUserId(), Arb.isolationUserId()) { userIdA, userIdB ->
            // Only test when the two user IDs are distinct
            if (userIdA == userIdB) return@checkAll

            // Arrange: userB is authenticated
            val mockAuth = mockk<FirebaseAuth>(relaxed = true)
            val mockUser = mockk<FirebaseUser>(relaxed = true)
            val mockDao = mockk<TrainingLogDao>(relaxed = true)
            val mockFirestore = mockk<com.google.firebase.firestore.FirebaseFirestore>(relaxed = true)

            every { mockAuth.currentUser } returns mockUser
            every { mockUser.uid } returns userIdB

            // Simulate DAO returning only logs belonging to userB (correct isolation)
            val userBLogs = listOf(
                TrainingLogEntity(
                    logId = UUID.randomUUID().toString(),
                    userId = userIdB,
                    workoutPlanId = null,
                    workoutDayName = "Treino B",
                    timestamp = Clock.System.now().toEpochMilliseconds(),
                    origin = "Galaxy_Watch_5",
                    exercisesJson = "[]",
                    totalVolume = 2000,
                    totalCalories = null,
                    duration = 3600,
                    syncStatus = SyncStatus.SYNCED,
                    lastSyncAttempt = null
                )
            )
            // userA's logs exist in the DAO but should never be returned for userB
            val userALogs = listOf(
                TrainingLogEntity(
                    logId = UUID.randomUUID().toString(),
                    userId = userIdA,
                    workoutPlanId = null,
                    workoutDayName = "Treino A",
                    timestamp = Clock.System.now().toEpochMilliseconds(),
                    origin = "Samsung_Galaxy_S23",
                    exercisesJson = "[]",
                    totalVolume = 1500,
                    totalCalories = null,
                    duration = 2700,
                    syncStatus = SyncStatus.SYNCED,
                    lastSyncAttempt = null
                )
            )

            // DAO correctly filters by userId — userA's data is never returned for userB
            coEvery { mockDao.getTrainingLogs(userIdB) } returns flowOf(userBLogs)
            coEvery { mockDao.getTrainingLogs(userIdA) } returns flowOf(userALogs)

            val repository = TrainingLogRepositoryImpl(mockFirestore, mockAuth, mockDao)

            // Act: Query training history as userB
            val history = repository.getTrainingHistory().first()

            // Assert: All returned logs belong to userB, not userA
            history.forEach { log ->
                log.userId shouldBe userIdB
                (log.userId == userIdA) shouldBe false
            }

            // Assert: DAO was queried with userB's UID only
            coVerify { mockDao.getTrainingLogs(userIdB) }
            coVerify(exactly = 0) { mockDao.getTrainingLogs(userIdA) }
        }
    }

    test("Feature: v-trainer, Property 21: User Data Isolation - saving training log tags it with authenticated user's UID").config(
        invocations = 1
    ) {
        checkAll(1, Arb.isolationUserId(), Arb.isolationUserId()) { authenticatedUserId, otherUserId ->
            // Only test when the two user IDs are distinct
            if (authenticatedUserId == otherUserId) return@checkAll

            // Arrange: authenticatedUserId is logged in
            val mockAuth = mockk<FirebaseAuth>(relaxed = true)
            val mockUser = mockk<FirebaseUser>(relaxed = true)
            val mockDao = mockk<TrainingLogDao>(relaxed = true)
            val mockFirestore = mockk<com.google.firebase.firestore.FirebaseFirestore>(relaxed = true)

            every { mockAuth.currentUser } returns mockUser
            every { mockUser.uid } returns authenticatedUserId

            every { mockFirestore.collection(any()) } returns mockk(relaxed = true) {
                every { document(any()) } returns mockk(relaxed = true) {
                    every { set(any()) } returns com.google.android.gms.tasks.Tasks.forResult(null)
                }
            }

            coEvery { mockDao.insert(any()) } just Runs
            coEvery { mockDao.updateSyncStatus(any(), any(), any()) } just Runs

            val repository = TrainingLogRepositoryImpl(mockFirestore, mockAuth, mockDao)

            // Create a log that claims to belong to otherUserId (attempt to save under wrong user)
            val logWithWrongUserId = Arb.authTrainingLog().sample(io.kotest.property.RandomSource.default()).value
                .copy(userId = otherUserId)

            // Act: Save the log — repository must override userId with authenticated user's UID
            val result = repository.saveTrainingLog(logWithWrongUserId)

            // Assert: Save succeeded
            result.isSuccess shouldBe true

            // Assert: The entity inserted into DAO has the authenticated user's UID, not otherUserId
            val insertedEntitySlot = slot<com.vtrainer.app.data.local.entities.TrainingLogEntity>()
            coVerify { mockDao.insert(capture(insertedEntitySlot)) }
            insertedEntitySlot.captured.userId shouldBe authenticatedUserId
            (insertedEntitySlot.captured.userId == otherUserId) shouldBe false
        }
    }

    test("Feature: v-trainer, Property 21: User Data Isolation - workout plans of userA are not accessible by userB").config(
        invocations = 1
    ) {
        checkAll(1, Arb.isolationUserId(), Arb.isolationUserId()) { userIdA, userIdB ->
            // Only test when the two user IDs are distinct
            if (userIdA == userIdB) return@checkAll

            // Arrange: userB is authenticated
            val mockAuth = mockk<FirebaseAuth>(relaxed = true)
            val mockUser = mockk<FirebaseUser>(relaxed = true)
            val mockDao = mockk<WorkoutPlanDao>(relaxed = true)
            val mockFirestore = mockk<com.google.firebase.firestore.FirebaseFirestore>(relaxed = true)

            every { mockAuth.currentUser } returns mockUser
            every { mockUser.uid } returns userIdB

            // Simulate DAO returning only plans belonging to userB (correct isolation)
            val userBPlans = listOf(
                WorkoutPlanEntity(
                    planId = UUID.randomUUID().toString(),
                    userId = userIdB,
                    name = "Plano B",
                    description = null,
                    trainingDaysJson = "[]",
                    createdAt = Clock.System.now().toEpochMilliseconds(),
                    updatedAt = Clock.System.now().toEpochMilliseconds(),
                    syncStatus = SyncStatus.SYNCED,
                    lastSyncAttempt = null
                )
            )

            // DAO correctly filters by userId — userA's plans are never returned for userB
            coEvery { mockDao.getWorkoutPlans(userIdB) } returns flowOf(userBPlans)
            coEvery { mockDao.getWorkoutPlans(userIdA) } returns flowOf(emptyList())

            // Set up Firestore listener mock (returns no documents)
            val mockCollectionRef = mockk<com.google.firebase.firestore.CollectionReference>(relaxed = true)
            val mockQuery = mockk<com.google.firebase.firestore.Query>(relaxed = true)
            val mockListenerReg = mockk<com.google.firebase.firestore.ListenerRegistration>(relaxed = true)

            every { mockFirestore.collection(any()) } returns mockCollectionRef
            every { mockCollectionRef.whereEqualTo(any<String>(), any()) } returns mockQuery
            every { mockQuery.addSnapshotListener(any()) } returns mockListenerReg

            val repository = WorkoutRepositoryImpl(mockFirestore, mockAuth, mockDao)

            // Act: Query workout plans as userB
            val plans = repository.getWorkoutPlans().first()

            // Assert: All returned plans belong to userB, not userA
            plans.forEach { plan ->
                plan.userId shouldBe userIdB
                (plan.userId == userIdA) shouldBe false
            }

            // Assert: DAO was queried with userB's UID only
            coVerify { mockDao.getWorkoutPlans(userIdB) }
            coVerify(exactly = 0) { mockDao.getWorkoutPlans(userIdA) }
        }
    }

    test("Feature: v-trainer, Property 21: User Data Isolation - saving workout plan tags it with authenticated user's UID").config(
        invocations = 1
    ) {
        checkAll(1, Arb.isolationUserId(), Arb.isolationUserId()) { authenticatedUserId, otherUserId ->
            // Only test when the two user IDs are distinct
            if (authenticatedUserId == otherUserId) return@checkAll

            // Arrange: authenticatedUserId is logged in
            val mockAuth = mockk<FirebaseAuth>(relaxed = true)
            val mockUser = mockk<FirebaseUser>(relaxed = true)
            val mockDao = mockk<WorkoutPlanDao>(relaxed = true)
            val mockFirestore = mockk<com.google.firebase.firestore.FirebaseFirestore>(relaxed = true)

            every { mockAuth.currentUser } returns mockUser
            every { mockUser.uid } returns authenticatedUserId

            every { mockFirestore.collection(any()) } returns mockk(relaxed = true) {
                every { document(any()) } returns mockk(relaxed = true) {
                    every { set(any()) } returns com.google.android.gms.tasks.Tasks.forResult(null)
                }
            }

            coEvery { mockDao.insert(any()) } just Runs
            coEvery { mockDao.updateSyncStatus(any(), any(), any()) } just Runs

            val repository = WorkoutRepositoryImpl(mockFirestore, mockAuth, mockDao)

            // Create a plan that claims to belong to otherUserId (attempt to save under wrong user)
            val planWithWrongUserId = Arb.authWorkoutPlan().sample(io.kotest.property.RandomSource.default()).value
                .copy(userId = otherUserId)

            // Act: Save the plan — repository must override userId with authenticated user's UID
            val result = repository.saveWorkoutPlan(planWithWrongUserId)

            // Assert: Save succeeded
            result.isSuccess shouldBe true

            // Assert: The entity inserted into DAO has the authenticated user's UID, not otherUserId
            val insertedEntitySlot = slot<WorkoutPlanEntity>()
            coVerify { mockDao.insert(capture(insertedEntitySlot)) }
            insertedEntitySlot.captured.userId shouldBe authenticatedUserId
            (insertedEntitySlot.captured.userId == otherUserId) shouldBe false
        }
    }
})

/**
 * Custom Arbitrary generator for valid Firebase user IDs used in isolation tests.
 * Firebase UIDs are alphanumeric strings of 28 characters.
 */
fun Arb.Companion.isolationUserId(): Arb<String> = arbitrary {
    UUID.randomUUID().toString().replace("-", "").take(28)
}
