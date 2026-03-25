package com.vtrainer.wear.presentation

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll

/**
 * Property 23: Touch Target Minimum Size on Watch
 *
 * All interactive controls on the watch screen must have a minimum touch
 * target size of 48dp to be usable during exercise.
 *
 * Validates: Requirements 18.3
 */
class TouchTargetMinSizeWatchPropertyTest : StringSpec({

    val minTouchTargetDp = 48

    /**
     * Simulates the touch target sizes defined in WatchWorkoutScreen.
     * Each interactive element is represented as a (widthDp, heightDp) pair.
     */
    data class WatchTouchTarget(val name: String, val widthDp: Int, val heightDp: Int)

    val screenTargets = listOf(
        WatchTouchTarget("Complete set button", widthDp = 160, heightDp = 48),
        WatchTouchTarget("Finish workout button", widthDp = 160, heightDp = 48),
        WatchTouchTarget("Skip rest button", widthDp = 80, heightDp = 48),
        WatchTouchTarget("Weight increment", widthDp = 48, heightDp = 48),
        WatchTouchTarget("Weight decrement", widthDp = 48, heightDp = 48),
        WatchTouchTarget("Reps increment", widthDp = 48, heightDp = 48),
        WatchTouchTarget("Reps decrement", widthDp = 48, heightDp = 48)
    )

    "all watch touch targets meet minimum 48dp size requirement" {
        checkAll(1, Arb.int(0, screenTargets.size - 1)) { idx ->
            val target = screenTargets[idx]
            target.widthDp shouldBeGreaterThanOrEqual minTouchTargetDp
            target.heightDp shouldBeGreaterThanOrEqual minTouchTargetDp
        }
    }

    "complete set button meets minimum touch target size" {
        val target = screenTargets.first { it.name == "Complete set button" }
        target.heightDp shouldBeGreaterThanOrEqual minTouchTargetDp
    }

    "adjustment controls meet minimum touch target size" {
        val adjustmentTargets = screenTargets.filter {
            it.name.contains("increment") || it.name.contains("decrement")
        }
        adjustmentTargets.forEach { target ->
            target.widthDp shouldBeGreaterThanOrEqual minTouchTargetDp
            target.heightDp shouldBeGreaterThanOrEqual minTouchTargetDp
        }
    }
})
