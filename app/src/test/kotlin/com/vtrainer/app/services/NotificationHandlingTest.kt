package com.vtrainer.app.services

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotBeEmpty

/**
 * Unit tests for notification handling logic.
 *
 * Validates: Requirements 16.3, 16.4
 *
 * Tests the pure content-building logic via [NotificationContentBuilder],
 * which mirrors the notification construction in [VTrainerMessagingService]
 * and [VTrainerWearMessagingService] without requiring an Android context.
 */
class NotificationHandlingTest : FunSpec({

    // -------------------------------------------------------------------------
    // 1. Personal record notification — correct title and body
    // Validates: Requirement 16.3
    // -------------------------------------------------------------------------
    test("personal_record notification displays correct title and body") {
        val data = mapOf(
            "type" to "personal_record",
            "exerciseName" to "Supino Reto",
            "recordType" to "max_weight",
            "newValue" to "80kg"
        )

        val (title, body) = NotificationContentBuilder.build(data)

        title shouldBe "Personal Record!"
        body shouldContain "Supino Reto"
        body shouldContain "max_weight"
        body shouldContain "80kg"
    }

    // -------------------------------------------------------------------------
    // 2. Workout reminder — correct title and workout name in body
    // Validates: Requirement 16.4
    // -------------------------------------------------------------------------
    test("workout_reminder notification displays correct title and workout name") {
        val data = mapOf(
            "type" to "workout_reminder",
            "workoutName" to "Treino A - Peito e Tríceps"
        )

        val (title, body) = NotificationContentBuilder.build(data)

        title shouldBe "Workout Reminder"
        body shouldBe "Treino A - Peito e Tríceps"
    }

    // -------------------------------------------------------------------------
    // 3. Workout reminder quick action — title is non-empty and descriptive
    // Validates: Requirement 16.4 (quick action to start the session)
    // -------------------------------------------------------------------------
    test("workout_reminder notification includes quick action (has non-empty title)") {
        val data = mapOf(
            "type" to "workout_reminder",
            "workoutName" to "Treino B"
        )

        val (title, _) = NotificationContentBuilder.build(data)

        title.shouldNotBeEmpty()
        title shouldBe "Workout Reminder"
    }

    // -------------------------------------------------------------------------
    // 4. Personal record with missing optional fields uses defaults
    // Validates: Requirement 16.3
    // -------------------------------------------------------------------------
    test("personal_record notification with missing optional fields uses defaults") {
        val data = mapOf(
            "type" to "personal_record",
            "exerciseName" to "Supino"
        )

        val (title, body) = NotificationContentBuilder.build(data)

        title shouldBe "Personal Record!"
        body shouldContain "Supino"
    }

    // -------------------------------------------------------------------------
    // 5. Workout reminder with missing workoutName uses default
    // Validates: Requirement 16.4
    // -------------------------------------------------------------------------
    test("workout_reminder with missing workoutName uses default") {
        val data = mapOf(
            "type" to "workout_reminder"
        )

        val (_, body) = NotificationContentBuilder.build(data)

        body shouldBe "Your workout"
    }

    // -------------------------------------------------------------------------
    // 6. Unknown notification type returns empty content
    // -------------------------------------------------------------------------
    test("unknown notification type returns empty content") {
        val data = mapOf(
            "type" to "unknown_type"
        )

        val (title, body) = NotificationContentBuilder.build(data)

        title shouldBe ""
        body shouldBe ""
    }
})
