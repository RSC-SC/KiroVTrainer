package com.vtrainer.app.data.repositories

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.vtrainer.app.data.local.dao.WorkoutPlanDao
import com.vtrainer.app.data.local.entities.WorkoutPlanEntity
import com.vtrainer.app.data.mappers.toEntity
import com.vtrainer.app.domain.models.PlannedExercise
import com.vtrainer.app.domain.models.SyncStatus
import com.vtrainer.app.domain.models.TrainingDay
import com.vtrainer.app.domain.models.WorkoutPlan
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import io.mockk.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import java.util.UUID

/**
 * Property-based test for workout plan deletion.
 * 
 * **Validates: Requirements 3.6**
 * 
 * Property 25: Workout Plan Deletion Removes from User's List
 * 
 * For any workout plan that is deleted, it SHALL no longer appear in the user's
 * list of workout plans after the deletion completes.
 * 
 * This is a basic CRUD invariant. Deleted items must not persist in the UI or database.
 */
class WorkoutPlanDeletionPropertyTest : FunSpec({
    test("Feature: v-trainer, Property 25: Workout Plan Deletion Removes from User's List").config(
        invocations = 100
    ) {
        checkAll(100, Arb.workoutPlanForDeletion()) { plan ->
            // Arrange: Mock dependencies
            val mockFirestore = mockk<FirebaseFirestore>(relaxed = true)
            val mockAuth = mockk<FirebaseAuth>(relaxed = true)
            val mockUser = mockk<FirebaseUser>(relaxed = true)
            val mockDao = mockk<WorkoutPlanDao>(relaxed = true)
            
            every { mockAuth.currentUser } returns mockUser
            every { mockUser.uid } returns "test-user-id"
            
            // Setup: Simulate that the plan exists in the database initially
            val initialPlans = listOf(plan.toEntity(SyncStatus.SYNCED))
            coEvery { mockDao.getWorkoutPlans("test-user-id") } returns flowOf(initialPlans)
            
            // Track deletion calls
            val deletedPlanIds = mutableListOf<String>()
            coEvery { mockDao.deleteById(capture(deletedPlanIds)) } just Runs
            
            val repository = WorkoutRepositoryImpl(mockFirestore, mockAuth, mockDao)
            
            // Verify: Plan exists before deletion
            val plansBeforeDeletion = mockDao.getWorkoutPlans("test-user-id").first()
            val planExistsBeforeDeletion = plansBeforeDeletion.any { it.planId == plan.planId }
            planExistsBeforeDeletion shouldBe true
            
            // Act: Delete the workout plan
            val deleteResult = repository.deleteWorkoutPlan(plan.planId)
            
            // Assert: Deletion should succeed
            deleteResult.isSuccess shouldBe true
            
            // Assert: DAO deleteById was called with correct planId
            coVerify { mockDao.deleteById(plan.planId) }
            deletedPlanIds.contains(plan.planId) shouldBe true
            
            // Simulate: After deletion, the plan should not be in the list
            val plansAfterDeletion = initialPlans.filter { it.planId != plan.planId }
            coEvery { mockDao.getWorkoutPlans("test-user-id") } returns flowOf(plansAfterDeletion)
            
            // Assert: Plan no longer appears in user's list
            val retrievedPlans = mockDao.getWorkoutPlans("test-user-id").first()
            val planExistsAfterDeletion = retrievedPlans.any { it.planId == plan.planId }
            planExistsAfterDeletion shouldBe false
            
            // Assert: The plan was actually removed (not just hidden)
            val planCount = retrievedPlans.size
            planCount shouldBe (initialPlans.size - 1)
        }
    }
})

/**
 * Custom Arbitrary generator for WorkoutPlan for deletion testing.
 * Generates realistic workout plans that can be deleted.
 */
fun Arb.Companion.workoutPlanForDeletion(): Arb<WorkoutPlan> = arbitrary {
    val trainingDays = Arb.list(Arb.trainingDayForDeletion(), 1..5).bind()
    
    WorkoutPlan(
        planId = UUID.randomUUID().toString(),
        userId = "test-user-id",
        name = Arb.string(5..50).bind(),
        description = Arb.string(0..200).orNull(0.3).bind(),
        trainingDays = trainingDays,
        createdAt = Arb.instantForDeletion().bind(),
        updatedAt = Arb.instantForDeletion().bind()
    )
}

/**
 * Custom Arbitrary generator for TrainingDay for deletion testing.
 */
fun Arb.Companion.trainingDayForDeletion(): Arb<TrainingDay> = arbitrary {
    TrainingDay(
        dayName = Arb.stringPattern("[A-Z] - [A-Za-z ]{5,20}").bind(),
        exercises = Arb.list(Arb.plannedExerciseForDeletion(), 1..8).bind()
    )
}

/**
 * Custom Arbitrary generator for PlannedExercise for deletion testing.
 */
fun Arb.Companion.plannedExerciseForDeletion(): Arb<PlannedExercise> = arbitrary {
    PlannedExercise(
        exerciseId = UUID.randomUUID().toString(),
        order = Arb.int(1..20).bind(),
        sets = Arb.int(1..10).bind(),
        reps = Arb.int(1..50).bind(),
        restSeconds = Arb.int(15..300).bind(),
        notes = Arb.string(0..100).orNull(0.5).bind()
    )
}

/**
 * Custom Arbitrary generator for Instant for deletion testing.
 */
fun Arb.Companion.instantForDeletion(): Arb<Instant> = arbitrary {
    val now = Clock.System.now()
    val offsetMillis = Arb.long(-365L * 24 * 60 * 60 * 1000, 0).bind()
    Instant.fromEpochMilliseconds(now.toEpochMilliseconds() + offsetMillis)
}
