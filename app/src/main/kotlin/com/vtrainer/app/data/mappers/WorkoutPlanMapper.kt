package com.vtrainer.app.data.mappers

import com.vtrainer.app.data.local.entities.WorkoutPlanEntity
import com.vtrainer.app.domain.models.WorkoutPlan
import com.vtrainer.app.domain.models.TrainingDay
import com.vtrainer.app.domain.models.PlannedExercise
import com.vtrainer.app.domain.models.SyncStatus
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/**
 * Serializable wrapper for TrainingDay to enable JSON serialization.
 */
@Serializable
private data class TrainingDayDto(
    val dayName: String,
    val exercises: List<PlannedExerciseDto>
)

/**
 * Serializable wrapper for PlannedExercise to enable JSON serialization.
 */
@Serializable
private data class PlannedExerciseDto(
    val exerciseId: String,
    val order: Int,
    val sets: Int,
    val reps: Int,
    val restSeconds: Int,
    val notes: String?
)

/**
 * JSON serializer with lenient parsing.
 */
private val json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

/**
 * Maps WorkoutPlan domain model to WorkoutPlanEntity for Room database.
 */
fun WorkoutPlan.toEntity(syncStatus: SyncStatus = SyncStatus.SYNCED): WorkoutPlanEntity {
    val trainingDaysDto = trainingDays.map { day ->
        TrainingDayDto(
            dayName = day.dayName,
            exercises = day.exercises.map { exercise ->
                PlannedExerciseDto(
                    exerciseId = exercise.exerciseId,
                    order = exercise.order,
                    sets = exercise.sets,
                    reps = exercise.reps,
                    restSeconds = exercise.restSeconds,
                    notes = exercise.notes
                )
            }
        )
    }
    
    return WorkoutPlanEntity(
        planId = planId,
        userId = userId,
        name = name,
        description = description,
        trainingDaysJson = json.encodeToString(trainingDaysDto),
        createdAt = createdAt.toEpochMilliseconds(),
        updatedAt = updatedAt.toEpochMilliseconds(),
        syncStatus = syncStatus,
        lastSyncAttempt = null
    )
}

/**
 * Maps WorkoutPlanEntity from Room database to WorkoutPlan domain model.
 */
fun WorkoutPlanEntity.toDomain(): WorkoutPlan {
    val trainingDaysDto = json.decodeFromString<List<TrainingDayDto>>(trainingDaysJson)
    
    val trainingDays = trainingDaysDto.map { dto ->
        TrainingDay(
            dayName = dto.dayName,
            exercises = dto.exercises.map { exerciseDto ->
                PlannedExercise(
                    exerciseId = exerciseDto.exerciseId,
                    order = exerciseDto.order,
                    sets = exerciseDto.sets,
                    reps = exerciseDto.reps,
                    restSeconds = exerciseDto.restSeconds,
                    notes = exerciseDto.notes
                )
            }
        )
    }
    
    return WorkoutPlan(
        planId = planId,
        userId = userId,
        name = name,
        description = description,
        trainingDays = trainingDays,
        createdAt = Instant.fromEpochMilliseconds(createdAt),
        updatedAt = Instant.fromEpochMilliseconds(updatedAt)
    )
}
