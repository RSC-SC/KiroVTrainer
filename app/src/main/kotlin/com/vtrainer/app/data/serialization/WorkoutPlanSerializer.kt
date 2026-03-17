package com.vtrainer.app.data.serialization

import com.vtrainer.app.domain.models.PlannedExercise
import com.vtrainer.app.domain.models.TrainingDay
import com.vtrainer.app.domain.models.WorkoutPlan
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Serializer for WorkoutPlan domain model.
 * Handles conversion between WorkoutPlan objects and JSON strings.
 */
object WorkoutPlanSerializer {
    private val json = Json {
        prettyPrint = false
        ignoreUnknownKeys = true
    }

    /**
     * Serializes a WorkoutPlan to JSON string.
     */
    fun toJson(workoutPlan: WorkoutPlan): String {
        val dto = workoutPlan.toDto()
        return json.encodeToString(dto)
    }

    /**
     * Deserializes a JSON string to WorkoutPlan.
     */
    fun fromJson(jsonString: String): WorkoutPlan {
        val dto = json.decodeFromString<WorkoutPlanDto>(jsonString)
        return dto.toDomain()
    }

    // DTOs for serialization
    @Serializable
    private data class WorkoutPlanDto(
        val planId: String,
        val userId: String,
        val name: String,
        val description: String?,
        val trainingDays: List<TrainingDayDto>,
        val createdAt: String,
        val updatedAt: String
    )

    @Serializable
    private data class TrainingDayDto(
        val dayName: String,
        val exercises: List<PlannedExerciseDto>
    )

    @Serializable
    private data class PlannedExerciseDto(
        val exerciseId: String,
        val order: Int,
        val sets: Int,
        val reps: Int,
        val restSeconds: Int,
        val notes: String?
    )

    // Extension functions for conversion
    private fun WorkoutPlan.toDto() = WorkoutPlanDto(
        planId = planId,
        userId = userId,
        name = name,
        description = description,
        trainingDays = trainingDays.map { it.toDto() },
        createdAt = createdAt.toString(),
        updatedAt = updatedAt.toString()
    )

    private fun TrainingDay.toDto() = TrainingDayDto(
        dayName = dayName,
        exercises = exercises.map { it.toDto() }
    )

    private fun PlannedExercise.toDto() = PlannedExerciseDto(
        exerciseId = exerciseId,
        order = order,
        sets = sets,
        reps = reps,
        restSeconds = restSeconds,
        notes = notes
    )

    private fun WorkoutPlanDto.toDomain() = WorkoutPlan(
        planId = planId,
        userId = userId,
        name = name,
        description = description,
        trainingDays = trainingDays.map { it.toDomain() },
        createdAt = Instant.parse(createdAt),
        updatedAt = Instant.parse(updatedAt)
    )

    private fun TrainingDayDto.toDomain() = TrainingDay(
        dayName = dayName,
        exercises = exercises.map { it.toDomain() }
    )

    private fun PlannedExerciseDto.toDomain() = PlannedExercise(
        exerciseId = exerciseId,
        order = order,
        sets = sets,
        reps = reps,
        restSeconds = restSeconds,
        notes = notes
    )
}
