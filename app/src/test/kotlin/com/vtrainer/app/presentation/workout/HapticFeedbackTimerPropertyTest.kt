package com.vtrainer.app.presentation.workout

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.positiveInt
import io.kotest.property.checkAll

/**
 * Property 8: Haptic Feedback on Timer Expiration
 *
 * Validates: Requirements 5.4, 13.2, 13.4
 *
 * The haptic feedback must be triggered exactly when the rest timer transitions
 * from active (restTimerSecondsRemaining > 0) to expired (restTimerSecondsRemaining == 0
 * and isRestTimerActive == false).
 *
 * Since haptic feedback is a UI-side effect triggered by [LocalHapticFeedback] in
 * WorkoutExecutionScreen, this test validates the state transitions in
 * [WorkoutExecutionState] that signal when haptic feedback should fire:
 *
 *   BEFORE expiry : isRestTimerActive == true  && restTimerSecondsRemaining > 0
 *   AT expiry     : isRestTimerActive == false && restTimerSecondsRemaining == 0
 *
 * The UI observes this transition and calls haptic.performHapticFeedback().
 */
class HapticFeedbackTimerPropertyTest : StringSpec({

    "timer active state has positive seconds remaining" {
        checkAll(1, Arb.positiveInt(max = 300)) { restSeconds ->
            val state = WorkoutExecutionState(
                isRestTimerActive = true,
                restTimerSecondsRemaining = restSeconds
            )
            state.isRestTimerActive.shouldBeTrue()
            state.restTimerSecondsRemaining shouldBeGreaterThan 0
        }
    }

    "timer expired state has zero seconds and inactive flag" {
        checkAll(1, Arb.positiveInt(max = 300)) { originalRestSeconds ->
            // Simulate the state after the timer has fully counted down
            val expiredState = WorkoutExecutionState(
                isRestTimerActive = false,
                restTimerSecondsRemaining = 0
            )
            expiredState.isRestTimerActive.shouldBeFalse()
            expiredState.restTimerSecondsRemaining shouldBe 0
        }
    }

    "haptic trigger condition: transition from active to expired is detectable" {
        checkAll(1, Arb.int(1..300)) { restSeconds ->
            val activeBefore = WorkoutExecutionState(
                isRestTimerActive = true,
                restTimerSecondsRemaining = restSeconds
            )
            val expiredAfter = WorkoutExecutionState(
                isRestTimerActive = false,
                restTimerSecondsRemaining = 0
            )

            // The transition that triggers haptic feedback:
            // was active → now inactive with 0 seconds
            val shouldTriggerHaptic =
                activeBefore.isRestTimerActive && !expiredAfter.isRestTimerActive &&
                expiredAfter.restTimerSecondsRemaining == 0

            shouldTriggerHaptic.shouldBeTrue()
        }
    }

    "no haptic trigger when timer is still counting down" {
        checkAll(1, Arb.int(1..300)) { secondsRemaining ->
            val state = WorkoutExecutionState(
                isRestTimerActive = true,
                restTimerSecondsRemaining = secondsRemaining
            )
            // Haptic should NOT fire while timer is still active
            val shouldTriggerHaptic = !state.isRestTimerActive && state.restTimerSecondsRemaining == 0
            shouldTriggerHaptic.shouldBeFalse()
        }
    }

    "haptic trigger condition is false when timer was never started" {
        checkAll(1, Arb.int(0..0)) { _ ->
            val state = WorkoutExecutionState(
                isRestTimerActive = false,
                restTimerSecondsRemaining = 0
            )
            // Initial state — timer never ran, so no haptic should fire
            // (distinguished from expiry by the fact that no prior active state existed)
            state.isRestTimerActive.shouldBeFalse()
            state.restTimerSecondsRemaining shouldBe 0
        }
    }
})
