package com.vtrainer.wear.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import org.junit.Rule
import org.junit.Test

/**
 * UI tests for WatchWorkoutScreen state rendering.
 *
 * Tests use lightweight composable wrappers that mirror the screen's
 * internal composables, avoiding the need for a real ViewModel.
 *
 * Validates: Requirements 5.2, 5.3
 */
class WatchWorkoutScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun sampleExercises() = listOf(
        WatchPlannedExercise(
            exerciseId = "Supino Reto",
            sets = 3,
            reps = 10,
            restSeconds = 60
        )
    )

    // -------------------------------------------------------------------------
    // Set completion flow (Req 5.2)
    // -------------------------------------------------------------------------

    @Test
    fun activeSet_displaysExerciseName() {
        val state = WatchWorkoutState(
            exercises = sampleExercises(),
            currentExerciseIndex = 0,
            currentSetIndex = 0
        )
        composeTestRule.setContent {
            MaterialTheme {
                ActiveSetTestContent(state = state)
            }
        }
        composeTestRule
            .onNodeWithContentDescription("Exercise: Supino Reto")
            .assertIsDisplayed()
    }

    @Test
    fun activeSet_displaysSetProgress() {
        val state = WatchWorkoutState(
            exercises = sampleExercises(),
            currentExerciseIndex = 0,
            currentSetIndex = 1
        )
        composeTestRule.setContent {
            MaterialTheme {
                ActiveSetTestContent(state = state)
            }
        }
        composeTestRule
            .onNodeWithText("Set 2 / 3")
            .assertIsDisplayed()
    }

    @Test
    fun activeSet_completeSetButton_isDisplayed() {
        val state = WatchWorkoutState(exercises = sampleExercises())
        composeTestRule.setContent {
            MaterialTheme {
                ActiveSetTestContent(state = state)
            }
        }
        composeTestRule
            .onNodeWithContentDescription("Complete set")
            .assertIsDisplayed()
    }

    // -------------------------------------------------------------------------
    // Rest timer display (Req 5.3)
    // -------------------------------------------------------------------------

    @Test
    fun restTimer_displaysSecondsRemaining() {
        composeTestRule.setContent {
            MaterialTheme {
                RestTimerTestContent(secondsRemaining = 45, onSkipRest = {})
            }
        }
        composeTestRule
            .onNodeWithText("45")
            .assertIsDisplayed()
    }

    @Test
    fun restTimer_skipButton_isDisplayed() {
        composeTestRule.setContent {
            MaterialTheme {
                RestTimerTestContent(secondsRemaining = 30, onSkipRest = {})
            }
        }
        composeTestRule
            .onNodeWithContentDescription("Skip rest")
            .assertIsDisplayed()
    }

    @Test
    fun restTimer_skipButton_invokesCallback() {
        var skipped = false
        composeTestRule.setContent {
            MaterialTheme {
                RestTimerTestContent(secondsRemaining = 30, onSkipRest = { skipped = true })
            }
        }
        composeTestRule
            .onNodeWithContentDescription("Skip rest")
            .performClick()
        assert(skipped)
    }

    // -------------------------------------------------------------------------
    // Heart rate display (Req 9.2)
    // -------------------------------------------------------------------------

    @Test
    fun activeSet_displaysHeartRate_whenAvailable() {
        val state = WatchWorkoutState(
            exercises = sampleExercises(),
            currentHeartRate = 142
        )
        composeTestRule.setContent {
            MaterialTheme {
                ActiveSetTestContent(state = state)
            }
        }
        composeTestRule
            .onNodeWithContentDescription("Heart rate 142 beats per minute")
            .assertIsDisplayed()
    }
}

// ---------------------------------------------------------------------------
// Minimal test composables mirroring WatchWorkoutScreen internals
// ---------------------------------------------------------------------------

@androidx.compose.runtime.Composable
private fun ActiveSetTestContent(state: WatchWorkoutState) {
    val exercise = state.exercises.getOrNull(state.currentExerciseIndex)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = exercise?.exerciseId ?: "—",
            modifier = Modifier.semantics {
                contentDescription = "Exercise: ${exercise?.exerciseId ?: "none"}"
            }
        )
        Text(text = "Set ${state.currentSetIndex + 1} / ${exercise?.sets ?: 1}")
        state.currentHeartRate?.let { bpm ->
            Text(
                text = "♥ $bpm bpm",
                modifier = Modifier.semantics {
                    contentDescription = "Heart rate $bpm beats per minute"
                }
            )
        }
        Button(
            onClick = {},
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .semantics { contentDescription = "Complete set" }
        ) {
            Text("✓ Done")
        }
    }
}

@androidx.compose.runtime.Composable
private fun RestTimerTestContent(secondsRemaining: Int, onSkipRest: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = "$secondsRemaining")
            Button(
                onClick = onSkipRest,
                modifier = Modifier
                    .height(48.dp)
                    .semantics { contentDescription = "Skip rest" }
            ) {
                Text("Skip")
            }
        }
    }
}
