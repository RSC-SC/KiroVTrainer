package com.vtrainer.app.presentation.dashboard

import com.vtrainer.app.domain.models.ExerciseLog
import com.vtrainer.app.domain.models.TrainingLog
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.checkAll
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import java.util.UUID

/**
 * Property-based test for weekly aggregation correctness.
 *
 * **Validates: Requirements 7.4, 10.5, 14.3**
 *
 * Property 19: Weekly and Monthly Aggregation Correctness
 *
 * For any list of TrainingLogs, the weekly stats computed by [WeeklyStatsCalculator] must equal
 * the sum of totalVolume for all logs whose timestamp falls within the current ISO week
 * (Monday 00:00 to Sunday 23:59), and the workout count must equal the number of such logs.
 *
 * Tests are performed against [WeeklyStatsCalculator] — the pure business-logic utility
 * extracted from [DashboardViewModel] — so no Android framework dependencies are needed.
 */
class WeeklyAggregationPropertyTest : FunSpec({

    // -------------------------------------------------------------------------
    // Shared helpers
    // -------------------------------------------------------------------------

    val tz = TimeZone.currentSystemDefault()

    /** Start of the current ISO week (Monday 00:00:00 in system timezone). */
    fun weekStart(): Instant {
        val today = Clock.System.now().toLocalDateTime(tz).date
        val daysFromMonday = today.dayOfWeek.ordinal // 0=Mon … 6=Sun
        val monday = today.minus(daysFromMonday, DateTimeUnit.DAY)
        return monday.atStartOfDayIn(tz)
    }

    /** Start of next ISO week (next Monday 00:00:00). */
    fun nextWeekStart(): Instant =
        weekStart().plus(7, DateTimeUnit.DAY, tz)

    /**
     * Returns a timestamp within the current ISO week.
     * [offsetHours] is clamped to [0, 6*24) so it stays within Mon–Sun.
     */
    fun inCurrentWeek(offsetHours: Int): Instant {
        val clampedOffset = offsetHours.coerceIn(0, 6 * 24 - 1)
        return weekStart().plus(clampedOffset.toLong() * 3600 * 1000, DateTimeUnit.MILLISECOND)
    }

    /**
     * Returns a timestamp in a previous week.
     * [offsetDays] is clamped to [1, 30].
     */
    fun inPreviousWeek(offsetDays: Int): Instant {
        val clampedOffset = offsetDays.coerceIn(1, 30)
        return weekStart().minus(clampedOffset.toLong() * 24 * 3600 * 1000, DateTimeUnit.MILLISECOND)
    }

    /**
     * Returns a timestamp in the next week.
     * [offsetHours] is clamped to [0, 6*24).
     */
    fun inNextWeek(offsetHours: Int): Instant {
        val clampedOffset = offsetHours.coerceIn(0, 6 * 24 - 1)
        return nextWeekStart().plus(clampedOffset.toLong() * 3600 * 1000, DateTimeUnit.MILLISECOND)
    }

    /** Builds a minimal [TrainingLog] with the given timestamp and totalVolume. */
    fun makeLog(timestamp: Instant, totalVolume: Int): TrainingLog = TrainingLog(
        logId = UUID.randomUUID().toString(),
        userId = "test-user",
        workoutPlanId = null,
        workoutDayName = "Test Day",
        timestamp = timestamp,
        origin = "Samsung_Galaxy_S23",
        duration = 3600,
        totalCalories = null,
        exercises = emptyList<ExerciseLog>(),
        totalVolume = totalVolume
    )

    // -------------------------------------------------------------------------
    // Scenario 1: Weekly volume equals sum of totalVolume for logs in current week
    // -------------------------------------------------------------------------
    test("Feature: v-trainer, Property 19: Weekly volume equals sum of totalVolume for logs in current week").config(
        invocations = 1
    ) {
        checkAll(
            1,
            Arb.list(Arb.int(100..5000), 1..10),
            Arb.list(Arb.int(0..167), 1..10)
        ) { volumes, offsets ->
            val ws = weekStart()
            val inWeekLogs = volumes.zip(offsets) { vol, offsetHours ->
                makeLog(inCurrentWeek(offsetHours), vol)
            }

            val result = WeeklyStatsCalculator.computeWeeklyVolume(inWeekLogs, ws)

            val expected = inWeekLogs.sumOf { it.totalVolume.toLong() }
            result shouldBe expected
        }
    }

    // -------------------------------------------------------------------------
    // Scenario 2: Weekly workout count equals number of logs in current week
    // -------------------------------------------------------------------------
    test("Feature: v-trainer, Property 19: Weekly workout count equals number of logs in current week").config(
        invocations = 1
    ) {
        checkAll(
            1,
            Arb.list(Arb.int(100..5000), 1..8),
            Arb.list(Arb.int(0..167), 1..8)
        ) { volumes, offsets ->
            val ws = weekStart()
            val inWeekLogs = volumes.zip(offsets) { vol, offsetHours ->
                makeLog(inCurrentWeek(offsetHours), vol)
            }

            val result = WeeklyStatsCalculator.computeWeeklyWorkoutCount(inWeekLogs, ws)

            result shouldBe inWeekLogs.size
        }
    }

    // -------------------------------------------------------------------------
    // Scenario 3: Logs from previous weeks are excluded from weekly stats
    // -------------------------------------------------------------------------
    test("Feature: v-trainer, Property 19: Logs from previous weeks are excluded from weekly stats").config(
        invocations = 1
    ) {
        checkAll(
            1,
            Arb.list(Arb.int(100..5000), 1..5),
            Arb.list(Arb.int(0..167), 1..5),
            Arb.list(Arb.int(100..5000), 1..5),
            Arb.list(Arb.int(1..30), 1..5)
        ) { inWeekVolumes, inWeekOffsets, prevVolumes, prevOffsets ->
            val ws = weekStart()
            val inWeekLogs = inWeekVolumes.zip(inWeekOffsets) { vol, offsetHours ->
                makeLog(inCurrentWeek(offsetHours), vol)
            }
            val prevWeekLogs = prevVolumes.zip(prevOffsets) { vol, offsetDays ->
                makeLog(inPreviousWeek(offsetDays), vol)
            }

            val allLogs = inWeekLogs + prevWeekLogs

            val volumeResult = WeeklyStatsCalculator.computeWeeklyVolume(allLogs, ws)
            val countResult = WeeklyStatsCalculator.computeWeeklyWorkoutCount(allLogs, ws)

            val expectedVolume = inWeekLogs.sumOf { it.totalVolume.toLong() }
            volumeResult shouldBe expectedVolume
            countResult shouldBe inWeekLogs.size
        }
    }

    // -------------------------------------------------------------------------
    // Scenario 4: Logs from the future (next week) are excluded from weekly stats
    // -------------------------------------------------------------------------
    test("Feature: v-trainer, Property 19: Logs from next week are excluded from weekly stats").config(
        invocations = 1
    ) {
        checkAll(
            1,
            Arb.list(Arb.int(100..5000), 1..5),
            Arb.list(Arb.int(0..167), 1..5),
            Arb.list(Arb.int(100..5000), 1..5),
            Arb.list(Arb.int(0..167), 1..5)
        ) { inWeekVolumes, inWeekOffsets, nextVolumes, nextOffsets ->
            val ws = weekStart()
            val inWeekLogs = inWeekVolumes.zip(inWeekOffsets) { vol, offsetHours ->
                makeLog(inCurrentWeek(offsetHours), vol)
            }
            val nextWeekLogs = nextVolumes.zip(nextOffsets) { vol, offsetHours ->
                makeLog(inNextWeek(offsetHours), vol)
            }

            val allLogs = inWeekLogs + nextWeekLogs

            val volumeResult = WeeklyStatsCalculator.computeWeeklyVolume(allLogs, ws)
            val countResult = WeeklyStatsCalculator.computeWeeklyWorkoutCount(allLogs, ws)

            val expectedVolume = inWeekLogs.sumOf { it.totalVolume.toLong() }
            volumeResult shouldBe expectedVolume
            countResult shouldBe inWeekLogs.size
        }
    }
})
