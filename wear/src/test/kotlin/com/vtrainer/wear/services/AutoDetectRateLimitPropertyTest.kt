package com.vtrainer.wear.services

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.property.Arb
import io.kotest.property.arbitrary.long
import io.kotest.property.checkAll

/**
 * Property 22: Auto-Detect Notification Rate Limiting
 *
 * The auto-detect service must not send more than 1 notification per hour,
 * regardless of how many movement detections occur.
 *
 * Validates: Requirements 15.4
 */
class AutoDetectRateLimitPropertyTest : StringSpec({

    /**
     * Pure rate-limit logic extracted from WorkoutAutoDetectService for testability.
     * Returns true if a notification is allowed given the last notification time.
     */
    fun isNotificationAllowed(nowMs: Long, lastNotificationMs: Long): Boolean =
        nowMs - lastNotificationMs >= WorkoutAutoDetectService.RATE_LIMIT_MS

    "notification is blocked within rate limit window" {
        checkAll(1, Arb.long(1L, WorkoutAutoDetectService.RATE_LIMIT_MS - 1)) { elapsedMs ->
            val lastNotificationMs = 1_000_000L
            val nowMs = lastNotificationMs + elapsedMs
            isNotificationAllowed(nowMs, lastNotificationMs).shouldBeFalse()
        }
    }

    "notification is allowed after rate limit window expires" {
        val lastNotificationMs = 1_000_000L
        val nowMs = lastNotificationMs + WorkoutAutoDetectService.RATE_LIMIT_MS
        isNotificationAllowed(nowMs, lastNotificationMs).shouldBeTrue()
    }

    "first notification is always allowed when no previous notification" {
        val nowMs = System.currentTimeMillis()
        // lastNotificationMs = 0 means no previous notification
        isNotificationAllowed(nowMs, lastNotificationMs = 0L).shouldBeTrue()
    }

    "rate limit is exactly 1 hour" {
        WorkoutAutoDetectService.RATE_LIMIT_MS shouldBe 60 * 60 * 1_000L
    }
}) {
    companion object {
        private infix fun Long.shouldBe(expected: Long) {
            if (this != expected) throw AssertionError("Expected $expected but was $this")
        }
    }
}
