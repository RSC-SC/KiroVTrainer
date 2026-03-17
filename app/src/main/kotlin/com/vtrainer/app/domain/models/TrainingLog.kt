package com.vtrainer.app.domain.models

import kotlinx.datetime.Instant

/**
 * Domain model representing a completed training session.
 */
data class TrainingLog(
    val logId: String,
    val userId: String,
    val workoutPlanId: String?,
    val workoutDayName: String,
    val timestamp: Instant,
    val origin: String,
    val duration: Int,
    val totalCalories: Int?,
    val exercises: List<ExerciseLog>,
    val totalVolume: Int
)

/**
 * Represents a logged exercise within a training session.
 */
data class ExerciseLog(
    val exerciseId: String,
    val sets: List<SetLog>,
    val totalVolume: Int,
    val isPersonalRecord: Boolean = false,
    val recordType: RecordType? = null
)

/**
 * Represents a single set within an exercise log.
 */
data class SetLog(
    val setNumber: Int,
    val reps: Int,
    val weight: Double,
    val restSeconds: Int,
    val heartRate: Int?,
    val completedAt: Instant
)
