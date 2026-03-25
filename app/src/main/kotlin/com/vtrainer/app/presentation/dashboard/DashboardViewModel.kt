package com.vtrainer.app.presentation.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.vtrainer.app.data.repositories.TrainingLogRepository
import com.vtrainer.app.data.repositories.WorkoutRepository
import com.vtrainer.app.domain.models.PersonalRecord
import com.vtrainer.app.domain.models.WorkoutPlan
import com.vtrainer.app.util.VTrainerLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime

/**
 * UI state for the Dashboard screen.
 *
 * Validates: Requirements 14.1, 14.2, 14.3, 14.4, 12.3, 12.5
 */
data class DashboardState(
    /** Next scheduled workout plan, or null if none is available. Requirement 14.1 */
    val nextWorkout: WorkoutPlan? = null,
    /** Number of workouts completed in the current week. Requirement 14.3 */
    val weeklyWorkoutCount: Int = 0,
    /** Total training volume (sum of weight × reps) for the current week. Requirement 14.3 */
    val weeklyTotalVolume: Long = 0L,
    /** Most recent personal records from the user's profile. Requirement 14.4 */
    val recentPersonalRecords: List<PersonalRecord> = emptyList(),
    /** True while data is being loaded. Requirement 14.2 */
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
 * ViewModel for the Dashboard screen.
 *
 * Responsibilities:
 * - Load the next scheduled workout from [WorkoutRepository]
 * - Calculate weekly stats (workout count and total volume) from [TrainingLogRepository]
 * - Load recent personal records from the Firestore user document
 * - Expose the aggregated [DashboardState] as a [StateFlow]
 *
 * Validates: Requirements 14.1, 14.2, 14.3, 14.4
 */
class DashboardViewModel(
    private val workoutRepository: WorkoutRepository,
    private val trainingLogRepository: TrainingLogRepository,
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) : ViewModel() {

    private val _state = MutableStateFlow(DashboardState())
    val state: StateFlow<DashboardState> = _state.asStateFlow()

    init {
        // Only load if a user is already authenticated (avoids crash before login)
        if (auth.currentUser != null) {
            loadDashboard()
            observePendingSyncCount()
        } else {
            _state.value = _state.value.copy(isLoading = false)
        }
    }

    /**
     * Triggers a full reload of all dashboard data.
     * Can be called to refresh after a workout is completed.
     */
    fun refresh() {
        loadDashboard()
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
            if (result.isSuccess) {
                val syncedCount = result.getOrDefault(0)
                VTrainerLogger.logSyncSuccess("dashboard_manual_sync", syncedCount)
            } else {
                VTrainerLogger.logSyncFailure(
                    operation = "dashboard_manual_sync",
                    errorCode = result.exceptionOrNull()?.javaClass?.simpleName ?: "UNKNOWN"
                )
            }
            _state.value = _state.value.copy(
                isSyncing = false,
                error = if (result.isFailure)
                    result.exceptionOrNull()?.message ?: "Falha ao sincronizar treinos"
                else null
            )
            // Refresh pending count after sync attempt
            observePendingSyncCount()
        }
    }

    private fun loadDashboard() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)

            // Load personal records from Firestore user document (one-shot)
            val personalRecords = loadPersonalRecords()

            // Combine workout plans and training history reactively
            combine(
                workoutRepository.getWorkoutPlans(),
                trainingLogRepository.getTrainingHistory()
            ) { plans, logs ->
                val nextWorkout = plans.firstOrNull()

                // Weekly stats: logs from the start of the current ISO week
                val weekStart = WeeklyStatsCalculator.getStartOfCurrentWeek()
                val weeklyWorkoutCount = WeeklyStatsCalculator.computeWeeklyWorkoutCount(logs, weekStart)
                val weeklyTotalVolume = WeeklyStatsCalculator.computeWeeklyVolume(logs, weekStart)

                _state.value.copy(
                    nextWorkout = nextWorkout,
                    weeklyWorkoutCount = weeklyWorkoutCount,
                    weeklyTotalVolume = weeklyTotalVolume,
                    recentPersonalRecords = personalRecords,
                    isLoading = false,
                    error = null
                )
            }
                .catch { e ->
                    VTrainerLogger.logNetworkError(
                        context = "DashboardViewModel.loadDashboard",
                        errorCode = e.javaClass.simpleName,
                        message = "Failed to load dashboard data"
                    )
                    emit(
                        _state.value.copy(
                            isLoading = false,
                            error = e.message ?: "Failed to load dashboard data",
                            isOffline = true
                        )
                    )
                }
                .collect { newState ->
                    _state.value = newState
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
     * Loads personal records from the Firestore user document.
     * Returns an empty list if the user is not authenticated or if the document
     * does not contain personal records yet.
     *
     * Validates: Requirement 14.4
     */
    private suspend fun loadPersonalRecords(): List<PersonalRecord> {
        val userId = auth.currentUser?.uid ?: return emptyList()
        return try {
            val userDoc = firestore.collection("users")
                .document(userId)
                .get()
                .await()

            @Suppress("UNCHECKED_CAST")
            val recordsMap = userDoc.get("personalRecords") as? Map<String, Map<String, Any>>
                ?: return emptyList()

            recordsMap.map { (exerciseId, data) ->
                PersonalRecord(
                    exerciseId = exerciseId,
                    maxWeight = (data["maxWeight"] as? Number)?.toDouble() ?: 0.0,
                    maxVolume = (data["maxVolume"] as? Number)?.toInt() ?: 0,
                    achievedAt = kotlinx.datetime.Instant.fromEpochMilliseconds(
                        (data["achievedAt"] as? com.google.firebase.Timestamp)
                            ?.toDate()?.time ?: 0L
                    )
                )
            }
                .sortedByDescending { it.achievedAt }
                .take(5) // Show only the 5 most recent records on the dashboard
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Returns the [kotlinx.datetime.Instant] representing the start of the current
     * ISO week (Monday 00:00:00 UTC) so that weekly stats are calculated correctly.
     *
     * Validates: Requirement 14.3
     *
     * @deprecated Use [WeeklyStatsCalculator.getStartOfCurrentWeek] instead.
     */
    private fun getStartOfCurrentWeek(): kotlinx.datetime.Instant =
        WeeklyStatsCalculator.getStartOfCurrentWeek()
}
