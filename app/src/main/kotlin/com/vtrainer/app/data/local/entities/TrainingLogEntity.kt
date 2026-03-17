package com.vtrainer.app.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.vtrainer.app.domain.models.SyncStatus

/**
 * Room entity for storing training logs locally.
 * Supports offline-first architecture with sync status tracking.
 */
@Entity(tableName = "training_logs")
data class TrainingLogEntity(
    @PrimaryKey
    val logId: String,
    val userId: String,
    val workoutPlanId: String?,
    val workoutDayName: String,
    val timestamp: Long,
    val origin: String,
    val exercisesJson: String, // JSON serialized list of ExerciseLog objects
    val totalVolume: Int,
    val totalCalories: Int?,
    val duration: Int,
    val syncStatus: SyncStatus,
    val lastSyncAttempt: Long?
)
