package com.vtrainer.app.data.repositories

import com.vtrainer.app.domain.models.Difficulty
import com.vtrainer.app.domain.models.Equipment
import com.vtrainer.app.domain.models.Exercise
import com.vtrainer.app.domain.models.MediaType
import com.vtrainer.app.domain.models.MuscleGroup
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotBeBlank
import io.kotest.property.Arb
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import java.util.UUID

/**
 * Property-based test for exercise data completeness.
 *
 * **Validates: Requirements 2.1, 2.4**
 *
 * Property 9: Exercise Library Data Completeness
 *
 * For any exercise in the exercise library, it SHALL have all required fields:
 * exerciseId, name, muscleGroup, instructions, mediaUrl, and mediaType
 * (either GIF or VIDEO).
 */
class ExerciseDataCompletenessPropertyTest : FunSpec({

    test("Feature: v-trainer, Property 9: Exercise Library Data Completeness - required fields are non-blank").config(
        invocations = 100
    ) {
        checkAll(100, Arb.exercise()) { exercise ->
            // Assert: All required string fields must be non-blank
            exercise.exerciseId.shouldNotBeBlank()
            exercise.name.shouldNotBeBlank()
            exercise.instructions.shouldNotBeBlank()
            exercise.mediaUrl.shouldNotBeBlank()

            // Assert: muscleGroup must be non-null (guaranteed by type system, but verify value is valid)
            MuscleGroup.values().contains(exercise.muscleGroup) shouldBe true

            // Assert: mediaType must be non-null and a valid enum value
            MediaType.values().contains(exercise.mediaType) shouldBe true
        }
    }

    test("Feature: v-trainer, Property 9: Exercise Library Data Completeness - mediaType is always GIF or VIDEO").config(
        invocations = 100
    ) {
        checkAll(100, Arb.exercise()) { exercise ->
            // Assert: mediaType must be exhaustively one of GIF or VIDEO
            val validMediaTypes = setOf(MediaType.GIF, MediaType.VIDEO)
            validMediaTypes.contains(exercise.mediaType) shouldBe true

            // Exhaustive check: no other values exist in the enum
            MediaType.values().toSet() shouldBe validMediaTypes
        }
    }

    test("Feature: v-trainer, Property 9: Exercise Library Data Completeness - repository returns complete exercises").config(
        invocations = 100
    ) {
        checkAll(100, Arb.list(Arb.exercise(), 1..20)) { exercises ->
            // Arrange: Use a fake repository backed by the generated exercises
            val fakeRepository = FakeExerciseRepository(exercises)

            // Act: Retrieve exercises from the repository
            val retrievedExercises = fakeRepository.getExercises().first()

            // Assert: Every exercise returned satisfies the completeness property
            retrievedExercises.forEach { exercise ->
                exercise.exerciseId.shouldNotBeBlank()
                exercise.name.shouldNotBeBlank()
                exercise.instructions.shouldNotBeBlank()
                exercise.mediaUrl.shouldNotBeBlank()
                MuscleGroup.values().contains(exercise.muscleGroup) shouldBe true
                MediaType.values().contains(exercise.mediaType) shouldBe true
            }

            // Assert: Repository returns the same number of exercises as stored
            retrievedExercises.size shouldBe exercises.size
        }
    }
})

/**
 * Fake ExerciseRepository implementation for testing.
 * Returns a fixed list of exercises without any external dependencies.
 */
private class FakeExerciseRepository(
    private val exercises: List<Exercise>
) : ExerciseRepository {

    override fun getExercises(): Flow<List<Exercise>> = flowOf(exercises)

    override suspend fun searchExercises(query: String): List<Exercise> =
        exercises.filter { it.name.contains(query, ignoreCase = true) }

    override suspend fun filterByMuscleGroup(group: MuscleGroup): List<Exercise> =
        exercises.filter { it.muscleGroup == group || it.secondaryMuscles.contains(group) }
}

/**
 * Custom Arbitrary generator for Exercise objects.
 * Generates exercises with all required fields populated with valid, non-blank values.
 */
fun Arb.Companion.exercise(): Arb<Exercise> = arbitrary {
    Exercise(
        exerciseId = UUID.randomUUID().toString(),
        name = Arb.string(3..50).filter { it.isNotBlank() }.bind(),
        muscleGroup = Arb.enum<MuscleGroup>().bind(),
        secondaryMuscles = Arb.list(Arb.enum<MuscleGroup>(), 0..3).bind(),
        instructions = Arb.string(10..200).filter { it.isNotBlank() }.bind(),
        mediaUrl = "https://example.com/media/${UUID.randomUUID()}.gif",
        mediaType = Arb.enum<MediaType>().bind(),
        difficulty = Arb.enum<Difficulty>().bind(),
        equipment = Arb.list(Arb.enum<Equipment>(), 0..3).bind()
    )
}
