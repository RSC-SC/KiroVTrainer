package com.vtrainer.app.presentation.workout

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.vtrainer.app.data.repositories.TrainingLogRepository
import com.vtrainer.app.domain.models.PlannedExercise
import com.vtrainer.app.domain.models.TrainingDay
import com.vtrainer.app.domain.models.TrainingLog
import com.vtrainer.app.domain.models.WorkoutPlan
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Clock
import java.util.UUID

/**
 * Unit tests for WorkoutExecutionViewModel.
 *
 * Validates: Requirements 4.2, 4.3, 4.6
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutExecutionViewModelTest : FunSpec({

    val scheduler = TestCoroutineScheduler()
    val testDispatcher = StandardTestDispatcher(scheduler)

    beforeSpec {
        Dispatchers.setMain(testDispatcher)
    }

    afterSpec {
        Dispatchers.resetMain()
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    fun makeRepo(saveResult: Result<Unit> = Result.success(Unit)): TrainingLogRepository {
        val repo = mockk<TrainingLogRepository>(relaxed = true)
        coEvery { repo.saveTrainingLog(any()) } returns saveResult
        return repo
    }

    fun makeAuth(uid: String = "test-user-id"): FirebaseAuth {
        val user = mockk<FirebaseUser>()
        every { user.uid } returns uid
        val auth = mockk<FirebaseAuth>()
        every { auth.currentUser } returns user
        return auth
    }

    fun makePlan(
        exerciseCount: Int = 1,
        setsPerExercise: Int = 3,
        restSeconds: Int = 60
    ): WorkoutPlan {
        val now = Clock.System.now()
        val exercises = (1..exerciseCount).map { i ->
            PlannedExercise(
                exerciseId = "exercise-$i",
                order = i,
                sets = setsPerExercise,
                reps = 10,
                restSeconds = restSeconds,
                notes = null
            )
        }
        return WorkoutPlan(
            planId = UUID.randomUUID().toString(),
            userId = "test-user-id",
            name = "Test Plan",
            description = null,
            trainingDays = listOf(
                TrainingDay(dayName = "Day A", exercises = exercises)
            ),
            createdAt = now,
            updatedAt = now
        )
    }

    // -------------------------------------------------------------------------
    // 1. Set completion flow — completedSets grows with correct weight/reps
    // Validates: Requirement 4.2
    // -------------------------------------------------------------------------
    test("completeSet() adds a SetLog with the provided weight and reps") {
        val vm = WorkoutExecutionViewModel(makeRepo(), makeAuth())
        val plan = makePlan(setsPerExercise = 3)

        vm.startWorkout(plan, dayIndex = 0)
        vm.completeSet(weight = 80.0, reps = 8)

        val state = vm.state.value
        state.completedSets shouldHaveSize 1
        state.completedSets[0].weight shouldBe 80.0
        state.completedSets[0].reps shouldBe 8
        state.completedSets[0].setNumber shouldBe 1
    }

    test("completeSet() called twice grows completedSets to 2") {
        val vm = WorkoutExecutionViewModel(makeRepo(), makeAuth())
        val plan = makePlan(setsPerExercise = 3)

        vm.startWorkout(plan, dayIndex = 0)
        vm.completeSet(weight = 80.0, reps = 8)

        // Advance timer so the VM advances to next set
        advanceTimeBy(scheduler, 61_000L)

        vm.completeSet(weight = 85.0, reps = 6)

        val state = vm.state.value
        state.completedSets shouldHaveSize 2
        state.completedSets[1].weight shouldBe 85.0
        state.completedSets[1].reps shouldBe 6
    }

    // -------------------------------------------------------------------------
    // 2. Rest timer countdown — restTimerSecondsRemaining starts at exercise.restSeconds
    // Validates: Requirement 4.3
    // -------------------------------------------------------------------------
    test("completeSet() starts rest timer at exercise.restSeconds") {
        val restSeconds = 90
        val vm = WorkoutExecutionViewModel(makeRepo(), makeAuth())
        val plan = makePlan(restSeconds = restSeconds)

        vm.startWorkout(plan, dayIndex = 0)
        vm.completeSet(weight = 60.0, reps = 10)

        val state = vm.state.value
        state.isRestTimerActive shouldBe true
        state.restTimerSecondsRemaining shouldBe restSeconds
    }

    test("rest timer counts down after time advances") {
        val restSeconds = 60
        val vm = WorkoutExecutionViewModel(makeRepo(), makeAuth())
        val plan = makePlan(setsPerExercise = 3, restSeconds = restSeconds)

        vm.startWorkout(plan, dayIndex = 0)
        vm.completeSet(weight = 60.0, reps = 10)

        // Advance 30 seconds
        advanceTimeBy(scheduler, 30_000L)

        val state = vm.state.value
        state.restTimerSecondsRemaining shouldBe 30
        state.isRestTimerActive shouldBe true
    }

    test("rest timer deactivates after full countdown") {
        val restSeconds = 60
        val vm = WorkoutExecutionViewModel(makeRepo(), makeAuth())
        val plan = makePlan(setsPerExercise = 3, restSeconds = restSeconds)

        vm.startWorkout(plan, dayIndex = 0)
        vm.completeSet(weight = 60.0, reps = 10)

        // Advance past the full rest period
        advanceTimeBy(scheduler, 61_000L)

        val state = vm.state.value
        state.isRestTimerActive shouldBe false
        state.restTimerSecondsRemaining shouldBe 0
    }

    // -------------------------------------------------------------------------
    // 3. Workout completion and save — finishWorkout() calls saveTrainingLog()
    // Validates: Requirement 4.6
    // -------------------------------------------------------------------------
    test("finishWorkout() calls saveTrainingLog with a valid TrainingLog") {
        val repo = makeRepo()
        val userId = "user-abc"
        val vm = WorkoutExecutionViewModel(repo, makeAuth(uid = userId))
        val plan = makePlan(setsPerExercise = 1, restSeconds = 30)

        vm.startWorkout(plan, dayIndex = 0)
        vm.completeSet(weight = 100.0, reps = 5)

        // Skip rest so the workout is marked complete
        vm.skipRest()

        vm.finishWorkout()
        advanceUntilIdle(scheduler)

        val logSlot = slot<TrainingLog>()
        coVerify { repo.saveTrainingLog(capture(logSlot)) }

        val savedLog = logSlot.captured
        savedLog.userId shouldBe userId
        savedLog.workoutPlanId shouldBe plan.planId
        savedLog.exercises shouldNotBe null
        savedLog.totalVolume shouldBe (100.0 * 5).toInt()
        savedLog.logId shouldNotBe null
    }

    test("finishWorkout() sets isWorkoutComplete to true on success") {
        val vm = WorkoutExecutionViewModel(makeRepo(), makeAuth())
        val plan = makePlan(setsPerExercise = 1, restSeconds = 30)

        vm.startWorkout(plan, dayIndex = 0)
        vm.completeSet(weight = 50.0, reps = 10)
        vm.skipRest()
        vm.finishWorkout()
        advanceUntilIdle(scheduler)

        vm.state.value.isWorkoutComplete shouldBe true
    }

    test("finishWorkout() sets error when saveTrainingLog fails") {
        val repo = makeRepo(saveResult = Result.failure(RuntimeException("Network error")))
        val vm = WorkoutExecutionViewModel(repo, makeAuth())
        // Use 2 sets so skipRest() after the first set doesn't complete the workout
        val plan = makePlan(setsPerExercise = 2, restSeconds = 30)

        vm.startWorkout(plan, dayIndex = 0)
        vm.completeSet(weight = 50.0, reps = 10)
        // Skip rest — advances to set 2, workout is NOT complete yet
        vm.skipRest()
        vm.finishWorkout()
        advanceUntilIdle(scheduler)

        vm.state.value.isWorkoutComplete shouldBe false
        vm.state.value.error shouldNotBe null
    }

    // -------------------------------------------------------------------------
    // 4. Progression — after all sets of exercise 1, currentExerciseIndex advances
    // Validates: Requirement 4.2
    // -------------------------------------------------------------------------
    test("after completing all sets of exercise 1, currentExerciseIndex advances to 1") {
        val vm = WorkoutExecutionViewModel(makeRepo(), makeAuth())
        // 2 exercises, 2 sets each
        val plan = makePlan(exerciseCount = 2, setsPerExercise = 2, restSeconds = 30)

        vm.startWorkout(plan, dayIndex = 0)

        // Complete set 1 of exercise 1
        vm.completeSet(weight = 60.0, reps = 10)
        vm.skipRest() // advances to set 2 of exercise 1

        // Complete set 2 of exercise 1
        vm.completeSet(weight = 60.0, reps = 10)
        vm.skipRest() // all sets done → advances to exercise 2

        val state = vm.state.value
        state.currentExerciseIndex shouldBe 1
        state.currentSetIndex shouldBe 0
    }

    // -------------------------------------------------------------------------
    // 5. skipRest() advances to next set without waiting for timer
    // Validates: Requirement 4.3
    // -------------------------------------------------------------------------
    test("skipRest() immediately deactivates timer and advances to next set") {
        val vm = WorkoutExecutionViewModel(makeRepo(), makeAuth())
        val plan = makePlan(setsPerExercise = 3, restSeconds = 120)

        vm.startWorkout(plan, dayIndex = 0)
        vm.completeSet(weight = 70.0, reps = 10)

        // Timer should be active
        vm.state.value.isRestTimerActive shouldBe true

        vm.skipRest()

        val state = vm.state.value
        state.isRestTimerActive shouldBe false
        state.restTimerSecondsRemaining shouldBe 0
        state.currentSetIndex shouldBe 1
    }

    test("skipRest() does not require waiting for timer to expire") {
        val vm = WorkoutExecutionViewModel(makeRepo(), makeAuth())
        val plan = makePlan(setsPerExercise = 3, restSeconds = 300) // 5 min rest

        vm.startWorkout(plan, dayIndex = 0)
        vm.completeSet(weight = 70.0, reps = 10)

        // Skip immediately without advancing time
        vm.skipRest()

        // Should have advanced to set index 1 without waiting 300 seconds
        vm.state.value.currentSetIndex shouldBe 1
        vm.state.value.isRestTimerActive shouldBe false
    }
})

// -------------------------------------------------------------------------
// Helper extension to advance time using the scheduler
// -------------------------------------------------------------------------
@OptIn(ExperimentalCoroutinesApi::class)
private fun advanceTimeBy(scheduler: TestCoroutineScheduler, millis: Long) {
    scheduler.advanceTimeBy(millis)
    scheduler.runCurrent()
}

@OptIn(ExperimentalCoroutinesApi::class)
private fun advanceUntilIdle(scheduler: TestCoroutineScheduler) {
    scheduler.advanceUntilIdle()
}
