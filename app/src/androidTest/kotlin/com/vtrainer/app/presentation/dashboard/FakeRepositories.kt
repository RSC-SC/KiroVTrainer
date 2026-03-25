package com.vtrainer.app.presentation.dashboard

import com.vtrainer.app.data.repositories.TrainingLogRepository
import com.vtrainer.app.data.repositories.WorkoutRepository
import com.vtrainer.app.domain.models.TrainingLog
import com.vtrainer.app.domain.models.WorkoutPlan
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Minimal fake [WorkoutRepository] for UI tests — returns an empty list by default.
 * The DashboardViewModel is overridden in tests to expose a fixed [DashboardState],
 * so these fakes are only needed to satisfy the constructor.
 */
class FakeWorkoutRepository : WorkoutRepository {
    override fun getWorkoutPlans(): Flow<List<WorkoutPlan>> = flowOf(emptyList())
    override suspend fun saveWorkoutPlan(plan: WorkoutPlan): Result<Unit> = Result.success(Unit)
    override suspend fun deleteWorkoutPlan(planId: String): Result<Unit> = Result.success(Unit)
}

/**
 * Minimal fake [TrainingLogRepository] for UI tests — returns an empty list by default.
 */
class FakeTrainingLogRepository : TrainingLogRepository {
    override suspend fun saveTrainingLog(log: TrainingLog): Result<Unit> = Result.success(Unit)
    override fun getTrainingHistory(): Flow<List<TrainingLog>> = flowOf(emptyList())
    override suspend fun syncPendingLogs(): Result<Int> = Result.success(0)
    override fun getPendingSyncCount(): Flow<Int> = flowOf(0)
}
