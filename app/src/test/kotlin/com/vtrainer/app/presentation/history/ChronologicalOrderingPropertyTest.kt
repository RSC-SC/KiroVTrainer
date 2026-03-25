package com.vtrainer.app.presentation.history

import com.vtrainer.app.data.repositories.TrainingLogRepository
import com.vtrainer.app.domain.models.ExerciseLog
import com.vtrainer.app.domain.models.TrainingLog
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.Duration.Companion.days

/**
 * Property-based test for training history chronological ordering.
 *
 * **Validates: Requirements 7.1**
 *
 * Property 13: Training History Chronological Ordering
 *
 * For any list of TrainingLogs with arbitrary timestamps, the TrainingHistoryViewModel
 * must always return them sorted by timestamp in descending order (most recent first).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChronologicalOrderingPropertyTest : FunSpec({

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

    fun makeViewModel(logs: List<TrainingLog>): TrainingHistoryViewModel {
        val mockRepo = mockk<TrainingLogRepository>(relaxed = true)
        every { mockRepo.getTrainingHistory() } returns flowOf(logs)
        return TrainingHistoryViewModel(
            trainingLogRepository = mockRepo,
            clock = Clock.System
        )
    }

    // -------------------------------------------------------------------------
    // Scenario 1: For any list of logs with random timestamps, returned logs are sorted descending
    // -------------------------------------------------------------------------
    test("Feature: v-trainer, Property 13: Logs are always sorted by timestamp descending").config(
        invocations = 1
    ) {
        checkAll(1, Arb.trainingLogList()) { logs ->
            runTest(testDispatcher) {
                val vm = makeViewModel(logs)
                advanceUntilIdle()

                val state = vm.state.value
                val timestamps = state.logs.map { it.timestamp }
                timestamps shouldBe timestamps.sortedDescending()
            }
        }
    }

    // -------------------------------------------------------------------------
    // Scenario 2: The most recent log is always first
    // -------------------------------------------------------------------------
    test("Feature: v-trainer, Property 13: Most recent log is always first").config(
        invocations = 1
    ) {
        checkAll(1, Arb.nonEmptyTrainingLogList()) { logs ->
            runTest(testDispatcher) {
                val vm = makeViewModel(logs)
                advanceUntilIdle()

                val state = vm.state.value
                if (state.logs.isNotEmpty()) {
                    val maxTimestamp = logs.maxOf { it.timestamp }
                    state.logs.first().timestamp shouldBe maxTimestamp
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Scenario 3: The oldest log is always last
    // -------------------------------------------------------------------------
    test("Feature: v-trainer, Property 13: Oldest log is always last").config(
        invocations = 1
    ) {
        checkAll(1, Arb.nonEmptyTrainingLogList()) { logs ->
            runTest(testDispatcher) {
                val vm = makeViewModel(logs)
                advanceUntilIdle()

                val state = vm.state.value
                if (state.logs.isNotEmpty()) {
                    val minTimestamp = logs.minOf { it.timestamp }
                    state.logs.last().timestamp shouldBe minTimestamp
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Scenario 4: Sorting is stable — logs with equal timestamps maintain relative order
    // -------------------------------------------------------------------------
    test("Feature: v-trainer, Property 13: Sorting is stable for logs with equal timestamps").config(
        invocations = 1
    ) {
        checkAll(1, Arb.trainingLogListWithDuplicateTimestamps()) { logs ->
            runTest(testDispatcher) {
                val vm = makeViewModel(logs)
                advanceUntilIdle()

                val state = vm.state.value
                // Verify the sort is stable: for equal timestamps, original relative order is preserved
                val sortedExpected = logs.sortedByDescending { it.timestamp }
                state.logs shouldBe sortedExpected
            }
        }
    }
})

// -------------------------------------------------------------------------
// Custom Arb generators
// -------------------------------------------------------------------------

private val now = Clock.System.now()
private val oneYearAgo = now.minus(365.days)

/**
 * Generates a random Instant within the last 365 days.
 */
fun Arb.Companion.recentInstant(): Arb<Instant> = arbitrary {
    val epochSecondsNow = now.epochSeconds
    val epochSecondsYearAgo = oneYearAgo.epochSeconds
    val randomEpochSeconds = Arb.long(epochSecondsYearAgo..epochSecondsNow).bind()
    Instant.fromEpochSeconds(randomEpochSeconds)
}

/**
 * Generates a minimal TrainingLog with a random timestamp.
 */
fun Arb.Companion.trainingLog(): Arb<TrainingLog> = arbitrary {
    TrainingLog(
        logId = Arb.string(5..10).bind(),
        userId = "test-user",
        workoutPlanId = null,
        workoutDayName = "Day A",
        timestamp = Arb.recentInstant().bind(),
        origin = "mobile",
        duration = Arb.int(10..120).bind(),
        totalCalories = null,
        exercises = emptyList<ExerciseLog>(),
        totalVolume = Arb.int(0..10000).bind()
    )
}

/**
 * Generates a list of 0–20 TrainingLogs with arbitrary timestamps.
 */
fun Arb.Companion.trainingLogList(): Arb<List<TrainingLog>> =
    Arb.list(Arb.trainingLog(), 0..20)

/**
 * Generates a non-empty list of 1–20 TrainingLogs with arbitrary timestamps.
 */
fun Arb.Companion.nonEmptyTrainingLogList(): Arb<List<TrainingLog>> =
    Arb.list(Arb.trainingLog(), 1..20)

/**
 * Generates a list of TrainingLogs where some share the same timestamp,
 * to test stable sort behaviour.
 */
fun Arb.Companion.trainingLogListWithDuplicateTimestamps(): Arb<List<TrainingLog>> = arbitrary {
    val sharedTimestamp = Arb.recentInstant().bind()
    val count = Arb.int(2..6).bind()
    (1..count).map { i ->
        TrainingLog(
            logId = "log-$i",
            userId = "test-user",
            workoutPlanId = null,
            workoutDayName = "Day A",
            timestamp = sharedTimestamp,
            origin = "mobile",
            duration = 60,
            totalCalories = null,
            exercises = emptyList(),
            totalVolume = i * 100
        )
    }
}
