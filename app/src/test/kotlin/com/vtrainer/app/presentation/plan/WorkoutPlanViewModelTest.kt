package com.vtrainer.app.presentation.plan

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.vtrainer.app.data.repositories.WorkoutRepository
import com.vtrainer.app.domain.models.PlannedExercise
import com.vtrainer.app.domain.models.TrainingDay
import com.vtrainer.app.domain.models.WorkoutPlan
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Clock
import java.util.UUID

/**
 * Unit tests for WorkoutPlanViewModel.
 *
 * Validates: Requirements 3.4, 3.6
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutPlanViewModelTest : FunSpec({

    val scheduler = TestCoroutineScheduler()
    val testDispatcher = StandardTestDispatcher(scheduler)

    beforeSpec {
        Dispatchers.setMain(testDispatcher)
    }

    afterSpec {
        Dispatchers.resetMain()
    }

    fun makeRepo(
        plans: List<WorkoutPlan> = emptyList(),
        saveResult: Result<Unit> = Result.success(Unit),
        deleteResult: Result<Unit> = Result.success(Unit)
    ): WorkoutRepository {
        val repo = mockk<WorkoutRepository>(relaxed = true)
        every { repo.getWorkoutPlans() } returns flowOf(plans)
        coEvery { repo.saveWorkoutPlan(any()) } returns saveResult
        coEvery { repo.deleteWorkoutPlan(any()) } returns deleteResult
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
        planId: String = UUID.randomUUID().toString(),
        name: String = "Test Plan",
        days: List<TrainingDay> = emptyList()
    ): WorkoutPlan {
        val now = Clock.System.now()
        return WorkoutPlan(
            planId = planId,
            userId = "test-user-id",
            name = name,
            description = null,
            trainingDays = days,
            createdAt = now,
            updatedAt = now
        )
    }

    fun makeExercise(id: String = "ex-1", order: Int = 1) = PlannedExercise(
        exerciseId = id,
        order = order,
        sets = 3,
        reps = 10,
        restSeconds = 60,
        notes = null
    )

    // -------------------------------------------------------------------------
    // 1. Plan creation — startNewPlan() sets editingPlan to a blank plan
    // Validates: Requirement 3.4
    // -------------------------------------------------------------------------
    test("startNewPlan() sets editingPlan to a non-null blank plan with empty name and no days") {
        val vm = WorkoutPlanViewModel(makeRepo(), makeAuth())
        scheduler.advanceUntilIdle()

        vm.startNewPlan()

        val editing = vm.state.value.editingPlan
        editing shouldNotBe null
        editing!!.name shouldBe ""
        editing.trainingDays shouldHaveSize 0
        editing.planId shouldNotBe null
    }

    // -------------------------------------------------------------------------
    // 2. Exercise addition — addExercise(dayIndex, exercise) adds to correct day
    // Validates: Requirement 3.4
    // -------------------------------------------------------------------------
    test("addExercise() adds the exercise to the correct day") {
        val vm = WorkoutPlanViewModel(makeRepo(), makeAuth())
        scheduler.advanceUntilIdle()

        vm.startNewPlan()
        vm.addTrainingDay("Day A")
        vm.addTrainingDay("Day B")

        val exercise = makeExercise("ex-1")
        vm.addExercise(dayIndex = 1, exercise = exercise)

        val editing = vm.state.value.editingPlan!!
        editing.trainingDays[0].exercises shouldHaveSize 0
        editing.trainingDays[1].exercises shouldHaveSize 1
        editing.trainingDays[1].exercises[0].exerciseId shouldBe "ex-1"
    }

    // -------------------------------------------------------------------------
    // 3. Plan save — savePlan() calls saveWorkoutPlan and sets saveSuccess=true
    // Validates: Requirement 3.4
    // -------------------------------------------------------------------------
    test("savePlan() calls workoutRepository.saveWorkoutPlan() with editingPlan and sets saveSuccess=true") {
        val repo = makeRepo()
        val vm = WorkoutPlanViewModel(repo, makeAuth())
        scheduler.advanceUntilIdle()

        vm.startNewPlan()
        val editingPlan = vm.state.value.editingPlan!!

        vm.savePlan()
        scheduler.advanceUntilIdle()

        coVerify { repo.saveWorkoutPlan(editingPlan) }
        vm.state.value.saveSuccess shouldBe true
    }

    // -------------------------------------------------------------------------
    // 4. Plan delete — deletePlan(planId) calls deleteWorkoutPlan(planId)
    // Validates: Requirement 3.6
    // -------------------------------------------------------------------------
    test("deletePlan() calls workoutRepository.deleteWorkoutPlan() with the correct planId") {
        val repo = makeRepo()
        val vm = WorkoutPlanViewModel(repo, makeAuth())
        scheduler.advanceUntilIdle()

        val planId = "plan-abc-123"
        vm.deletePlan(planId)
        scheduler.advanceUntilIdle()

        coVerify { repo.deleteWorkoutPlan(planId) }
    }

    // -------------------------------------------------------------------------
    // 5. updatePlanName() updates editingPlan.name correctly
    // -------------------------------------------------------------------------
    test("updatePlanName() updates editingPlan.name correctly") {
        val vm = WorkoutPlanViewModel(makeRepo(), makeAuth())
        scheduler.advanceUntilIdle()

        vm.startNewPlan()
        vm.updatePlanName("My New Plan")

        vm.state.value.editingPlan!!.name shouldBe "My New Plan"
    }

    // -------------------------------------------------------------------------
    // 6. addTrainingDay() adds a new day to editingPlan.trainingDays
    // -------------------------------------------------------------------------
    test("addTrainingDay() adds a new day to editingPlan.trainingDays") {
        val vm = WorkoutPlanViewModel(makeRepo(), makeAuth())
        scheduler.advanceUntilIdle()

        vm.startNewPlan()
        vm.addTrainingDay("Push Day")

        val days = vm.state.value.editingPlan!!.trainingDays
        days shouldHaveSize 1
        days[0].dayName shouldBe "Push Day"
    }

    // -------------------------------------------------------------------------
    // 7. removeExercise() removes the correct exercise from the correct day
    // -------------------------------------------------------------------------
    test("removeExercise() removes the correct exercise from the correct day") {
        val vm = WorkoutPlanViewModel(makeRepo(), makeAuth())
        scheduler.advanceUntilIdle()

        vm.startNewPlan()
        vm.addTrainingDay("Day A")
        vm.addExercise(0, makeExercise("ex-1", order = 1))
        vm.addExercise(0, makeExercise("ex-2", order = 2))

        vm.removeExercise(dayIndex = 0, exerciseIndex = 0)

        val exercises = vm.state.value.editingPlan!!.trainingDays[0].exercises
        exercises shouldHaveSize 1
        exercises[0].exerciseId shouldBe "ex-2"
    }

    // -------------------------------------------------------------------------
    // 8. cancelEdit() clears editingPlan to null
    // -------------------------------------------------------------------------
    test("cancelEdit() clears editingPlan to null") {
        val vm = WorkoutPlanViewModel(makeRepo(), makeAuth())
        scheduler.advanceUntilIdle()

        vm.startNewPlan()
        vm.state.value.editingPlan shouldNotBe null

        vm.cancelEdit()

        vm.state.value.editingPlan shouldBe null
    }

    // -------------------------------------------------------------------------
    // 9. savePlan() sets error when repository fails
    // Validates: Requirement 3.4
    // -------------------------------------------------------------------------
    test("savePlan() sets error state when repository throws an exception") {
        val repo = makeRepo(saveResult = Result.failure(RuntimeException("Save failed")))
        val vm = WorkoutPlanViewModel(repo, makeAuth())
        scheduler.advanceUntilIdle()

        vm.startNewPlan()
        vm.savePlan()
        scheduler.advanceUntilIdle()

        vm.state.value.saveSuccess shouldBe false
        vm.state.value.error shouldNotBe null
    }
})
