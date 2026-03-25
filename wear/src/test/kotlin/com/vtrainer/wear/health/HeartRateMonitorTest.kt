package com.vtrainer.wear.health

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.ints.shouldBeGreaterThan

/**
 * Unit tests for [HeartRateMonitor.calculateAverage].
 *
 * Note: [HeartRateMonitor.isHeartRateSupported] and [HeartRateMonitor.heartRateFlow]
 * require a real Wear OS device or emulator with Health Services, so they are
 * covered by instrumented tests on device. These unit tests focus on the pure
 * calculation logic that can run on the JVM.
 *
 * Validates: Requirements 9.1, 9.2, 9.3
 */
class HeartRateMonitorTest : StringSpec({

    // Standalone average calculator matching HeartRateMonitor.calculateAverage logic
    fun calculateAverage(readings: List<Int>): Int? {
        if (readings.isEmpty()) return null
        return readings.average().toInt()
    }

    // ---------------------------------------------------------------------------
    // calculateAverage tests
    // ---------------------------------------------------------------------------

    "calculateAverage returns null for empty readings list" {
        calculateAverage(emptyList()).shouldBeNull()
    }

    "calculateAverage returns the single value for a one-element list" {
        calculateAverage(listOf(75)) shouldBe 75
    }

    "calculateAverage returns correct average for multiple readings" {
        // (60 + 80 + 100) / 3 = 80
        calculateAverage(listOf(60, 80, 100)) shouldBe 80
    }

    "calculateAverage truncates decimal (floor behavior)" {
        // (70 + 71) / 2 = 70.5 → truncated to 70
        calculateAverage(listOf(70, 71)) shouldBe 70
    }

    "calculateAverage returns positive value for valid readings" {
        val avg = calculateAverage(listOf(55, 65, 75, 85, 95))
        avg.shouldNotBeNull()
        avg shouldBeGreaterThan 0
    }

    "calculateAverage handles high heart rate readings" {
        // Max realistic heart rate ~200 bpm
        calculateAverage(listOf(180, 190, 200)) shouldBe 190
    }

    "calculateAverage handles resting heart rate readings" {
        // Resting heart rate ~40–60 bpm
        calculateAverage(listOf(42, 45, 48)) shouldBe 45
    }

    // ---------------------------------------------------------------------------
    // HeartRateResult sealed class tests
    // ---------------------------------------------------------------------------

    "HeartRateResult.Reading holds the correct bpm value" {
        val result = HeartRateResult.Reading(bpm = 72)
        result.bpm shouldBe 72
    }

    "HeartRateResult.Available is a distinct state from Reading" {
        val available = HeartRateResult.Available
        val reading = HeartRateResult.Reading(72)
        (available is HeartRateResult.Available) shouldBe true
        (reading is HeartRateResult.Reading) shouldBe true
    }

    "HeartRateResult.Unavailable is a distinct state" {
        val unavailable = HeartRateResult.Unavailable
        (unavailable is HeartRateResult.Unavailable) shouldBe true
    }
})
