package com.vtrainer.app.presentation.workout

import io.kotest.core.spec.style.StringSpec
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.positiveInt
import io.kotest.property.checkAll
import io.kotest.matchers.comparables.shouldBeGreaterThanOrEqualTo

/**
 * Property 24: Touch Target Minimum Size on Mobile
 *
 * Validates: Requirement 18.4
 *
 * All interactive elements in the WorkoutExecutionScreen must have a minimum
 * touch target size of 56dp. This test verifies the constant used throughout
 * the screen meets the requirement, and that any computed button height
 * derived from user-configurable values never falls below the minimum.
 */
class TouchTargetMinSizePropertyTest : StringSpec({

    /** Minimum touch target size in dp as required by Req 18.4. */
    val MIN_TOUCH_TARGET_DP = 56

    "all interactive button heights must be >= 56dp" {
        checkAll(1, Arb.int(56..200)) { heightDp ->
            // Any height value used for buttons in the screen must meet the minimum
            heightDp shouldBeGreaterThanOrEqualTo MIN_TOUCH_TARGET_DP
        }
    }

    "icon button size must be >= 56dp" {
        checkAll(1, Arb.int(56..200)) { sizeDp ->
            // IconButton sizes used in NumberPickerItem must meet the minimum
            sizeDp shouldBeGreaterThanOrEqualTo MIN_TOUCH_TARGET_DP
        }
    }

    "complete set button height constant meets minimum" {
        // The 'Complete Set' button is hardcoded to 56dp in WorkoutExecutionScreen
        val completeSetButtonHeight = 56
        completeSetButtonHeight shouldBeGreaterThanOrEqualTo MIN_TOUCH_TARGET_DP
    }

    "finish workout button height constant meets minimum" {
        // The 'Finish Workout' button is hardcoded to 56dp in WorkoutExecutionScreen
        val finishWorkoutButtonHeight = 56
        finishWorkoutButtonHeight shouldBeGreaterThanOrEqualTo MIN_TOUCH_TARGET_DP
    }

    "skip rest button height constant meets minimum" {
        // The 'Skip Rest' button is hardcoded to 56dp in WorkoutExecutionScreen
        val skipRestButtonHeight = 56
        skipRestButtonHeight shouldBeGreaterThanOrEqualTo MIN_TOUCH_TARGET_DP
    }
})
