package com.vtrainer.app.util

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.kotest.property.Arb
import io.kotest.property.arbitrary.double
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll

/**
 * Unit tests for [VTrainerLogger] sanitization logic.
 *
 * These tests verify that sensitive data (weights, reps, calories, personal info)
 * is never included in log messages, satisfying Requirement 17.4.
 *
 * Note: Firebase SDK calls (Crashlytics, Performance) are NOT tested here because
 * they require a real Firebase app context. Only the pure sanitization logic is tested.
 *
 * Validates: Requirements 17.4
 */
class VTrainerLoggerTest : DescribeSpec({

    describe("sanitizeMessage") {

        it("redacts weight values") {
            val msg = VTrainerLogger.sanitizeMessage("weight=80.5 kg")
            msg shouldNotContain "80.5"
            msg shouldContain "weight=[REDACTED]"
        }

        it("redacts reps values") {
            val msg = VTrainerLogger.sanitizeMessage("reps=12 completed")
            msg shouldNotContain "12"
            msg shouldContain "reps=[REDACTED]"
        }

        it("redacts calories values") {
            val msg = VTrainerLogger.sanitizeMessage("calories=450 burned")
            msg shouldNotContain "450"
            msg shouldContain "calories=[REDACTED]"
        }

        it("redacts heartRate values") {
            val msg = VTrainerLogger.sanitizeMessage("heartRate=145 bpm")
            msg shouldNotContain "145"
            msg shouldContain "heartRate=[REDACTED]"
        }

        it("redacts bodyWeight values") {
            val msg = VTrainerLogger.sanitizeMessage("bodyWeight=75.0")
            msg shouldNotContain "75.0"
            msg shouldContain "bodyWeight=[REDACTED]"
        }

        it("redacts currentWeight values") {
            val msg = VTrainerLogger.sanitizeMessage("currentWeight=90.5")
            msg shouldNotContain "90.5"
            msg shouldContain "currentWeight=[REDACTED]"
        }

        it("redacts maxWeight values") {
            val msg = VTrainerLogger.sanitizeMessage("maxWeight=100")
            msg shouldNotContain "100"
            msg shouldContain "maxWeight=[REDACTED]"
        }

        it("redacts email addresses") {
            val msg = VTrainerLogger.sanitizeMessage("user email: joao@example.com failed")
            msg shouldNotContain "joao@example.com"
            msg shouldContain "[EMAIL_REDACTED]"
        }

        it("preserves non-sensitive operational data") {
            val msg = VTrainerLogger.sanitizeMessage("SYNC_SUCCESS operation=training_log_sync items=3")
            msg shouldContain "SYNC_SUCCESS"
            msg shouldContain "operation=training_log_sync"
            // items=3 is a count, not a sensitive value — should be preserved
            msg shouldContain "items=3"
        }

        it("preserves error codes") {
            val msg = VTrainerLogger.sanitizeMessage("NETWORK_ERROR errorCode=404 context=WorkoutRepository")
            msg shouldContain "errorCode=404"
            msg shouldContain "context=WorkoutRepository"
        }

        it("is case-insensitive for sensitive keys") {
            val msg = VTrainerLogger.sanitizeMessage("Weight=80 REPS=10")
            msg shouldNotContain "80"
            msg shouldNotContain "10"
        }

        it("handles empty string") {
            VTrainerLogger.sanitizeMessage("") shouldBe ""
        }

        it("handles message with no sensitive data unchanged") {
            val safe = "SYNC_FAILURE operation=workout_plan_sync errorCode=UNAVAILABLE attempt=2"
            VTrainerLogger.sanitizeMessage(safe) shouldBe safe
        }
    }

    describe("sanitizeMessage - property tests") {

        /**
         * Property: any message containing "weight=<number>" should never appear in sanitized output.
         *
         * Validates: Requirements 17.4
         */
        it("never leaks weight values in sanitized messages") {
            checkAll(Arb.double(0.1, 300.0)) { weightValue ->
                val msg = "weight=$weightValue"
                val sanitized = VTrainerLogger.sanitizeMessage(msg)
                sanitized shouldNotContain weightValue.toString()
            }
        }

        /**
         * Property: any message containing "reps=<integer>" should never appear in sanitized output.
         *
         * Validates: Requirements 17.4
         */
        it("never leaks reps values in sanitized messages") {
            checkAll(Arb.int(1, 100)) { repsValue ->
                val msg = "reps=$repsValue"
                val sanitized = VTrainerLogger.sanitizeMessage(msg)
                // The numeric value should be redacted
                sanitized shouldNotContain "reps=$repsValue"
                sanitized shouldContain "reps=[REDACTED]"
            }
        }

        /**
         * Property: any message containing "calories=<number>" should never appear in sanitized output.
         *
         * Validates: Requirements 17.4
         */
        it("never leaks calorie values in sanitized messages") {
            checkAll(Arb.int(1, 2000)) { calorieValue ->
                val msg = "calories=$calorieValue"
                val sanitized = VTrainerLogger.sanitizeMessage(msg)
                sanitized shouldNotContain "calories=$calorieValue"
                sanitized shouldContain "calories=[REDACTED]"
            }
        }
    }

    describe("sanitizeException") {

        it("sanitizes exception message containing weight data") {
            val original = Exception("Sync failed: weight=120.5 exceeds limit")
            val sanitized = VTrainerLogger.sanitizeException(original)
            sanitized.message shouldNotContain "120.5"
            sanitized.message shouldContain "weight=[REDACTED]"
        }

        it("preserves exception stack trace") {
            val original = Exception("some error")
            val sanitized = VTrainerLogger.sanitizeException(original)
            sanitized.stackTrace shouldBe original.stackTrace
        }

        it("handles exception with null message") {
            val original = Exception(null as String?)
            val sanitized = VTrainerLogger.sanitizeException(original)
            // Should not throw, message should be the class simple name
            sanitized.message shouldBe "Exception"
        }
    }
})
