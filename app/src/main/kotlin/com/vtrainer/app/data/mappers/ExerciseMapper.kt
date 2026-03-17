package com.vtrainer.app.data.mappers

import com.vtrainer.app.data.local.entities.ExerciseEntity
import com.vtrainer.app.domain.models.Exercise
import com.vtrainer.app.domain.models.MuscleGroup
import com.vtrainer.app.domain.models.MediaType
import com.vtrainer.app.domain.models.Difficulty
import com.vtrainer.app.domain.models.Equipment
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/**
 * JSON serializer with lenient parsing.
 */
private val json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

/**
 * Maps Exercise domain model to ExerciseEntity for Room database.
 */
fun Exercise.toEntity(cachedAt: Long = System.currentTimeMillis()): ExerciseEntity {
    return ExerciseEntity(
        exerciseId = exerciseId,
        name = name,
        muscleGroup = muscleGroup.name,
        secondaryMusclesJson = json.encodeToString(secondaryMuscles.map { it.name }),
        instructions = instructions,
        mediaUrl = mediaUrl,
        mediaType = mediaType.name,
        difficulty = difficulty.name,
        equipmentJson = json.encodeToString(equipment.map { it.name }),
        cachedAt = cachedAt
    )
}

/**
 * Maps ExerciseEntity from Room database to Exercise domain model.
 */
fun ExerciseEntity.toDomain(): Exercise {
    val secondaryMuscleNames = json.decodeFromString<List<String>>(secondaryMusclesJson)
    val equipmentNames = json.decodeFromString<List<String>>(equipmentJson)
    
    return Exercise(
        exerciseId = exerciseId,
        name = name,
        muscleGroup = MuscleGroup.valueOf(muscleGroup),
        secondaryMuscles = secondaryMuscleNames.map { MuscleGroup.valueOf(it) },
        instructions = instructions,
        mediaUrl = mediaUrl,
        mediaType = MediaType.valueOf(mediaType),
        difficulty = Difficulty.valueOf(difficulty),
        equipment = equipmentNames.map { Equipment.valueOf(it) }
    )
}
