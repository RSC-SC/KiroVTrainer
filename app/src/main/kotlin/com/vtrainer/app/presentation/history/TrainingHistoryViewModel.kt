package com.vtrainer.app.presentation.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vtrainer.app.data.repositories.TrainingLogRepository
import com.vtrainer.app.domain.models.TrainingLog
import com.vtrainer.app.presentation.dashboard.WeeklyStatsCalculator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

/**
 * Data for a single week's training progress.
 *
 * Validates: Requirement 7.4
 */
data class WeeklyProgressData(
    /** Human-readable label, e.g. "Week 12". */
    val weekLabel: String,
    /** Number of workouts completed in this week. */
    val workoutCount: Int,
    /** Total training volume (sum of weight × reps) for this week. */
    val totalVolume: Long
)

/**
 * Data for a single month's training progress.
 *
 * Validates: Requirement 7.4
 */
data class MonthlyProgressData(
    /** Human-readable label, e.g. "Mar 2026". */
    val monthLabel: String,
    /** Number of workouts completed in this month. */
    val workoutCount: Int,
    /** Total training volume (sum of weight × reps) for this month. */
    val totalVolume: Long
)

/**
 * UI state for the Training History screen.
 *
 * Validates: Requirements 7.1, 7.2, 7.3, 7.4, 7.5, 12.3, 12.5
 */
data class TrainingHistoryState(
    /** Training logs sorted by timestamp descending (most recent first). Requirement 7.1 */
    val logs: List<TrainingLog> = emptyList(),
    /** Weekly progress data for the last 8 weeks. Requirement 7.4 */
    val weeklyProgress: List<WeeklyProgressData> = emptyList(),
    /** Monthly progress data for the last 6 months. Requirement 7.4 */
    val monthlyProgress: List<MonthlyProgressData> = emptyList(),
    /** True while data is being loaded. */
    val isLoading: Boolean = true,
    /** Non-null error message when a load operation fails. */
    val error: String? = null,
    /** True when the device has no network connectivity. Requirement 12.3 */
    val isOffline: Boolean = false,
    /** Number of training logs waiting to be synced to Firestore. Requirement 12.5 */
    val pendingSyncCount: Int = 0,
    /** True while a manual sync operation is in progress. Requirement 12.5 */
    val isSyncing: Boolean = false
)

/**
 * ViewModel for the Training History screen.
 *
 * Responsibilities:
 * - Load training logs from [TrainingLogRepository] as a Flow
 * - Sort logs by timestamp descending (Requirement 7.1)
 * - Calculate weekly progress for the last 8 weeks (Requirement 7.4)
 * - Calculate monthly progress for the last 6 months (Requirement 7.4)
 * - Highlight personal records — logs where any [ExerciseLog] has isPersonalRecord=true (Requirement 7.5)
 * - Expose [TrainingHistoryState] as [StateFlow]
 *
 * Validates: Requirements 7.1, 7.2, 7.3, 7.4, 7.5
 */
class TrainingHistoryViewModel(
    private val trainingLogRepository: TrainingLogRepository,
    private val clock: Clock = Clock.System,
    private val tz: TimeZone = TimeZone.currentSystemDefault()
) : ViewModel() {

    private val _state = MutableStateFlow(TrainingHistoryState())
    val state: StateFlow<TrainingHistoryState> = _state.asStateFlow()

    init {
        loadHistory()
        observePendingSyncCount()
    }

    /**
     * Triggers a full reload of training history data.
     */
    fun refresh() {
        loadHistory()
    }

    /**
     * Dismisses the current error message.
     */
    fun dismissError() {
        _state.value = _state.value.copy(error = null)
    }

    /**
     * Manually triggers synchronization of all pending training logs.
     *
     * Validates: Requirement 12.5
     */
    fun retrySyncPendingLogs() {
        if (_state.value.isSyncing) return
        viewModelScope.launch {
            _state.value = _state.value.copy(isSyncing = true, error = null)
            val result = trainingLogRepository.syncPendingLogs()
            _state.value = _state.value.copy(
                isSyncing = false,
                error = if (result.isFailure)
                    result.exceptionOrNull()?.message ?: "Falha ao sincronizar treinos"
                else null
            )
        }
    }

    private fun loadHistory() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)

            trainingLogRepository.getTrainingHistory()
                .catch { e ->
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to load training history",
                        isOffline = true
                    )
                }
                .collect { logs ->
                    // Req 7.1: sort by timestamp descending
                    val sortedLogs = logs.sortedByDescending { it.timestamp }

                    val weeklyProgress = calculateWeeklyProgress(sortedLogs)
                    val monthlyProgress = calculateMonthlyProgress(sortedLogs)

                    _state.value = _state.value.copy(
                        logs = sortedLogs,
                        weeklyProgress = weeklyProgress,
                        monthlyProgress = monthlyProgress,
                        isLoading = false,
                        error = null
                    )
                }
        }
    }

    /**
     * Observes the count of pending-sync training logs reactively.
     *
     * Validates: Requirement 12.5
     */
    private fun observePendingSyncCount() {
        viewModelScope.launch {
            trainingLogRepository.getPendingSyncCount()
                .catch { /* ignore — best-effort */ }
                .collect { count ->
                    _state.value = _state.value.copy(pendingSyncCount = count)
                }
        }
    }

    /**
     * Calculates weekly progress for the last 8 weeks.
     *
     * Validates: Requirement 7.4
     */
    private fun calculateWeeklyProgress(logs: List<TrainingLog>): List<WeeklyProgressData> {
        val currentWeekStart = WeeklyStatsCalculator.getStartOfCurrentWeek(clock, tz)

        return (0 until 8).map { weeksAgo ->
            val weekStart = currentWeekStart.minus(weeksAgo * 7, DateTimeUnit.DAY, tz)
            val weekEnd = weekStart.plus(7, DateTimeUnit.DAY, tz)

            val weekLogs = logs.filter { it.timestamp >= weekStart && it.timestamp < weekEnd }

            val localDate = weekStart.toLocalDateTime(tz).date
            // ISO week number: approximate using day-of-year
            val weekOfYear = ((localDate.dayOfYear - 1) / 7) + 1

            WeeklyProgressData(
                weekLabel = "Week $weekOfYear",
                workoutCount = weekLogs.size,
                totalVolume = weekLogs.sumOf { it.totalVolume.toLong() }
            )
        }.reversed() // oldest first for chart display
    }

    /**
     * Calculates monthly progress for the last 6 months.
     *
     * Validates: Requirement 7.4
     */
    private fun calculateMonthlyProgress(logs: List<TrainingLog>): List<MonthlyProgressData> {
        val today = clock.now().toLocalDateTime(tz).date

        return (0 until 6).map { monthsAgo ->
            val targetMonth = today.minus(monthsAgo, DateTimeUnit.MONTH)
            val year = targetMonth.year
            val month = targetMonth.monthNumber

            val monthLogs = logs.filter {
                val logDate = it.timestamp.toLocalDateTime(tz).date
                logDate.year == year && logDate.monthNumber == month
            }

            val monthName = targetMonth.month.name
                .lowercase()
                .replaceFirstChar { it.uppercase() }
                .take(3)

            MonthlyProgressData(
                monthLabel = "$monthName $year",
                workoutCount = monthLogs.size,
                totalVolume = monthLogs.sumOf { it.totalVolume.toLong() }
            )
        }.reversed() // oldest first for chart display
    }
}

/**
 * Returns true if this training log contains at least one personal record.
 *
 * Validates: Requirement 7.5
 */
fun TrainingLog.hasPersonalRecord(): Boolean =
    exercises.any { it.isPersonalRecord }
