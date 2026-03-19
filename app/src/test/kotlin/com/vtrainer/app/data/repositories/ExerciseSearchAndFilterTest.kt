package com.vtrainer.app.data.repositories

import com.vtrainer.app.domain.models.Difficulty
import com.vtrainer.app.domain.models.Equipment
import com.vtrainer.app.domain.models.Exercise
import com.vtrainer.app.domain.models.MediaType
import com.vtrainer.app.domain.models.MuscleGroup
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.flowOf

/**
 * Unit tests for ExerciseRepository search and filter functionality.
 *
 * Requirements: 2.2 - Display exercise library with search and filter by muscle group
 */
class ExerciseSearchAndFilterTest : DescribeSpec({

    // --- Test fixtures ---

    val benchPress = exercise(
        id = "1",
        name = "Bench Press",
        primary = MuscleGroup.CHEST,
        secondary = listOf(MuscleGroup.TRICEPS, MuscleGroup.SHOULDERS)
    )
    val inclineBenchPress = exercise(
        id = "2",
        name = "Incline Bench Press",
        primary = MuscleGroup.CHEST,
        secondary = listOf(MuscleGroup.SHOULDERS)
    )
    val squat = exercise(
        id = "3",
        name = "Squat",
        primary = MuscleGroup.QUADRICEPS,
        secondary = listOf(MuscleGroup.GLUTES, MuscleGroup.HAMSTRINGS)
    )
    val deadlift = exercise(
        id = "4",
        name = "Deadlift",
        primary = MuscleGroup.BACK,
        secondary = listOf(MuscleGroup.HAMSTRINGS, MuscleGroup.GLUTES)
    )
    val tricepPushdown = exercise(
        id = "5",
        name = "Tricep Pushdown",
        primary = MuscleGroup.TRICEPS,
        secondary = emptyList()
    )

    val allExercises = listOf(benchPress, inclineBenchPress, squat, deadlift, tricepPushdown)

    fun repo(exercises: List<Exercise> = allExercises) = FakeSearchFilterRepository(exercises)

    // --- Search by name ---

    describe("searchExercises") {

        it("returns exercises matching query (case-insensitive)") {
            val result = repo().searchExercises("bench")
            result shouldContainExactlyInAnyOrder listOf(benchPress, inclineBenchPress)
        }

        it("is case-insensitive") {
            val result = repo().searchExercises("SQUAT")
            result shouldContainExactlyInAnyOrder listOf(squat)
        }

        it("returns empty list when no match") {
            val result = repo().searchExercises("pullup")
            result.shouldBeEmpty()
        }

        it("returns all exercises when query matches all") {
            val result = repo().searchExercises("e")
            // bench press, incline bench press, deadlift, tricep pushdown all contain 'e'
            result.forEach { exercise ->
                exercise.name.contains("e", ignoreCase = true) shouldBe true
            }
        }

        it("returns empty list when repository is empty") {
            val result = repo(emptyList()).searchExercises("bench")
            result.shouldBeEmpty()
        }

        it("matches partial name") {
            val result = repo().searchExercises("dead")
            result shouldContainExactlyInAnyOrder listOf(deadlift)
        }
    }

    // --- Filter by muscle group ---

    describe("filterByMuscleGroup") {

        it("returns exercises with matching primary muscle group") {
            val result = repo().filterByMuscleGroup(MuscleGroup.CHEST)
            result shouldContainExactlyInAnyOrder listOf(benchPress, inclineBenchPress)
        }

        it("returns exercises with matching secondary muscle group") {
            val result = repo().filterByMuscleGroup(MuscleGroup.HAMSTRINGS)
            result shouldContainExactlyInAnyOrder listOf(squat, deadlift)
        }

        it("returns exercises matching both primary and secondary") {
            // TRICEPS is primary for tricepPushdown and secondary for benchPress
            val result = repo().filterByMuscleGroup(MuscleGroup.TRICEPS)
            result shouldContainExactlyInAnyOrder listOf(benchPress, tricepPushdown)
        }

        it("returns empty list when no exercise matches the muscle group") {
            val result = repo().filterByMuscleGroup(MuscleGroup.BICEPS)
            result.shouldBeEmpty()
        }

        it("returns empty list when repository is empty") {
            val result = repo(emptyList()).filterByMuscleGroup(MuscleGroup.CHEST)
            result.shouldBeEmpty()
        }

        it("returns correct count for GLUTES (secondary in squat and deadlift)") {
            val result = repo().filterByMuscleGroup(MuscleGroup.GLUTES)
            result shouldHaveSize 2
            result shouldContainExactlyInAnyOrder listOf(squat, deadlift)
        }

        it("does not return duplicates when exercise matches both primary and secondary") {
            // Create an exercise where the same group appears as primary and secondary
            val weirdExercise = exercise(
                id = "99",
                name = "Weird Exercise",
                primary = MuscleGroup.CHEST,
                secondary = listOf(MuscleGroup.CHEST)
            )
            val result = repo(listOf(weirdExercise)).filterByMuscleGroup(MuscleGroup.CHEST)
            result shouldHaveSize 1
        }
    }
})

// --- Helpers ---

private fun exercise(
    id: String,
    name: String,
    primary: MuscleGroup,
    secondary: List<MuscleGroup>
) = Exercise(
    exerciseId = id,
    name = name,
    muscleGroup = primary,
    secondaryMuscles = secondary,
    instructions = "Do the exercise",
    mediaUrl = "https://example.com/$id.gif",
    mediaType = MediaType.GIF,
    difficulty = Difficulty.BEGINNER,
    equipment = emptyList()
)

/**
 * Fake repository for unit testing search and filter logic in isolation.
 */
private class FakeSearchFilterRepository(
    private val exercises: List<Exercise>
) : ExerciseRepository {

    override fun getExercises() = flowOf(exercises)

    override suspend fun searchExercises(query: String): List<Exercise> =
        exercises.filter { it.name.contains(query, ignoreCase = true) }

    override suspend fun filterByMuscleGroup(group: MuscleGroup): List<Exercise> =
        exercises
            .filter { it.muscleGroup == group || it.secondaryMuscles.contains(group) }
            .distinctBy { it.exerciseId }
}
