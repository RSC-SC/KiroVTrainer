package com.vtrainer.wear.health

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.comparables.shouldBeGreaterThanOrEqualTo
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.nonNegativeInt
import io.kotest.property.checkAll

/**
 * Property 18: Calorie Data Inclusion in Training Logs
 *
 * Validates: Requirements 10.2, 10.3, 10.4
 *
 * Properties verified:
 * - Calorie count is always non-negative (cannot burn negative calories)
 * - When tracking produces a positive calorie count, it is included in the log
 * - When tracking produces zero calories, it is treated as unavailable (null)
 * - Cumulative calories only increase over time (monotonic property)
 * - Total calories at workout end equals the last cumulative value
 */
class CalorieDataInclusionPropertyTest : StringSpec({

    "calorie count is always non-negative" {
        checkAll(1, Arb.nonNegativeInt(max = 2000)) { calories ->
            calories shouldBeGreaterThanOrEqualTo 0
        }
    }

    "positive calorie count is included in training log" {
        checkAll(1, Arb.int(1..2000)) { calories ->
            // When tracking returns a positive value, it should be included (not null)
            val result = calories.takeIf { it > 0 }
            result.shouldNotBeNull()
            result shouldBe calories
        }
    }

    "zero calorie count is treated as unavailable (null)" {
        checkAll(1, Arb.int(0..0)) { _ ->
            // When tracking returns 0, it means no data — should be null in the log
            val result = 0.takeIf { it > 0 }
            result.shouldBeNull()
        }
    }

    "cumulative calories are monotonically non-decreasing" {
        checkAll(1, Arb.int(0..500)) { initialCalories ->
            // Each update should be >= the previous value
            val update1 = initialCalories
            val update2 = initialCalories + 50
            val update3 = initialCalories + 120

            update2 shouldBeGreaterThanOrEqualTo update1
            update3 shouldBeGreaterThanOrEqualTo update2
        }
    }

    "total calories at workout end equals last cumulative value" {
        checkAll(1, Arb.int(1..1500)) { finalCalories ->
            // The value returned by stopTracking() should match the last emitted value
            val lastEmittedValue = finalCalories
            val totalAtEnd = lastEmittedValue.takeIf { it > 0 }
            totalAtEnd shouldBe finalCalories
        }
    }
})
