package com.vtrainer.app.data.serialization

import com.vtrainer.app.domain.models.PlannedExercise
import com.vtrainer.app.domain.models.TrainingDay
import com.vtrainer.app.domain.models.WorkoutPlan
import kotlinx.datetime.Instant
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Serializer for WorkoutPlan domain model.
 * Handles conversion between WorkoutPlan objects and JSON strings.
 *
 * Validates: Requirements 19.1, 19.2, 19.3
 */
object WorkoutPlanSerializer {
    private val json = Json {
        prettyPrint = false
        ignoreUnknownKeys = true
    }

    /**
     * Serializes a WorkoutPlan to JSON string.
     * Requirement 19.3: provides a formatter to export WorkoutPlan objects to valid JSON.
     */
    fun toJson(workoutPlan: WorkoutPlan): String {
        val dto = workoutPlan.toDto()
        return json.encodeToString(dto)
    }

    /**
     * Alias for [toJson] used by the import/export feature.
     * Requirement 19.3, 19.5
     */
    fun exportToJson(plan: WorkoutPlan): String = toJson(plan)

    /**
     * Deserializes a JSON string to WorkoutPlan.
     * Throws on invalid JSON.
     */
    fun fromJson(jsonString: String): WorkoutPlan {
        val dto = json.decodeFromString<WorkoutPlanDto>(jsonString)
        return dto.toDomain()
    }

    /**
     * Parses a JSON string into a [WorkoutPlan], returning a [Result] with a descriptive
     * error message on failure.
     *
     * Requirement 19.1: parses valid JSON into a WorkoutPlan object.
     * Requirement 19.2: returns a descriptive error message for invalid JSON.
     */
    fun importFromJson(jsonString: String): Result<WorkoutPlan> {
        if (jsonString.isBlank()) {
            return Result.failure(IllegalArgumentException("JSON string must not be empty"))
        }
        return try {
            val dto = json.decodeFromString<WorkoutPlanDto>(jsonString)
            val errors = dto.validate()
            if (errors.isNotEmpty()) {
                Result.failure(IllegalArgumentException("Invalid workout plan: ${errors.joinToString("; ")}"))
            } else {
                Result.success(dto.toDomain())
            }
        } catch (e: SerializationException) {
            Result.failure(IllegalArgumentException("Invalid JSON format: ${e.message}"))
        } catch (e: IllegalArgumentException) {
            Result.failure(IllegalArgumentException("Invalid workout plan data: ${e.message}"))
        }
    }

    // DTOs for serialization
    @Serializable
    private data class WorkoutPlanDto(
        val planId: String = "",
        val userId: String = "",
        val name: String = "",
        val description: String? = null,
        val trainingDays: List<TrainingDayDto> = emptyList(),
        val createdAt: String = "",
        val updatedAt: String = ""
    ) {
        /** Returns a list of human-readable validation error messages, empty if valid. */
        fun validate(): List<String> {
            val errors = mutableListOf<String>()
            if (planId.isBlank()) errors.add("planId must not be blank")
            if (userId.isBlank()) errors.add("userId must not be blank")
            if (name.isBlank()) errors.add("name must not be blank")
            if (createdAt.isBlank()) errors.add("createdAt must not be blank")
            else runCatching { Instant.parse(createdAt) }.onFailure { errors.add("createdAt is not a valid ISO-8601 timestamp") }
            if (updatedAt.isBlank()) errors.add("updatedAt must not be blank")
            else runCatching { Instant.parse(updatedAt) }.onFailure { errors.add("updatedAt is not a valid ISO-8601 timestamp") }
            trainingDays.forEachIndexed { i, day ->
                if (day.dayName.isBlank()) errors.add("trainingDays[$i].dayName must not be blank")
                day.exercises.forEachIndexed { j, ex ->
                    if (ex.exerciseId.isBlank()) errors.add("trainingDays[$i].exercises[$j].exerciseId must not be blank")
                    if (ex.sets <= 0) errors.add("trainingDays[$i].exercises[$j].sets must be positive")
                    if (ex.reps <= 0) errors.add("trainingDays[$i].exercises[$j].reps must be positive")
                    if (ex.restSeconds < 0) errors.add("trainingDays[$i].exercises[$j].restSeconds must be non-negative")
                }
            }
            return errors
        }
    }

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
