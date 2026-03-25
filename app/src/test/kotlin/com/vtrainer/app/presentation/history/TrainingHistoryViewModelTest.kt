package com.vtrainer.app.presentation.history

import com.vtrainer.app.data.repositories.TrainingLogRepository
import com.vtrainer.app.domain.models.ExerciseLog
import com.vtrainer.app.domain.models.TrainingLog
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

/**
 * Unit tests for TrainingHistoryViewModel.
 *
 * Validates: Requirements 7.1, 7.4
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TrainingHistoryViewModelTest : FunSpec({

    val scheduler = TestCoroutineScheduler()
    val testDispatcher = StandardTestDispatcher(scheduler)

    // Fixed clock: 2024-03-15 (Friday) at noon UTC
    // This gives us deterministic weekly/monthly calculations.
    val fixedNow = Instant.parse("2024-03-15T12:00:00Z")
    val fixedClock = object : Clock {
        override fun now(): Instant = fixedNow
    }
    val tz = TimeZone.UTC

    beforeSpec {
        Dispatchers.setMain(testDispatcher)
    }

    afterSpec {
        Dispatchers.resetMain()
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    fun makeLog(
        logId: String,
        timestamp: Instant,
        totalVolume: Int = 1000,
        exercises: List<ExerciseLog> = emptyList()
    ) = TrainingLog(
        logId = logId,
        userId = "user-1",
        workoutPlanId = null,
        workoutDayName = "Day A",
        timestamp = timestamp,
        origin = "mobile",
        duration = 60,
        totalCalories = null,
        exercises = exercises,
        totalVolume = totalVolume
    )

    fun makeExerciseLog(isPersonalRecord: Boolean) = ExerciseLog(
        exerciseId = "ex-1",
        sets = emptyList(),
        totalVolume = 500,
        isPersonalRecord = isPersonalRecord
    )

    fun makeViewModel(
        logs: List<TrainingLog>,
        clock: Clock = fixedClock
    ): TrainingHistoryViewModel {
        val mockRepo = mockk<TrainingLogRepository>(relaxed = true)
        every { mockRepo.getTrainingHistory() } returns flowOf(logs)
        return TrainingHistoryViewModel(
            trainingLogRepository = mockRepo,
            clock = clock,
            tz = tz
        )
    }

    fun makeViewModelWithError(errorMessage: String): TrainingHistoryViewModel {
        val mockRepo = mockk<TrainingLogRepository>(relaxed = true)
        every { mockRepo.getTrainingHistory() } returns flow {
            throw RuntimeException(errorMessage)
        }
        return TrainingHistoryViewModel(
            trainingLogRepository = mockRepo,
            clock = fixedClock,
            tz = tz
        )
    }

    // -------------------------------------------------------------------------
    // Test 1: History loading — state.logs is populated from repository after init
    // -------------------------------------------------------------------------
    test("history loading — state.logs is populated from repository after init") {
        runTest(testDispatcher) {
            val logs = listOf(
                makeLog("log-1", Instant.parse("2024-03-14T10:00:00Z")),
                makeLog("log-2", Instant.parse("2024-03-13T09:00:00Z")),
                makeLog("log-3", Instant.parse("2024-03-12T08:00:00Z"))
            )

            val vm = makeViewModel(logs)
            advanceUntilIdle()

            val state = vm.state.value
            state.logs shouldHaveSize 3
            state.isLoading shouldBe false
            state.error shouldBe null
        }
    }

    // -------------------------------------------------------------------------
    // Test 2: Progress chart data — weeklyProgress has 8 entries, monthlyProgress has 6 entries
    // -------------------------------------------------------------------------
    test("progress chart data — weeklyProgress has 8 entries and monthlyProgress has 6 entries") {
        runTest(testDispatcher) {
            val vm = makeViewModel(emptyList())
            advanceUntilIdle()

            val state = vm.state.value
            state.weeklyProgress shouldHaveSize 8
            state.monthlyProgress shouldHaveSize 6
        }
    }

    // -------------------------------------------------------------------------
    // Test 3: Logs from current week appear in weeklyProgress with correct volume
    // -------------------------------------------------------------------------
    test("logs from current week appear in weeklyProgress with correct volume") {
        runTest(testDispatcher) {
            // fixedNow = 2024-03-15 (Friday). Current week (Mon-Sun) = 2024-03-11 to 2024-03-17.
            val currentWeekLog1 = makeLog(
                "log-cw1",
                Instant.parse("2024-03-11T10:00:00Z"), // Monday of current week
                totalVolume = 2000
            )
            val currentWeekLog2 = makeLog(
                "log-cw2",
                Instant.parse("2024-03-14T15:00:00Z"), // Thursday of current week
                totalVolume = 3000
            )
            val oldLog = makeLog(
                "log-old",
                Instant.parse("2024-03-04T10:00:00Z"), // Previous week
                totalVolume = 9999
            )

            val vm = makeViewModel(listOf(currentWeekLog1, currentWeekLog2, oldLog))
            advanceUntilIdle()

            val state = vm.state.value
            // weeklyProgress is oldest-first; last entry is the current week
            val currentWeekEntry = state.weeklyProgress.last()
            currentWeekEntry.workoutCount shouldBe 2
            currentWeekEntry.totalVolume shouldBe 5000L
        }
    }

    // -------------------------------------------------------------------------
    // Test 4: Logs from previous months appear in monthlyProgress with correct count
    // -------------------------------------------------------------------------
    test("logs from previous months appear in monthlyProgress with correct count") {
        runTest(testDispatcher) {
            // fixedNow = 2024-03-15. monthlyProgress covers Mar, Feb, Jan, Dec, Nov, Oct (oldest first).
            val janLog1 = makeLog("log-jan1", Instant.parse("2024-01-10T10:00:00Z"))
            val janLog2 = makeLog("log-jan2", Instant.parse("2024-01-20T10:00:00Z"))
            val febLog = makeLog("log-feb", Instant.parse("2024-02-15T10:00:00Z"))

            val vm = makeViewModel(listOf(janLog1, janLog2, febLog))
            advanceUntilIdle()

            val state = vm.state.value
            // monthlyProgress is oldest-first: index 0 = Oct 2023, 1 = Nov, 2 = Dec, 3 = Jan, 4 = Feb, 5 = Mar
            val janEntry = state.monthlyProgress[3]
            janEntry.monthLabel shouldBe "Jan 2024"
            janEntry.workoutCount shouldBe 2

            val febEntry = state.monthlyProgress[4]
            febEntry.monthLabel shouldBe "Feb 2024"
            febEntry.workoutCount shouldBe 1
        }
    }

    // -------------------------------------------------------------------------
    // Test 5: hasPersonalRecord() returns true when any exercise has isPersonalRecord=true
    // -------------------------------------------------------------------------
    test("hasPersonalRecord() returns true when any exercise has isPersonalRecord=true") {
        val log = makeLog(
            "log-pr",
            Instant.parse("2024-03-14T10:00:00Z"),
            exercises = listOf(
                makeExerciseLog(isPersonalRecord = false),
                makeExerciseLog(isPersonalRecord = true)
            )
        )

        log.hasPersonalRecord() shouldBe true
    }

    // -------------------------------------------------------------------------
    // Test 6: hasPersonalRecord() returns false when no exercise has isPersonalRecord=true
    // -------------------------------------------------------------------------
    test("hasPersonalRecord() returns false when no exercise has isPersonalRecord=true") {
        val log = makeLog(
            "log-no-pr",
            Instant.parse("2024-03-14T10:00:00Z"),
            exercises = listOf(
                makeExerciseLog(isPersonalRecord = false),
                makeExerciseLog(isPersonalRecord = false)
            )
        )

        log.hasPersonalRecord() shouldBe false
    }

    // -------------------------------------------------------------------------
    // Test 7: Error state — when repository throws, state.error is set and state.logs is empty
    // -------------------------------------------------------------------------
    test("error state — when repository throws, state.error is set and state.logs is empty") {
        runTest(testDispatcher) {
            val vm = makeViewModelWithError("Database unavailable")
            advanceUntilIdle()

            val state = vm.state.value
            state.logs.shouldBeEmpty()
            state.isLoading shouldBe false
            state.error shouldNotBe null
            state.error shouldBe "Database unavailable"
        }
    }
})
