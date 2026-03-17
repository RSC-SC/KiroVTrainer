package com.vtrainer.app.data.mappers

import com.vtrainer.app.data.local.entities.TrainingLogEntity
import com.vtrainer.app.domain.models.TrainingLog
import com.vtrainer.app.domain.models.ExerciseLog
import com.vtrainer.app.domain.models.SetLog
import com.vtrainer.app.domain.models.RecordType
import com.vtrainer.app.domain.models.SyncStatus
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/**
 * Serializable wrapper for ExerciseLog to enable JSON serialization.
 */
@Serializable
private data class ExerciseLogDto(
    val exerciseId: String,
    val sets: List<SetLogDto>,
    val totalVolume: Int,
    val isPersonalRecord: Boolean = false,
    val recordType: String? = null
)

/**
 * Serializable wrapper for SetLog to enable JSON serialization.
 */
@Serializable
private data class SetLogDto(
    val setNumber: Int,
    val reps: Int,
    val weight: Double,
    val restSeconds: Int,
    val heartRate: Int?,
    val completedAt: Long
)

/**
 * JSON serializer with lenient parsing.
 */
private val json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

/**
 * Maps TrainingLog domain model to TrainingLogEntity for Room database.
 */
fun TrainingLog.toEntity(syncStatus: SyncStatus = SyncStatus.SYNCED): TrainingLogEntity {
    val exercisesDto = exercises.map { exercise ->
        ExerciseLogDto(
            exerciseId = exercise.exerciseId,
            sets = exercise.sets.map { set ->
                SetLogDto(
                    setNumber = set.setNumber,
                    reps = set.reps,
                    weight = set.weight,
                    restSeconds = set.restSeconds,
                    heartRate = set.heartRate,
                    completedAt = set.completedAt.toEpochMilliseconds()
                )
            },
            totalVolume = exercise.totalVolume,
            isPersonalRecord = exercise.isPersonalRecord,
            recordType = exercise.recordType?.name
        )
    }
    
    return TrainingLogEntity(
        logId = logId,
        userId = userId,
        workoutPlanId = workoutPlanId,
        workoutDayName = workoutDayName,
        timestamp = timestamp.toEpochMilliseconds(),
        origin = origin,
        exercisesJson = json.encodeToString(exercisesDto),
        totalVolume = totalVolume,
        totalCalories = totalCalories,
        duration = duration,
        syncStatus = syncStatus,
        lastSyncAttempt = null
    )
}

/**
 * Maps TrainingLogEntity from Room database to TrainingLog domain model.
 */
fun TrainingLogEntity.toDomain(): TrainingLog {
    val exercisesDto = json.decodeFromString<List<ExerciseLogDto>>(exercisesJson)
    
    val exercises = exercisesDto.map { dto ->
        ExerciseLog(
            exerciseId = dto.exerciseId,
            sets = dto.sets.map { setDto ->
                SetLog(
                    setNumber = setDto.setNumber,
                    reps = setDto.reps,
                    weight = setDto.weight,
                    restSeconds = setDto.restSeconds,
                    heartRate = setDto.heartRate,
                    completedAt = Instant.fromEpochMilliseconds(setDto.completedAt)
                )
            },
            totalVolume = dto.totalVolume,
            isPersonalRecord = dto.isPersonalRecord,
            recordType = dto.recordType?.let { RecordType.valueOf(it) }
        )
    }
    
    return TrainingLog(
        logId = logId,
        userId = userId,
        workoutPlanId = workoutPlanId,
        workoutDayName = workoutDayName,
        timestamp = Instant.fromEpochMilliseconds(timestamp),
        origin = origin,
        duration = duration,
        totalCalories = totalCalories,
        exercises = exercises,
        totalVolume = totalVolume
    )
}
