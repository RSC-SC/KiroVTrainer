package com.vtrainer.wear.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText

/**
 * Main workout execution screen for Wear OS.
 *
 * Displays current exercise, set progression, heart rate, rest timer,
 * calories, and weight/reps adjustment controls.
 *
 * Validates: Requirements 5.1, 5.2, 5.3, 5.4, 5.5, 18.2, 18.3
 */
@Composable
fun WatchWorkoutScreen(
    viewModel: WatchWorkoutViewModel,
    onHapticSetComplete: () -> Unit = {},
    onHapticTimerExpired: () -> Unit = {},
    onWorkoutFinished: () -> Unit = {}
) {
    val state by viewModel.state.collectAsState()

    // Consume haptic triggers (Req 5.4)
    LaunchedEffect(state.triggerSetCompletionHaptic) {
        if (state.triggerSetCompletionHaptic) {
            onHapticSetComplete()
            viewModel.onSetCompletionHapticConsumed()
        }
    }

    LaunchedEffect(state.triggerTimerExpiredHaptic) {
        if (state.triggerTimerExpiredHaptic) {
            onHapticTimerExpired()
            viewModel.onTimerExpiredHapticConsumed()
        }
    }

    LaunchedEffect(state.isWorkoutComplete) {
        if (state.isWorkoutComplete && state.syncStatus == WatchSyncStatus.SYNCED) {
            onWorkoutFinished()
        }
    }

    Scaffold(timeText = { TimeText() }) {
        when {
            state.isWorkoutComplete -> WorkoutCompleteContent(state)
            state.isRestTimerActive -> RestTimerContent(
                state = state,
                onSkipRest = { viewModel.skipRest() }
            )
            else -> ActiveSetContent(
                state = state,
                onAdjustWeight = { viewModel.adjustWeight(it) },
                onAdjustReps = { viewModel.adjustReps(it) },
                onCompleteSet = { weight, reps -> viewModel.completeSet(weight, reps) },
                onFinishWorkout = { viewModel.finishWorkout() }
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Active set content (Req 5.1, 5.2, 5.5)
// ---------------------------------------------------------------------------

@Composable
private fun ActiveSetContent(
    state: WatchWorkoutState,
    onAdjustWeight: (Double) -> Unit,
    onAdjustReps: (Int) -> Unit,
    onCompleteSet: (Double, Int) -> Unit,
    onFinishWorkout: () -> Unit
) {
    val exercise = state.exercises.getOrNull(state.currentExerciseIndex)
    val plannedWeight = 0.0 // default; real app would load from plan
    val currentWeight = state.adjustedWeight ?: plannedWeight
    val currentReps = state.adjustedReps ?: (exercise?.reps ?: 10)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Exercise name
        Text(
            text = exercise?.exerciseId ?: "—",
            style = MaterialTheme.typography.title3,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "Exercise: ${exercise?.exerciseId ?: "none"}" }
        )

        // Set progress
        Text(
            text = "Set ${state.currentSetIndex + 1} / ${exercise?.sets ?: 1}",
            style = MaterialTheme.typography.body2,
            color = MaterialTheme.colors.secondary,
            modifier = Modifier.semantics {
                contentDescription = "Set ${state.currentSetIndex + 1} of ${exercise?.sets ?: 1}"
            }
        )

        // Heart rate (Req 9.2)
        state.currentHeartRate?.let { bpm ->
            Text(
                text = "♥ $bpm bpm",
                style = MaterialTheme.typography.caption3,
                color = Color(0xFFFF6B6B),
                modifier = Modifier.semantics { contentDescription = "Heart rate $bpm beats per minute" }
            )
        }

        // Weight and reps adjustment (Req 5.5, 18.3 — min 48dp touch targets)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            AdjustControl(
                label = "${currentWeight.toInt()} kg",
                contentDesc = "Weight ${currentWeight.toInt()} kilograms",
                onDecrement = { onAdjustWeight((currentWeight - 2.5).coerceAtLeast(0.0)) },
                onIncrement = { onAdjustWeight(currentWeight + 2.5) }
            )
            AdjustControl(
                label = "$currentReps reps",
                contentDesc = "$currentReps repetitions",
                onDecrement = { onAdjustReps((currentReps - 1).coerceAtLeast(1)) },
                onIncrement = { onAdjustReps(currentReps + 1) }
            )
        }

        // Complete set button (Req 5.2, 18.3 — min 48dp)
        Button(
            onClick = { onCompleteSet(currentWeight, currentReps) },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .semantics { contentDescription = "Complete set" },
            colors = ButtonDefaults.buttonColors(backgroundColor = MaterialTheme.colors.primary)
        ) {
            Text("✓ Done", fontWeight = FontWeight.Bold)
        }

        // Finish workout (last set of last exercise)
        val isLastSet = exercise != null &&
            state.currentSetIndex == exercise.sets - 1 &&
            state.currentExerciseIndex == state.exercises.size - 1

        if (isLastSet) {
            Button(
                onClick = onFinishWorkout,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .semantics { contentDescription = "Finish workout" },
                colors = ButtonDefaults.buttonColors(backgroundColor = MaterialTheme.colors.secondary)
            ) {
                Text("Finish", fontSize = 12.sp)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Rest timer content (Req 5.3, 5.4)
// ---------------------------------------------------------------------------

@Composable
private fun RestTimerContent(
    state: WatchWorkoutState,
    onSkipRest: () -> Unit
) {
    val restSeconds = state.restTimerSecondsRemaining
    val totalRest = state.exercises
        .getOrNull(state.currentExerciseIndex)?.restSeconds?.toFloat() ?: 60f
    val progress = if (totalRest > 0) restSeconds / totalRest else 0f

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // Circular progress indicator (Req 5.3)
        CircularProgressIndicator(
            progress = progress,
            modifier = Modifier
                .size(100.dp)
                .semantics { contentDescription = "Rest timer $restSeconds seconds remaining" },
            strokeWidth = 6.dp,
            indicatorColor = MaterialTheme.colors.primary,
            trackColor = MaterialTheme.colors.onSurface.copy(alpha = 0.2f)
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "$restSeconds",
                style = MaterialTheme.typography.display3,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "sec rest",
                style = MaterialTheme.typography.caption3,
                color = MaterialTheme.colors.secondary
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onSkipRest,
                modifier = Modifier
                    .height(48.dp)
                    .width(80.dp)
                    .semantics { contentDescription = "Skip rest" },
                colors = ButtonDefaults.buttonColors(backgroundColor = MaterialTheme.colors.surface)
            ) {
                Text("Skip", fontSize = 11.sp)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Workout complete content (Req 5.6)
// ---------------------------------------------------------------------------

@Composable
private fun WorkoutCompleteContent(state: WatchWorkoutState) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Done!",
            style = MaterialTheme.typography.title2,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.semantics { contentDescription = "Workout complete" }
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "${state.completedSets.size} sets",
            style = MaterialTheme.typography.body2,
            color = MaterialTheme.colors.secondary
        )
        state.currentCalories.takeIf { it > 0 }?.let { cal ->
            Text(
                text = "$cal kcal",
                style = MaterialTheme.typography.body2,
                color = MaterialTheme.colors.secondary,
                modifier = Modifier.semantics { contentDescription = "$cal calories burned" }
            )
        }
        Spacer(Modifier.height(8.dp))
        SyncStatusIndicator(state.syncStatus)
    }
}

// ---------------------------------------------------------------------------
// Sync status indicator (Req 5.6)
// ---------------------------------------------------------------------------

@Composable
private fun SyncStatusIndicator(status: WatchSyncStatus) {
    val (label, color) = when (status) {
        WatchSyncStatus.IDLE -> "—" to MaterialTheme.colors.onSurface
        WatchSyncStatus.SYNCING -> "Syncing…" to MaterialTheme.colors.secondary
        WatchSyncStatus.SYNCED -> "Synced ✓" to Color(0xFF4CAF50)
        WatchSyncStatus.FAILED -> "Sync failed" to Color(0xFFFF5252)
    }
    Text(
        text = label,
        style = MaterialTheme.typography.caption3,
        color = color,
        modifier = Modifier.semantics { contentDescription = "Sync status: $label" }
    )
}

// ---------------------------------------------------------------------------
// Reusable adjustment control (Req 5.5, 18.3)
// ---------------------------------------------------------------------------

@Composable
private fun AdjustControl(
    label: String,
    contentDesc: String,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        // Increment — min 48dp touch target (Req 18.3)
        Button(
            onClick = onIncrement,
            modifier = Modifier
                .size(48.dp)
                .semantics { contentDescription = "Increase $contentDesc" },
            colors = ButtonDefaults.buttonColors(backgroundColor = MaterialTheme.colors.surface)
        ) {
            Text("+", fontWeight = FontWeight.Bold)
        }
        Text(
            text = label,
            style = MaterialTheme.typography.caption2,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(vertical = 2.dp)
                .semantics { contentDescription = contentDesc }
        )
        // Decrement — min 48dp touch target (Req 18.3)
        Button(
            onClick = onDecrement,
            modifier = Modifier
                .size(48.dp)
                .semantics { contentDescription = "Decrease $contentDesc" },
            colors = ButtonDefaults.buttonColors(backgroundColor = MaterialTheme.colors.surface)
        ) {
            Text("−", fontWeight = FontWeight.Bold)
        }
    }
}
