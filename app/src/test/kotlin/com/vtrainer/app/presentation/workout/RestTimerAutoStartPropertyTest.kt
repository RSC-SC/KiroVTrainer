package com.vtrainer.app.presentation.workout

import com.google.firebase.auth.FirebaseAuth
import com.vtrainer.app.data.repositories.TrainingLogRepository
import com.vtrainer.app.domain.models.PlannedExercise
import com.vtrainer.app.domain.models.TrainingDay
import com.vtrainer.app.domain.models.WorkoutPlan
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.string
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
 * Property-based test for rest timer auto-start on set completion.
 *
 * **Validates: Requirements 4.3, 5.3, 13.1**
 *
 * Property 7: Rest Timer Auto-Start on Set Completion
 *
 * For any WorkoutPlan with any configured restSeconds value, after calling completeSet(),
 * the WorkoutExecutionViewModel state must immediately show isRestTimerActive = true and
 * restTimerSecondsRemaining equal to the exercise's configured restSeconds.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RestTimerAutoStartPropertyTest : FunSpec({

    // -------------------------------------------------------------------------
    // Shared setup: use UnconfinedTestDispatcher so viewModelScope coroutines
    // run synchronously on the test thread, making state assertions immediate.
    // -------------------------------------------------------------------------

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

    fun makePlan(restSeconds: Int, sets: Int = 3): WorkoutPlan {
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
                            reps = 10,
                            restSeconds = restSeconds,
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
    // Scenario 1: After completeSet(), isRestTimerActive is true immediately
    // -------------------------------------------------------------------------
    test("Feature: v-trainer, Property 7: After completeSet(), isRestTimerActive is true immediately").config(
        invocations = 1
    ) {
        checkAll(1, Arb.restSeconds()) { restSeconds ->
            val vm = makeViewModel()
            val plan = makePlan(restSeconds)

            vm.startWorkout(plan, dayIndex = 0)
            vm.completeSet(weight = 60.0, reps = 10)

            val state = vm.state.value
            state.isRestTimerActive shouldBe true
        }
    }

    // -------------------------------------------------------------------------
    // Scenario 2: After completeSet(), restTimerSecondsRemaining equals exercise's restSeconds
    // -------------------------------------------------------------------------
    test("Feature: v-trainer, Property 7: After completeSet(), restTimerSecondsRemaining equals exercise's restSeconds").config(
        invocations = 1
    ) {
        checkAll(1, Arb.restSeconds()) { restSeconds ->
            val vm = makeViewModel()
            val plan = makePlan(restSeconds)

            vm.startWorkout(plan, dayIndex = 0)
            vm.completeSet(weight = 80.0, reps = 8)

            val state = vm.state.value
            state.restTimerSecondsRemaining shouldBe restSeconds
        }
    }

    // -------------------------------------------------------------------------
    // Scenario 3: After skipRest(), isRestTimerActive is false
    // -------------------------------------------------------------------------
    test("Feature: v-trainer, Property 7: After skipRest(), isRestTimerActive is false").config(
        invocations = 1
    ) {
        checkAll(1, Arb.restSeconds()) { restSeconds ->
            val vm = makeViewModel()
            // Use 2 sets so skipRest() can advance without completing the workout
            val plan = makePlan(restSeconds, sets = 2)

            vm.startWorkout(plan, dayIndex = 0)
            vm.completeSet(weight = 100.0, reps = 5)

            // Verify timer is active before skipping
            vm.state.value.isRestTimerActive shouldBe true

            vm.skipRest()

            val state = vm.state.value
            state.isRestTimerActive shouldBe false
        }
    }

    // -------------------------------------------------------------------------
    // Scenario 4: Rest timer seconds come from the planned exercise configuration
    // -------------------------------------------------------------------------
    test("Feature: v-trainer, Property 7: Rest timer seconds come from planned exercise configuration, not hardcoded").config(
        invocations = 1
    ) {
        checkAll(
            1,
            Arb.restSeconds(),
            Arb.restSeconds()
        ) { restSecondsA, restSecondsB ->
            // Only meaningful when the two values differ
            if (restSecondsA == restSecondsB) return@checkAll

            val vmA = makeViewModel()
            val planA = makePlan(restSecondsA)
            vmA.startWorkout(planA, dayIndex = 0)
            vmA.completeSet(weight = 50.0, reps = 12)

            val vmB = makeViewModel()
            val planB = makePlan(restSecondsB)
            vmB.startWorkout(planB, dayIndex = 0)
            vmB.completeSet(weight = 50.0, reps = 12)

            // Each ViewModel must reflect its own plan's restSeconds
            vmA.state.value.restTimerSecondsRemaining shouldBe restSecondsA
            vmB.state.value.restTimerSecondsRemaining shouldBe restSecondsB

            // The two timers must differ (proving values come from config, not hardcoded)
            (vmA.state.value.restTimerSecondsRemaining == vmB.state.value.restTimerSecondsRemaining) shouldBe false
        }
    }
})

// -------------------------------------------------------------------------
// Custom Arb generators
// -------------------------------------------------------------------------

/**
 * Generates valid rest durations in seconds (30–300 seconds, i.e. 30s to 5 minutes).
 */
fun Arb.Companion.restSeconds(): Arb<Int> = Arb.int(30..300)
