package com.vtrainer.app.data.mappers

import com.vtrainer.app.domain.models.*
import kotlinx.datetime.Instant
import org.junit.Test
import org.junit.Assert.*

/**
 * Unit tests for mapper functions between domain models and Room entities.
 * Validates bidirectional mapping and JSON serialization/deserialization.
 */
class MapperTest {

    @Test
    fun `WorkoutPlan to Entity and back preserves all data`() {
        // Given: A complete WorkoutPlan domain model
        val originalPlan = WorkoutPlan(
            planId = "plan-123",
            userId = "user-456",
            name = "Treino ABC",
            description = "Divisão de 3 dias",
            trainingDays = listOf(
                TrainingDay(
                    dayName = "Treino A - Peito",
                    exercises = listOf(
                        PlannedExercise(
                            exerciseId = "supino_reto",
                            order = 1,
                            sets = 4,
                            reps = 12,
                            restSeconds = 60,
                            notes = "Foco em controle"
                        ),
                        PlannedExercise(
                            exerciseId = "supino_inclinado",
                            order = 2,
                            sets = 3,
                            reps = 10,
                            restSeconds = 60,
                            notes = null
                        )
                    )
                ),
                TrainingDay(
                    dayName = "Treino B - Costas",
                    exercises = listOf(
                        PlannedExercise(
                            exerciseId = "barra_fixa",
                            order = 1,
                            sets = 4,
                            reps = 8,
                            restSeconds = 90,
                            notes = "Pegada pronada"
                        )
                    )
                )
            ),
            createdAt = Instant.fromEpochMilliseconds(1710000000000),
            updatedAt = Instant.fromEpochMilliseconds(1710086400000)
        )

        // When: Converting to entity and back to domain
        val entity = originalPlan.toEntity(SyncStatus.SYNCED)
        val convertedPlan = entity.toDomain()

        // Then: All data should be preserved
        assertEquals(originalPlan.planId, convertedPlan.planId)
        assertEquals(originalPlan.userId, convertedPlan.userId)
        assertEquals(originalPlan.name, convertedPlan.name)
        assertEquals(originalPlan.description, convertedPlan.description)
        assertEquals(originalPlan.trainingDays.size, convertedPlan.trainingDays.size)
        assertEquals(originalPlan.createdAt, convertedPlan.createdAt)
        assertEquals(originalPlan.updatedAt, convertedPlan.updatedAt)

        // Verify training days
        originalPlan.trainingDays.forEachIndexed { index, originalDay ->
            val convertedDay = convertedPlan.trainingDays[index]
            assertEquals(originalDay.dayName, convertedDay.dayName)
            assertEquals(originalDay.exercises.size, convertedDay.exercises.size)

            // Verify exercises
            originalDay.exercises.forEachIndexed { exIndex, originalExercise ->
                val convertedExercise = convertedDay.exercises[exIndex]
                assertEquals(originalExercise.exerciseId, convertedExercise.exerciseId)
                assertEquals(originalExercise.order, convertedExercise.order)
                assertEquals(originalExercise.sets, convertedExercise.sets)
                assertEquals(originalExercise.reps, convertedExercise.reps)
                assertEquals(originalExercise.restSeconds, convertedExercise.restSeconds)
                assertEquals(originalExercise.notes, convertedExercise.notes)
            }
        }
    }

    @Test
    fun `TrainingLog to Entity and back preserves all data`() {
        // Given: A complete TrainingLog domain model
        val originalLog = TrainingLog(
            logId = "log-789",
            userId = "user-456",
            workoutPlanId = "plan-123",
            workoutDayName = "Treino A - Peito",
            timestamp = Instant.fromEpochMilliseconds(1710172800000),
            origin = "Galaxy_Watch_4",
            duration = 3600,
            totalCalories = 450,
            exercises = listOf(
                ExerciseLog(
                    exerciseId = "supino_reto",
                    sets = listOf(
                        SetLog(
                            setNumber = 1,
                            reps = 12,
                            weight = 60.0,
                            restSeconds = 60,
                            heartRate = 145,
                            completedAt = Instant.fromEpochMilliseconds(1710173100000)
                        ),
                        SetLog(
                            setNumber = 2,
                            reps = 10,
                            weight = 65.0,
                            restSeconds = 60,
                            heartRate = 152,
                            completedAt = Instant.fromEpochMilliseconds(1710173220000)
                        )
                    ),
                    totalVolume = 1370,
                    isPersonalRecord = true,
                    recordType = RecordType.MAX_WEIGHT
                )
            ),
            totalVolume = 1370
        )

        // When: Converting to entity and back to domain
        val entity = originalLog.toEntity(SyncStatus.SYNCED)
        val convertedLog = entity.toDomain()

        // Then: All data should be preserved
        assertEquals(originalLog.logId, convertedLog.logId)
        assertEquals(originalLog.userId, convertedLog.userId)
        assertEquals(originalLog.workoutPlanId, convertedLog.workoutPlanId)
        assertEquals(originalLog.workoutDayName, convertedLog.workoutDayName)
        assertEquals(originalLog.timestamp, convertedLog.timestamp)
        assertEquals(originalLog.origin, convertedLog.origin)
        assertEquals(originalLog.duration, convertedLog.duration)
        assertEquals(originalLog.totalCalories, convertedLog.totalCalories)
        assertEquals(originalLog.totalVolume, convertedLog.totalVolume)
        assertEquals(originalLog.exercises.size, convertedLog.exercises.size)

        // Verify exercises
        originalLog.exercises.forEachIndexed { index, originalExercise ->
            val convertedExercise = convertedLog.exercises[index]
            assertEquals(originalExercise.exerciseId, convertedExercise.exerciseId)
            assertEquals(originalExercise.totalVolume, convertedExercise.totalVolume)
            assertEquals(originalExercise.isPersonalRecord, convertedExercise.isPersonalRecord)
            assertEquals(originalExercise.recordType, convertedExercise.recordType)
            assertEquals(originalExercise.sets.size, convertedExercise.sets.size)

            // Verify sets
            originalExercise.sets.forEachIndexed { setIndex, originalSet ->
                val convertedSet = convertedExercise.sets[setIndex]
                assertEquals(originalSet.setNumber, convertedSet.setNumber)
                assertEquals(originalSet.reps, convertedSet.reps)
                assertEquals(originalSet.weight, convertedSet.weight, 0.001)
                assertEquals(originalSet.restSeconds, convertedSet.restSeconds)
                assertEquals(originalSet.heartRate, convertedSet.heartRate)
                assertEquals(originalSet.completedAt, convertedSet.completedAt)
            }
        }
    }

