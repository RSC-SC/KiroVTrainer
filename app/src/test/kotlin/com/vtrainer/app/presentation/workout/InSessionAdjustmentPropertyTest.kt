package com.vtrainer.app.presentation.workout

import com.google.firebase.auth.FirebaseAuth
import com.vtrainer.app.data.repositories.TrainingLogRepository
import com.vtrainer.app.domain.models.PlannedExercise
import com.vtrainer.app.domain.models.TrainingDay
import com.vtrainer.app.domain.models.WorkoutPlan
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.double
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Clock
import java.util.UUID

/**
 * Property-based test for in-session weight/reps adjustment persistence.
 *
 * **Validates: Requirements 4.5, 5.5**
 *
 * Property 26: In-Session Weight/Reps Adjustment Persistence
 *
 * For any weight and reps values adjusted during a session via adjustWeight() and adjustReps(),
 * when completeSet() is called, the completed SetLog must record the adjusted values
 * (not the plan defaults).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class InSessionAdjustmentPropertyTest : FunSpec({

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

    fun makeViewModel(): WorkoutExecutionViewModel {
        val mockRepo = mockk<TrainingLogRepository>(relaxed = true)
        val mockAuth = mockk<FirebaseAuth>(relaxed = true)
        return WorkoutExecutionViewModel(mockRepo, mockAuth)
    }

    fun makePlan(defaultWeight: Double = 60.0, defaultReps: Int = 10, sets: Int = 3): WorkoutPlan {
        val now = Clock.System.now()
        return WorkoutPlan(
            planId = UUID.randomUUID().toString(),
            userId = "test-user",
            name = "Test Plan",
            description = null,
            trainingDays = listOf(
                TrainingDay(
                    dayName = "Day A",
                    exercises = listOf(
                        PlannedExercise(
                            exerciseId = UUID.randomUUID().toString(),
                            order = 1,
                            sets = sets,
                            reps = defaultReps,
                            restSeconds = 60,
                            notes = null
                        )
                    )
                )
            ),
            createdAt = now,
            updatedAt = now
        )
    }

    // -------------------------------------------------------------------------
    // Scenario 1: adjustWeight() updates adjustedWeight in state immediately
    // -------------------------------------------------------------------------
    test("Feature: v-trainer, Property 26: adjustWeight() updates adjustedWeight in state immediately").config(
        invocations = 1
    ) {
        checkAll(1, Arb.adjustedWeight()) { weight ->
            val vm = makeViewModel()
            vm.startWorkout(makePlan(), dayIndex = 0)

            vm.adjustWeight(weight)

            vm.state.value.adjustedWeight shouldBe weight
        }
    }

    // -------------------------------------------------------------------------
    // Scenario 2: adjustReps() updates adjustedReps in state immediately
    // -------------------------------------------------------------------------
    test("Feature: v-trainer, Property 26: adjustReps() updates adjustedReps in state immediately").config(
        invocations = 1
    ) {
        checkAll(1, Arb.adjustedReps()) { reps ->
            val vm = makeViewModel()
            vm.startWorkout(makePlan(), dayIndex = 0)

            vm.adjustReps(reps)

            vm.state.value.adjustedReps shouldBe reps
        }
    }

    // -------------------------------------------------------------------------
    // Scenario 3: completeSet() with adjusted values records those exact values in SetLog
    // -------------------------------------------------------------------------
    test("Feature: v-trainer, Property 26: completeSet() records adjusted weight and reps in SetLog").config(
        invocations = 1
    ) {
        checkAll(1, Arb.adjustedWeight(), Arb.adjustedReps()) { weight, reps ->
            val vm = makeViewModel()
            val plan = makePlan(defaultWeight = 50.0, defaultReps = 8)
            vm.startWorkout(plan, dayIndex = 0)

            // Apply adjustments then complete the set with those values
            vm.adjustWeight(weight)
            vm.adjustReps(reps)
            vm.completeSet(weight = weight, reps = reps)

            val completedSets = vm.state.value.completedSets
            completedSets.size shouldBe 1
            completedSets[0].weight shouldBe weight
            completedSets[0].reps shouldBe reps
        }
    }

    // -------------------------------------------------------------------------
    // Scenario 4: Adjustments are reset after completeSet() (ready for next set)
    // -------------------------------------------------------------------------
    test("Feature: v-trainer, Property 26: adjustedWeight and adjustedReps are reset to null after completeSet()").config(
        invocations = 1
    ) {
        checkAll(1, Arb.adjustedWeight(), Arb.adjustedReps()) { weight, reps ->
            val vm = makeViewModel()
            vm.startWorkout(makePlan(sets = 3), dayIndex = 0)

            vm.adjustWeight(weight)
            vm.adjustReps(reps)
            vm.completeSet(weight = weight, reps = reps)

            val state = vm.state.value
            state.adjustedWeight.shouldBeNull()
            state.adjustedReps.shouldBeNull()
        }
    }

    // -------------------------------------------------------------------------
    // Scenario 5: Multiple sequential adjustments — last value wins
    // -------------------------------------------------------------------------
    test("Feature: v-trainer, Property 26: multiple sequential adjustments — last value wins").config(
        invocations = 1
    ) {
        checkAll(
            1,
            Arb.adjustedWeight(),
            Arb.adjustedWeight(),
            Arb.adjustedReps(),
            Arb.adjustedReps()
        ) { weight1, weight2, reps1, reps2 ->
            val vm = makeViewModel()
            vm.startWorkout(makePlan(sets = 3), dayIndex = 0)

            // Apply first adjustments
            vm.adjustWeight(weight1)
            vm.adjustReps(reps1)

            // Override with second adjustments
            vm.adjustWeight(weight2)
            vm.adjustReps(reps2)

            // Complete set with the final (second) values
            vm.completeSet(weight = weight2, reps = reps2)

            val completedSets = vm.state.value.completedSets
            completedSets.size shouldBe 1
            completedSets[0].weight shouldBe weight2
            completedSets[0].reps shouldBe reps2
        }
    }
})

// -------------------------------------------------------------------------
// Custom Arb generators
// -------------------------------------------------------------------------

/**
 * Generates realistic weight values in kg (1.0–300.0 kg).
 */
fun Arb.Companion.adjustedWeight(): Arb<Double> =
    Arb.double(1.0, 300.0)

/**
 * Generates realistic reps values (1–50 reps).
 */
fun Arb.Companion.adjustedReps(): Arb<Int> =
    Arb.int(1..50)
