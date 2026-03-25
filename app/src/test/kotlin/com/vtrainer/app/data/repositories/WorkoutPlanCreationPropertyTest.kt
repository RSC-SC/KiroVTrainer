package com.vtrainer.app.data.repositories

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.vtrainer.app.data.local.dao.WorkoutPlanDao
import com.vtrainer.app.data.local.entities.WorkoutPlanEntity
import com.vtrainer.app.data.mappers.toDomain
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
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import java.util.UUID

/**
 * Property-based test for workout plan creation preserving exercise count.
 * 
 * **Validates: Requirements 3.1, 3.2, 3.4**
 * 
 * Property 11: Workout Plan Creation Preserves Exercise Count
 * 
 * For any workout plan created with N exercises across all training days,
 * the saved plan SHALL contain exactly N exercises when retrieved.
 * 
 * This property ensures that creating and saving a plan doesn't lose exercises,
 * which would corrupt workout plans.
 */
class WorkoutPlanCreationPropertyTest : FunSpec({
    test("Feature: v-trainer, Property 11: Workout Plan Creation Preserves Exercise Count").config(
        invocations = 1
    ) {
        checkAll(1, Arb.workoutPlanWithExercises()) { plan ->
            // Arrange: Mock dependencies
            val mockFirestore = mockk<FirebaseFirestore>(relaxed = true)
            val mockAuth = mockk<FirebaseAuth>(relaxed = true)
            val mockUser = mockk<FirebaseUser>(relaxed = true)
            val mockDao = mockk<WorkoutPlanDao>(relaxed = true)
            
            every { mockAuth.currentUser } returns mockUser
            every { mockUser.uid } returns "test-user-id"
            
            // Capture the entity that gets saved
            val savedEntities = mutableListOf<WorkoutPlanEntity>()
            coEvery { mockDao.insert(capture(savedEntities)) } just Runs
            coEvery { mockDao.updateSyncStatus(any(), any(), any()) } just Runs
            
            val repository = WorkoutRepositoryImpl(mockFirestore, mockAuth, mockDao)
            
            // Act: Save the workout plan
            val saveResult = repository.saveWorkoutPlan(plan)
            
            // Assert: Save should succeed
            saveResult.isSuccess shouldBe true
            
            // Assert: DAO insert was called
            coVerify { mockDao.insert(any()) }
            
            // Calculate expected exercise count from original plan
            val originalExerciseCount = plan.trainingDays.sumOf { it.exercises.size }
            
            // Calculate exercise count from saved entity
            val savedEntity = savedEntities.first()
            val savedPlan = savedEntity.toDomain()
            val savedExerciseCount = savedPlan.trainingDays.sumOf { it.exercises.size }
            
            // Assert: Exercise count must be preserved
            savedExerciseCount shouldBe originalExerciseCount
        }
    }
})

/**
 * Custom Arbitrary generator for WorkoutPlan with exercises.
 * Generates workout plans with 1-7 training days, each with 1-10 exercises.
 */
fun Arb.Companion.workoutPlanWithExercises(): Arb<WorkoutPlan> = arbitrary {
    val trainingDays = Arb.list(Arb.trainingDay(), 1..7).bind()
    
    WorkoutPlan(
        planId = UUID.randomUUID().toString(),
        userId = "test-user-id",
        name = Arb.string(5..50).bind(),
        description = Arb.string(0..200).orNull(0.3).bind(),
        trainingDays = trainingDays,
        createdAt = Arb.instant().bind(),
        updatedAt = Arb.instant().bind()
    )
}

/**
 * Custom Arbitrary generator for TrainingDay.
 */
fun Arb.Companion.trainingDay(): Arb<TrainingDay> = arbitrary {
    TrainingDay(
        dayName = Arb.stringPattern("[A-Z] - [A-Za-z ]{5,20}").bind(),
        exercises = Arb.list(Arb.plannedExercise(), 1..10).bind()
    )
}

/**
 * Custom Arbitrary generator for PlannedExercise.
 */
fun Arb.Companion.plannedExercise(): Arb<PlannedExercise> = arbitrary {
    PlannedExercise(
        exerciseId = UUID.randomUUID().toString(),
        order = Arb.int(1..20).bind(),
        sets = Arb.int(1..5).bind(),
        reps = Arb.int(1..20).bind(),
        restSeconds = Arb.int(30..180).bind(),
        notes = Arb.string(0..100).orNull(0.5).bind()
    )
}

/**
 * Custom Arbitrary generator for Instant.
 */
fun Arb.Companion.instant(): Arb<Instant> = arbitrary {
    val now = Clock.System.now()
    val offsetMillis = Arb.long(-365L * 24 * 60 * 60 * 1000, 0).bind()
    Instant.fromEpochMilliseconds(now.toEpochMilliseconds() + offsetMillis)
}