    @Test
    fun `Exercise to Entity and back preserves all data`() {
        // Given: A complete Exercise domain model
        val originalExercise = Exercise(
            exerciseId = "supino_reto",
            name = "Supino Reto",
            muscleGroup = MuscleGroup.CHEST,
            secondaryMuscles = listOf(MuscleGroup.TRICEPS, MuscleGroup.SHOULDERS),
            instructions = "Deite-se no banco e empurre a barra para cima",
            mediaUrl = "https://storage.googleapis.com/v-trainer/exercises/supino_reto.gif",
            mediaType = MediaType.GIF,
            difficulty = Difficulty.INTERMEDIATE,
            equipment = listOf(Equipment.BARBELL, Equipment.BENCH)
        )

        // When: Converting to entity and back to domain
        val entity = originalExercise.toEntity(cachedAt = 1710000000000)
        val convertedExercise = entity.toDomain()

        // Then: All data should be preserved
        assertEquals(originalExercise.exerciseId, convertedExercise.exerciseId)
        assertEquals(originalExercise.name, convertedExercise.name)
        assertEquals(originalExercise.muscleGroup, convertedExercise.muscleGroup)
        assertEquals(originalExercise.secondaryMuscles.size, convertedExercise.secondaryMuscles.size)
        assertEquals(originalExercise.secondaryMuscles, convertedExercise.secondaryMuscles)
        assertEquals(originalExercise.instructions, convertedExercise.instructions)
        assertEquals(originalExercise.mediaUrl, convertedExercise.mediaUrl)
        assertEquals(originalExercise.mediaType, convertedExercise.mediaType)
        assertEquals(originalExercise.difficulty, convertedExercise.difficulty)
        assertEquals(originalExercise.equipment.size, convertedExercise.equipment.size)
        assertEquals(originalExercise.equipment, convertedExercise.equipment)
    }

    @Test
    fun `WorkoutPlan with empty training days converts correctly`() {
        // Given: A WorkoutPlan with no training days
        val plan = WorkoutPlan(
            planId = "plan-empty",
            userId = "user-456",
            name = "Empty Plan",
            description = null,
            trainingDays = emptyList(),
            createdAt = Instant.fromEpochMilliseconds(1710000000000),
            updatedAt = Instant.fromEpochMilliseconds(1710000000000)
        )

        // When: Converting to entity and back
        val entity = plan.toEntity()
        val converted = entity.toDomain()

        // Then: Empty list should be preserved
        assertEquals(0, converted.trainingDays.size)
        assertEquals(plan.planId, converted.planId)
    }

    @Test
    fun `TrainingLog with null optional fields converts correctly`() {
        // Given: A TrainingLog with null optional fields
        val log = TrainingLog(
            logId = "log-minimal",
            userId = "user-456",
            workoutPlanId = null,
            workoutDayName = "Custom Workout",
            timestamp = Instant.fromEpochMilliseconds(1710172800000),
            origin = "Mobile_App",
            duration = 1800,
            totalCalories = null,
            exercises = listOf(
                ExerciseLog(
                    exerciseId = "push_up",
                    sets = listOf(
                        SetLog(
                            setNumber = 1,
                            reps = 20,
                            weight = 0.0,
                            restSeconds = 30,
                            heartRate = null,
                            completedAt = Instant.fromEpochMilliseconds(1710173100000)
                        )
                    ),
                    totalVolume = 0,
                    isPersonalRecord = false,
                    recordType = null
                )
            ),
            totalVolume = 0
        )

        // When: Converting to entity and back
        val entity = log.toEntity()
        val converted = entity.toDomain()

        // Then: Null values should be preserved
        assertNull(converted.workoutPlanId)
        assertNull(converted.totalCalories)
        assertNull(converted.exercises[0].recordType)
        assertNull(converted.exercises[0].sets[0].heartRate)
    }

    @Test
    fun `Exercise with empty lists converts correctly`() {
        // Given: An Exercise with empty secondary muscles and equipment
        val exercise = Exercise(
            exerciseId = "plank",
            name = "Prancha",
            muscleGroup = MuscleGroup.CORE,
            secondaryMuscles = emptyList(),
            instructions = "Mantenha a posição",
            mediaUrl = "https://example.com/plank.gif",
            mediaType = MediaType.GIF,
            difficulty = Difficulty.BEGINNER,
            equipment = emptyList()
        )

        // When: Converting to entity and back
        val entity = exercise.toEntity()
        val converted = entity.toDomain()

        // Then: Empty lists should be preserved
        assertEquals(0, converted.secondaryMuscles.size)
        assertEquals(0, converted.equipment.size)
        assertEquals(exercise.exerciseId, converted.exerciseId)
    }
}
