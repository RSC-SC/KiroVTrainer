package com.vtrainer.app.domain.models

import kotlinx.datetime.Instant

/**
 * Domain model representing a workout plan with multiple training days.
 */
data class WorkoutPlan(
    val planId: String,
    val userId: String,
    val name: String,
    val description: String?,
    val trainingDays: List<TrainingDay>,
    val createdAt: Instant,
    val updatedAt: Instant
)

/**
 * Represents a single training day within a workout plan.
 */
data class TrainingDay(
    val dayName: String,
    val exercises: List<PlannedExercise>
)

/**
 * Represents a planned exercise with its configuration.
 */
data class PlannedExercise(
    val exerciseId: String,
    val order: Int,
    val sets: Int,
    val reps: Int,
    val restSeconds: Int,
    val notes: String?
)
