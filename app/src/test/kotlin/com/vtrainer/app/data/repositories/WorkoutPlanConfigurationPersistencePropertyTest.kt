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
 * Property-based test for workout plan configuration persistence.
 * 
 * Validates: Requirements 3.3, 3.4
 * 
 * Property 12: Workout Plan Configuration Persistence
 * 
 * For any exercise in a workout plan configured with specific sets, reps, and rest time values,
 * those exact values SHALL be present when the plan is retrieved from storage.
 */
class WorkoutPlanConfigurationPersistencePropertyTest : FunSpec({
    test("Feature: v-trainer, Property 12: Workout Plan Configuration Persistence").config(
        invocations = 1
    ) {
        checkAll(1, Arb.workoutPlanWithConfiguration()) { plan ->
            val mockFirestore = mockk<FirebaseFirestore>(relaxed = true)
            val mockAuth = mockk<FirebaseAuth>(relaxed = true)
            val mockUser = mockk<FirebaseUser>(relaxed = true)
            val mockDao = mockk<WorkoutPlanDao>(relaxed = true)
            
            every { mockAuth.currentUser } returns mockUser
            every { mockUser.uid } returns "test-user-id"
            
            val savedEntities = mutableListOf<WorkoutPlanEntity>()
            coEvery { mockDao.insert(capture(savedEntities)) } just Runs
            coEvery { mockDao.updateSyncStatus(any(), any(), any()) } just Runs
            
            val repository = WorkoutRepositoryImpl(mockFirestore, mockAuth, mockDao)
            
            val saveResult = repository.saveWorkoutPlan(plan)
            
            saveResult.isSuccess shouldBe true
            
            coVerify { mockDao.insert(any()) }
            
            val savedEntity = savedEntities.first()
            val retrievedPlan = savedEntity.toDomain()
            
            plan.trainingDays.forEachIndexed { dayIndex, originalDay ->
                val retrievedDay = retrievedPlan.trainingDays[dayIndex]
                
                originalDay.exercises.forEachIndexed { exerciseIndex, originalExercise ->
                    val retrievedExercise = retrievedDay.exercises[exerciseIndex]
                    
                    retrievedExercise.sets shouldBe originalExercise.sets
                    retrievedExercise.reps shouldBe originalExercise.reps
                    retrievedExercise.restSeconds shouldBe originalExercise.restSeconds
                    retrievedExercise.exerciseId shouldBe originalExercise.exerciseId
                    retrievedExercise.order shouldBe originalExercise.order
                    retrievedExercise.notes shouldBe originalExercise.notes
                }
            }
        }
    }
})

fun Arb.Companion.workoutPlanWithConfiguration(): Arb<WorkoutPlan> = arbitrary {
    val trainingDays = Arb.list(Arb.trainingDayWithConfiguration(), 1..5).bind()
    
    WorkoutPlan(
        planId = UUID.randomUUID().toString(),
        userId = "test-user-id",
        name = Arb.string(5..50).bind(),
        description = Arb.string(0..200).orNull(0.3).bind(),
        trainingDays = trainingDays,
        createdAt = Arb.instantForTest().bind(),
        updatedAt = Arb.instantForTest().bind()
    )
}

fun Arb.Companion.trainingDayWithConfiguration(): Arb<TrainingDay> = arbitrary {
    TrainingDay(
        dayName = Arb.stringPattern("[A-Z] - [A-Za-z ]{5,20}").bind(),
        exercises = Arb.list(Arb.plannedExerciseWithConfiguration(), 1..8).bind()
    )
}

fun Arb.Companion.plannedExerciseWithConfiguration(): Arb<PlannedExercise> = arbitrary {
    PlannedExercise(
        exerciseId = UUID.randomUUID().toString(),
        order = Arb.int(1..20).bind(),
        sets = Arb.int(1..10).bind(),
        reps = Arb.int(1..50).bind(),
        restSeconds = Arb.int(15..300).bind(),
        notes = Arb.string(0..100).orNull(0.5).bind()
    )
}

fun Arb.Companion.instantForTest(): Arb<Instant> = arbitrary {
    val now = Clock.System.now()
    val offsetMillis = Arb.long(-365L * 24 * 60 * 60 * 1000, 0).bind()
    Instant.fromEpochMilliseconds(now.toEpochMilliseconds() + offsetMillis)
}
