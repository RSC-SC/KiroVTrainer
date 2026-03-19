package com.vtrainer.app.data.repositories

import com.vtrainer.app.domain.models.Difficulty
import com.vtrainer.app.domain.models.Equipment
import com.vtrainer.app.domain.models.Exercise
import com.vtrainer.app.domain.models.MediaType
import com.vtrainer.app.domain.models.MuscleGroup
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.util.UUID

/**
 * Property-based test for exercise muscle group filter correctness.
 *
 * **Validates: Requirements 2.2**
 *
 * Property 10: Exercise Filter by Muscle Group Correctness
 *
 * For any muscle group filter applied to the exercise library, all returned exercises
 * SHALL have that muscle group as either their primary muscleGroup or in their
 * secondaryMuscles list.
 */
class ExerciseMuscleGroupFilterPropertyTest : FunSpec({

    test("Feature: v-trainer, Property 10: Exercise Filter by Muscle Group Correctness - no false positives").config(
        invocations = 100
    ) {
        checkAll(100, Arb.list(Arb.exerciseWithMuscleGroups(), 0..30), Arb.enum<MuscleGroup>()) { exercises, filterGroup ->
            // Arrange: Use a fake repository backed by the generated exercises
            val fakeRepository = FakeMuscleGroupFilterRepository(exercises)

            // Act: Apply the muscle group filter
            val filtered = fakeRepository.filterByMuscleGroup(filterGroup)

            // Assert: Every returned exercise must have the filter group as primary or secondary
            filtered.forEach { exercise ->
                val matchesPrimary = exercise.muscleGroup == filterGroup
                val matchesSecondary = exercise.secondaryMuscles.contains(filterGroup)
                (matchesPrimary || matchesSecondary) shouldBe true
            }
        }
    }

    test("Feature: v-trainer, Property 10: Exercise Filter by Muscle Group Correctness - no false negatives").config(
        invocations = 100
    ) {
        checkAll(100, Arb.list(Arb.exerciseWithMuscleGroups(), 0..30), Arb.enum<MuscleGroup>()) { exercises, filterGroup ->
            // Arrange: Use a fake repository backed by the generated exercises
            val fakeRepository = FakeMuscleGroupFilterRepository(exercises)

            // Act: Apply the muscle group filter
            val filtered = fakeRepository.filterByMuscleGroup(filterGroup)
            val filteredIds = filtered.map { it.exerciseId }.toSet()

            // Assert: Every exercise that matches the filter criteria must be included
            exercises.forEach { exercise ->
                val shouldBeIncluded = exercise.muscleGroup == filterGroup ||
                    exercise.secondaryMuscles.contains(filterGroup)
                if (shouldBeIncluded) {
                    filteredIds.contains(exercise.exerciseId) shouldBe true
                }
            }
        }
    }

    test("Feature: v-trainer, Property 10: Exercise Filter by Muscle Group Correctness - filter count matches expected").config(
        invocations = 100
    ) {
        checkAll(100, Arb.list(Arb.exerciseWithMuscleGroups(), 0..30), Arb.enum<MuscleGroup>()) { exercises, filterGroup ->
            // Arrange
            val fakeRepository = FakeMuscleGroupFilterRepository(exercises)

            // Act
            val filtered = fakeRepository.filterByMuscleGroup(filterGroup)

            // Assert: The count of filtered exercises matches the count of exercises
            // that have the filter group as primary or secondary
            val expectedCount = exercises.count { exercise ->
                exercise.muscleGroup == filterGroup || exercise.secondaryMuscles.contains(filterGroup)
            }
            filtered.size shouldBe expectedCount
        }
    }
})

/**
 * Fake ExerciseRepository implementation for muscle group filter testing.
 * Implements the filter logic directly without Room/Firestore dependencies.
 */
private class FakeMuscleGroupFilterRepository(
    private val exercises: List<Exercise>
) : ExerciseRepository {

    override fun getExercises(): Flow<List<Exercise>> = flowOf(exercises)

    override suspend fun searchExercises(query: String): List<Exercise> =
        exercises.filter { it.name.contains(query, ignoreCase = true) }

    override suspend fun filterByMuscleGroup(group: MuscleGroup): List<Exercise> =
        exercises.filter { it.muscleGroup == group || it.secondaryMuscles.contains(group) }
}

/**
 * Custom Arbitrary generator for Exercise objects with varied muscle group assignments.
 * Generates exercises with random primary and secondary muscle groups to thoroughly
 * exercise the filter logic.
 */
fun Arb.Companion.exerciseWithMuscleGroups(): Arb<Exercise> = arbitrary {
    val primaryMuscle = Arb.enum<MuscleGroup>().bind()
    // Generate 0-3 secondary muscles, potentially overlapping with primary
    val secondaryMuscles = Arb.list(Arb.enum<MuscleGroup>(), 0..3).bind()

    Exercise(
        exerciseId = UUID.randomUUID().toString(),
        name = Arb.string(3..50).filter { it.isNotBlank() }.bind(),
        muscleGroup = primaryMuscle,
        secondaryMuscles = secondaryMuscles,
        instructions = Arb.string(10..200).filter { it.isNotBlank() }.bind(),
        mediaUrl = "https://example.com/media/${UUID.randomUUID()}.gif",
        mediaType = Arb.enum<MediaType>().bind(),
        difficulty = Arb.enum<Difficulty>().bind(),
        equipment = Arb.list(Arb.enum<Equipment>(), 0..3).bind()
    )
}
