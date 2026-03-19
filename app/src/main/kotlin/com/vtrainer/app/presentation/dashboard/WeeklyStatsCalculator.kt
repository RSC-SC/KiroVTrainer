package com.vtrainer.app.presentation.dashboard

import com.vtrainer.app.domain.models.TrainingLog
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

/**
 * Pure utility for computing weekly training statistics.
 *
 * Extracted from [DashboardViewModel] so that the aggregation logic can be
 * unit-tested without Android framework dependencies (viewModelScope / Dispatchers.Main).
 *
 * Validates: Requirements 7.4, 10.5, 14.3
 */
object WeeklyStatsCalculator {

    /**
     * Returns the [Instant] representing the start of the current ISO week
     * (Monday 00:00:00 in the system timezone).
     *
     * Validates: Requirement 14.3
     */
    fun getStartOfCurrentWeek(
        clock: Clock = Clock.System,
        tz: TimeZone = TimeZone.currentSystemDefault()
    ): Instant {
        val today = clock.now().toLocalDateTime(tz).date
        val daysFromMonday = today.dayOfWeek.ordinal // 0=Mon … 6=Sun
        val monday = today.minus(daysFromMonday, DateTimeUnit.DAY)
        return monday.atStartOfDayIn(tz)
    }

    /**
     * Filters [logs] to only those whose [TrainingLog.timestamp] falls within the
     * current ISO week (i.e. >= [weekStart] and < [weekStart] + 7 days).
     *
     * Validates: Requirement 14.3
     */
    fun filterCurrentWeekLogs(
        logs: List<TrainingLog>,
        weekStart: Instant = getStartOfCurrentWeek(),
        tz: TimeZone = TimeZone.currentSystemDefault()
    ): List<TrainingLog> {
        val weekEnd = weekStart.plus(7, DateTimeUnit.DAY, tz)
        return logs.filter { it.timestamp >= weekStart && it.timestamp < weekEnd }
    }

    /**
     * Computes the total training volume for [logs] within the current ISO week.
     * Volume = sum of [TrainingLog.totalVolume] for all qualifying logs.
     *
     * Validates: Requirements 7.4, 14.3
     */
    fun computeWeeklyVolume(
        logs: List<TrainingLog>,
        weekStart: Instant = getStartOfCurrentWeek(),
        tz: TimeZone = TimeZone.currentSystemDefault()
    ): Long = filterCurrentWeekLogs(logs, weekStart, tz).sumOf { it.totalVolume.toLong() }

    /**
     * Computes the number of workouts completed within the current ISO week.
     *
     * Validates: Requirements 10.5, 14.3
     */
    fun computeWeeklyWorkoutCount(
        logs: List<TrainingLog>,
        weekStart: Instant = getStartOfCurrentWeek(),
        tz: TimeZone = TimeZone.currentSystemDefault()
    ): Int = filterCurrentWeekLogs(logs, weekStart, tz).size
}
