package com.vtrainer.app.data.serialization

import com.vtrainer.app.domain.models.PlannedExercise
import com.vtrainer.app.domain.models.TrainingDay
import com.vtrainer.app.domain.models.WorkoutPlan
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.result.shouldBeFailure
import io.kotest.matchers.result.shouldBeSuccess
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotBeBlank
import io.kotest.property.Arb
import io.kotest.property.checkAll
import kotlinx.datetime.Instant

/**
 * Unit tests for [WorkoutPlanSerializer] import/export functionality.
 *
 * Validates: Requirements 19.1, 19.2, 19.3, 19.5
 */
class WorkoutPlanSerializerTest : FunSpec({

    val samplePlan = WorkoutPlan(
        planId = "plan-001",
        userId = "user-abc",
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
                        notes = "Foco no excêntrico"
                    )
                )
            )
        ),
        createdAt = Instant.parse("2024-01-01T10:00:00Z"),
        updatedAt = Instant.parse("2024-01-02T12:00:00Z")
    )

    // -----------------------------------------------------------------------
    // Export tests — Requirement 19.3, 19.5
    // -----------------------------------------------------------------------

    test("exportToJson produces non-blank JSON string") {
        val json = WorkoutPlanSerializer.exportToJson(samplePlan)
        json.shouldNotBeBlank()
    }

    test("exportToJson includes all required fields") {
        val json = WorkoutPlanSerializer.exportToJson(samplePlan)
        json shouldContain "\"planId\""
        json shouldContain "\"userId\""
        json shouldContain "\"name\""
        json shouldContain "\"trainingDays\""
        json shouldContain "\"createdAt\""
        json shouldContain "\"updatedAt\""
    }

    test("exportToJson preserves plan name and description") {
        val json = WorkoutPlanSerializer.exportToJson(samplePlan)
        json shouldContain "Treino ABC"
        json shouldContain "Divisão de 3 dias"
    }

    test("exportToJson preserves exercise configuration") {
        val json = WorkoutPlanSerializer.exportToJson(samplePlan)
        json shouldContain "supino_reto"
        json shouldContain "\"sets\":4"
        json shouldContain "\"reps\":12"
        json shouldContain "\"restSeconds\":60"
    }

    test("exportToJson and toJson produce identical output") {
        val plan = samplePlan
        WorkoutPlanSerializer.exportToJson(plan) shouldBe WorkoutPlanSerializer.toJson(plan)
    }

    // -----------------------------------------------------------------------
    // Import valid JSON — Requirement 19.1
    // -----------------------------------------------------------------------

    test("importFromJson parses valid JSON into WorkoutPlan") {
        val json = WorkoutPlanSerializer.exportToJson(samplePlan)
        val result = WorkoutPlanSerializer.importFromJson(json)
        result.shouldBeSuccess { imported ->
            imported.planId shouldBe samplePlan.planId
            imported.userId shouldBe samplePlan.userId
            imported.name shouldBe samplePlan.name
            imported.description shouldBe samplePlan.description
            imported.trainingDays.size shouldBe samplePlan.trainingDays.size
        }
    }

    test("importFromJson preserves exercise details") {
        val json = WorkoutPlanSerializer.exportToJson(samplePlan)
        val result = WorkoutPlanSerializer.importFromJson(json)
        result.shouldBeSuccess { imported ->
            val exercise = imported.trainingDays[0].exercises[0]
            exercise.exerciseId shouldBe "supino_reto"
            exercise.sets shouldBe 4
            exercise.reps shouldBe 12
            exercise.restSeconds shouldBe 60
            exercise.notes shouldBe "Foco no excêntrico"
        }
    }

    test("importFromJson preserves null description") {
        val planNoDesc = samplePlan.copy(description = null)
        val json = WorkoutPlanSerializer.exportToJson(planNoDesc)
        val result = WorkoutPlanSerializer.importFromJson(json)
        result.shouldBeSuccess { imported ->
            imported.description shouldBe null
        }
    }

    // -----------------------------------------------------------------------
    // Import invalid JSON — Requirement 19.2
    // -----------------------------------------------------------------------

    test("importFromJson returns failure for empty string") {
        val result = WorkoutPlanSerializer.importFromJson("")
        result.shouldBeFailure { error ->
            error.message shouldContain "empty"
        }
    }

    test("importFromJson returns failure for blank string") {
        val result = WorkoutPlanSerializer.importFromJson("   ")
        result.shouldBeFailure { error ->
            error.message shouldContain "empty"
        }
    }

    test("importFromJson returns failure for malformed JSON") {
        val result = WorkoutPlanSerializer.importFromJson("{not valid json}")
        result.shouldBeFailure { error ->
            error.message.shouldNotBeBlank()
        }
    }

    test("importFromJson returns failure for JSON missing required planId") {
        val json = """{"userId":"u1","name":"Plan","trainingDays":[],"createdAt":"2024-01-01T00:00:00Z","updatedAt":"2024-01-01T00:00:00Z"}"""
        val result = WorkoutPlanSerializer.importFromJson(json)
        result.shouldBeFailure { error ->
            error.message shouldContain "planId"
        }
    }

    test("importFromJson returns failure for JSON missing required name") {
        val json = """{"planId":"p1","userId":"u1","trainingDays":[],"createdAt":"2024-01-01T00:00:00Z","updatedAt":"2024-01-01T00:00:00Z"}"""
        val result = WorkoutPlanSerializer.importFromJson(json)
        result.shouldBeFailure { error ->
            error.message shouldContain "name"
        }
    }

    test("importFromJson returns failure for invalid timestamp format") {
        val json = """{"planId":"p1","userId":"u1","name":"Plan","trainingDays":[],"createdAt":"not-a-date","updatedAt":"2024-01-01T00:00:00Z"}"""
        val result = WorkoutPlanSerializer.importFromJson(json)
        result.shouldBeFailure { error ->
            error.message shouldContain "createdAt"
        }
    }

    test("importFromJson returns failure for exercise with non-positive sets") {
        val json = """{"planId":"p1","userId":"u1","name":"Plan","trainingDays":[{"dayName":"Day A","exercises":[{"exerciseId":"ex1","order":1,"sets":0,"reps":10,"restSeconds":60,"notes":null}]}],"createdAt":"2024-01-01T00:00:00Z","updatedAt":"2024-01-01T00:00:00Z"}"""
        val result = WorkoutPlanSerializer.importFromJson(json)
        result.shouldBeFailure { error ->
            error.message shouldContain "sets"
        }
    }

    test("importFromJson returns failure for exercise with negative restSeconds") {
        val json = """{"planId":"p1","userId":"u1","name":"Plan","trainingDays":[{"dayName":"Day A","exercises":[{"exerciseId":"ex1","order":1,"sets":3,"reps":10,"restSeconds":-1,"notes":null}]}],"createdAt":"2024-01-01T00:00:00Z","updatedAt":"2024-01-01T00:00:00Z"}"""
        val result = WorkoutPlanSerializer.importFromJson(json)
        result.shouldBeFailure { error ->
            error.message shouldContain "restSeconds"
        }
    }

    test("importFromJson error message is descriptive (not generic)") {
        val result = WorkoutPlanSerializer.importFromJson("not json at all")
        result.shouldBeFailure { error ->
            // Must not be a generic "error" — should describe what went wrong
            error.message!!.length shouldBe error.message!!.length // always true; real check below
            error.message shouldContain Regex("(JSON|format|invalid)", RegexOption.IGNORE_CASE)
        }
    }

    // -----------------------------------------------------------------------
    // Round-trip — Requirement 19.4
    // -----------------------------------------------------------------------

    test("export then import produces equivalent WorkoutPlan") {
        val json = WorkoutPlanSerializer.exportToJson(samplePlan)
        val result = WorkoutPlanSerializer.importFromJson(json)
        result.shouldBeSuccess { imported ->
            imported shouldBe samplePlan
        }
    }

    test("round-trip preserves plan with multiple training days and exercises") {
        val complexPlan = samplePlan.copy(
            trainingDays = listOf(
                TrainingDay("Treino A", listOf(
                    PlannedExercise("supino_reto", 1, 4, 12, 60, null),
                    PlannedExercise("supino_inclinado", 2, 3, 10, 60, "Leve")
                )),
                TrainingDay("Treino B", listOf(
                    PlannedExercise("puxada_frente", 1, 4, 10, 90, null)
                ))
            )
        )
        val json = WorkoutPlanSerializer.exportToJson(complexPlan)
        val result = WorkoutPlanSerializer.importFromJson(json)
        result.shouldBeSuccess { imported ->
            imported shouldBe complexPlan
        }
    }

    test("round-trip property: export then import then export produces same JSON") {
        checkAll(1, Arb.workoutPlan()) { plan ->
            val json1 = WorkoutPlanSerializer.exportToJson(plan)
            val imported = WorkoutPlanSerializer.importFromJson(json1).getOrThrow()
            val json2 = WorkoutPlanSerializer.exportToJson(imported)
            json1 shouldBe json2
        }
    }
})
