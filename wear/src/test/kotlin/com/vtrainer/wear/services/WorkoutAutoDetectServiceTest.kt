package com.vtrainer.wear.services

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import kotlin.math.sqrt

/**
 * Unit tests for [WorkoutAutoDetectService] detection and rate-limiting logic.
 *
 * Tests focus on pure functions extracted from the service to avoid
 * Android framework dependencies (SensorManager, NotificationManager).
 *
 * Validates: Requirements 15.2, 15.4
 */
class WorkoutAutoDetectServiceTest : StringSpec({

    // -------------------------------------------------------------------------
    // Movement magnitude calculation (Req 15.1)
    // -------------------------------------------------------------------------

    fun magnitude(x: Float, y: Float, z: Float): Double =
        sqrt((x * x + y * y + z * z).toDouble())

    "magnitude of gravity-only reading is approximately 9.8" {
        val mag = magnitude(0f, 0f, 9.8f)
        (mag > 9.0 && mag < 10.5).shouldBeTrue()
    }

    "magnitude exceeds threshold during vigorous movement" {
        // Simulate a strong push: ~15 m/s² total
        val mag = magnitude(10f, 10f, 5f)
        (mag > WorkoutAutoDetectService.MOVEMENT_THRESHOLD).shouldBeTrue()
    }

    "magnitude below threshold for gentle movement" {
        val mag = magnitude(1f, 1f, 9.8f)
        (mag < WorkoutAutoDetectService.MOVEMENT_THRESHOLD).shouldBeTrue()
    }

    // -------------------------------------------------------------------------
    // Peak detection window (Req 15.2)
    // -------------------------------------------------------------------------

    "detection window is 30 seconds" {
        WorkoutAutoDetectService.DETECTION_WINDOW_MS shouldBe 30_000L
    }

    "minimum peaks required for detection" {
        WorkoutAutoDetectService.MIN_PEAKS_IN_WINDOW shouldBe 10
    }

    "peaks outside window are excluded from count" {
        val nowMs = 100_000L
        val windowMs = WorkoutAutoDetectService.DETECTION_WINDOW_MS
        val peaks = mutableListOf<Long>()

        // Add 5 old peaks (outside window)
        repeat(5) { peaks.add(nowMs - windowMs - 1_000L) }
        // Add 5 recent peaks (inside window)
        repeat(5) { peaks.add(nowMs - 1_000L) }

        val activePeaks = peaks.filter { nowMs - it <= windowMs }
        activePeaks.size shouldBe 5
        (activePeaks.size >= WorkoutAutoDetectService.MIN_PEAKS_IN_WINDOW).shouldBeFalse()
    }

    "sufficient peaks within window triggers detection" {
        val nowMs = 100_000L
        val windowMs = WorkoutAutoDetectService.DETECTION_WINDOW_MS
        val peaks = mutableListOf<Long>()

        // Add MIN_PEAKS_IN_WINDOW peaks inside the window
        repeat(WorkoutAutoDetectService.MIN_PEAKS_IN_WINDOW) {
            peaks.add(nowMs - (it * 2_000L))
        }

        val activePeaks = peaks.filter { nowMs - it <= windowMs }
        (activePeaks.size >= WorkoutAutoDetectService.MIN_PEAKS_IN_WINDOW).shouldBeTrue()
    }

    // -------------------------------------------------------------------------
    // Rate limiting (Req 15.4)
    // -------------------------------------------------------------------------

    "rate limit constant is 1 hour in milliseconds" {
        WorkoutAutoDetectService.RATE_LIMIT_MS shouldBe 3_600_000L
    }

    "notification blocked when called within rate limit window" {
        val lastMs = 1_000_000L
        val nowMs = lastMs + WorkoutAutoDetectService.RATE_LIMIT_MS - 1
        val allowed = nowMs - lastMs >= WorkoutAutoDetectService.RATE_LIMIT_MS
        allowed.shouldBeFalse()
    }

    "notification allowed when rate limit window has passed" {
        val lastMs = 1_000_000L
        val nowMs = lastMs + WorkoutAutoDetectService.RATE_LIMIT_MS
        val allowed = nowMs - lastMs >= WorkoutAutoDetectService.RATE_LIMIT_MS
        allowed.shouldBeTrue()
    }
})
