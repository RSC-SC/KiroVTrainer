package com.vtrainer.app.data.serialization

import com.vtrainer.app.domain.models.PlannedExercise
import com.vtrainer.app.domain.models.TrainingDay
import com.vtrainer.app.domain.models.WorkoutPlan
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import kotlinx.datetime.Instant

/**
 * Property-based test for WorkoutPlan serialization.
 * 
 * Feature: v-trainer
 * Property 1: Workout Plan Round-Trip Serialization
 * 
 * Validates: Requirements 19.4
 * 
 * Tests that serializing a WorkoutPlan to JSON and then deserializing it back
 * produces an equivalent WorkoutPlan object with the same data.
 */
class WorkoutPlanSerializationPropertyTest : FunSpec({
    
    test("Feature: v-trainer, Property 1: Workout Plan Round-Trip Serialization") {
        checkAll(100, Arb.workoutPlan()) { plan ->
            // Serialize to JSON
            val json = WorkoutPlanSerializer.toJson(plan)
            
            // Deserialize back to object
            val deserialized = WorkoutPlanSerializer.fromJson(json)
            
            // Verify equivalence
            deserialized shouldBe plan
        }
    }
})

/**
 * Custom Arb generator for WorkoutPlan.
 * Generates random but valid WorkoutPlan instances for property testing.
 */
fun Arb.Companion.workoutPlan(): Arb<WorkoutPlan> = arbitrary {
    WorkoutPlan(
        planId = Arb.uuid().bind(),
        userId = Arb.uuid().bind(),
        name = Arb.string(5..50, Codepoint.alphanumeric()).bind(),
        description = Arb.string(0..200, Codepoint.alphanumeric()).orNull(0.3).bind(),
        trainingDays = Arb.list(Arb.trainingDay(), 1..7).bind(),
        createdAt = Arb.instant().bind(),
        updatedAt = Arb.instant().bind()
    )
}

/**
 * Custom Arb generator for TrainingDay.
 */
fun Arb.Companion.trainingDay(): Arb<TrainingDay> = arbitrary {
    TrainingDay(
        dayName = Arb.stringPattern("[A-Z][a-z]+ [A-Z] - [A-Za-z ]+").bind(),
        exercises = Arb.list(Arb.plannedExercise(), 1..10).bind()
    )
}

/**
 * Custom Arb generator for PlannedExercise.
 */
fun Arb.Companion.plannedExercise(): Arb<PlannedExercise> = arbitrary {
    PlannedExercise(
        exerciseId = Arb.stringPattern("[a-z_]+").bind(),
        order = Arb.int(1..20).bind(),
        sets = Arb.int(1..10).bind(),
        reps = Arb.int(1..50).bind(),
        restSeconds = Arb.int(30..300).bind(),
        notes = Arb.string(0..100, Codepoint.alphanumeric()).orNull(0.5).bind()
    )
}

/**
 * Custom Arb generator for UUID strings.
 */
fun Arb.Companion.uuid(): Arb<String> = arbitrary {
    java.util.UUID.randomUUID().toString()
}

/**
 * Custom Arb generator for Instant.
 * Generates timestamps within a reasonable range (2020-2030).
 */
fun Arb.Companion.instant(): Arb<Instant> = arbitrary {
    val minEpochSeconds = 1577836800L // 2020-01-01
    val maxEpochSeconds = 1893456000L // 2030-01-01
    val epochSeconds = Arb.long(minEpochSeconds..maxEpochSeconds).bind()
    Instant.fromEpochSeconds(epochSeconds)
}
