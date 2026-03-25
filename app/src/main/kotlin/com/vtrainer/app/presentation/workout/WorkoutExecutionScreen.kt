package com.vtrainer.app.presentation.workout

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vtrainer.app.domain.models.SetLog
import com.vtrainer.app.domain.models.WorkoutPlan
import com.vtrainer.app.presentation.common.NetworkErrorDialog
import com.vtrainer.app.presentation.common.OfflineBanner

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutExecutionScreen(
    viewModel: WorkoutExecutionViewModel,
    workoutPlan: WorkoutPlan,
    dayIndex: Int = 0,
    onWorkoutFinished: () -> Unit = {}
) {
    val state by viewModel.state.collectAsState()
    val haptic = LocalHapticFeedback.current

    remember(workoutPlan, dayIndex) {
        if (state.currentPlan == null) {
            viewModel.startWorkout(workoutPlan, dayIndex)
        }
        true
    }

    if (state.error != null) {
        NetworkErrorDialog(
            message = state.error!!,
            title = "Erro ao Salvar Treino",
            onDismiss = { viewModel.dismissError() },
            onRetry = { viewModel.finishWorkout() }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val dayName = workoutPlan.trainingDays.getOrNull(dayIndex)?.dayName
                        ?: workoutPlan.name
                    Text(text = dayName, fontWeight = FontWeight.Bold)
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            OfflineBanner(isOffline = state.isOffline)

            Box(modifier = Modifier.fillMaxSize()) {
                if (state.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                } else if (state.isWorkoutComplete) {
                    WorkoutCompletionSummary(
                        completedSets = state.completedSets,
                        onDismiss = onWorkoutFinished,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    )
                } else if (state.currentPlan != null) {
                    val plan = state.currentPlan!!
                    val day = plan.trainingDays.getOrNull(state.currentDayIndex)
                    val exercise = day?.exercises?.getOrNull(state.currentExerciseIndex)

                    if (exercise == null) {
                        Text(
                            text = "Nenhum exercício encontrado.",
                            modifier = Modifier.align(Alignment.Center)
                        )
                    } else {
                        val totalSets = exercise.sets
                        val currentSet = state.currentSetIndex + 1

                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            item { Spacer(modifier = Modifier.height(8.dp)) }

                            item {
                                ExerciseHeaderCard(
                                    exerciseId = exercise.exerciseId,
                                    currentSet = currentSet,
                                    totalSets = totalSets
                                )
                            }

                            if (state.isRestTimerActive) {
                                item {
                                    RestTimerCard(
                                        secondsRemaining = state.restTimerSecondsRemaining,
                                        totalSeconds = exercise.restSeconds,
                                        onSkipRest = { viewModel.skipRest() }
                                    )
                                }
                            }

                            if (!state.isRestTimerActive) {
                                item {
                                    WeightRepsAdjustCard(
                                        weight = state.adjustedWeight ?: exercise.reps.toDouble(),
                                        reps = state.adjustedReps ?: exercise.reps,
                                        onWeightIncrease = {
                                            viewModel.adjustWeight(
                                                (state.adjustedWeight ?: exercise.reps.toDouble()) + 2.5
                                            )
                                        },
                                        onWeightDecrease = {
                                            val w = (state.adjustedWeight ?: exercise.reps.toDouble()) - 2.5
                                            viewModel.adjustWeight(if (w < 0.0) 0.0 else w)
                                        },
                                        onRepsIncrease = {
                                            viewModel.adjustReps((state.adjustedReps ?: exercise.reps) + 1)
                                        },
                                        onRepsDecrease = {
                                            val r = (state.adjustedReps ?: exercise.reps) - 1
                                            viewModel.adjustReps(if (r < 1) 1 else r)
                                        }
                                    )
                                }

                                item {
                                    Button(
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            viewModel.completeSet(
                                                weight = state.adjustedWeight ?: exercise.reps.toDouble(),
                                                reps = state.adjustedReps ?: exercise.reps
                                            )
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(56.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Completar Série $currentSet de $totalSets",
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                    }
                                }
                            }

                            if (state.completedSets.isNotEmpty()) {
                                item {
                                    Text(
                                        text = "Séries Completadas",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                items(state.completedSets) { setLog ->
                                    CompletedSetItem(setLog = setLog)
                                }
                            }

                            if (state.isWorkoutComplete || isLastSet(state, exercise.sets)) {
                                item {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    FilledTonalButton(
                                        onClick = { viewModel.finishWorkout() },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(56.dp)
                                    ) {
                                        Text(
                                            text = "Finalizar Treino",
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                    }
                                }
                            }

                            item { Spacer(modifier = Modifier.height(16.dp)) }
                        }
                    }
                } else {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
            }
        }
    }
}

@Composable
private fun ExerciseHeaderCard(
    exerciseId: String,
    currentSet: Int,
    totalSets: Int
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = exerciseId,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Série $currentSet de $totalSets",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun RestTimerCard(
    secondsRemaining: Int,
    totalSeconds: Int,
    onSkipRest: () -> Unit
) {
    val progress = if (totalSeconds > 0) secondsRemaining.toFloat() / totalSeconds.toFloat() else 0f

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Descanso",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "${secondsRemaining}s",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Spacer(modifier = Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = progress,
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.secondary
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(
                onClick = onSkipRest,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Text(text = "Pular Descanso")
            }
        }
    }
}

@Composable
private fun WeightRepsAdjustCard(
    weight: Double,
    reps: Int,
    onWeightIncrease: () -> Unit,
    onWeightDecrease: () -> Unit,
    onRepsIncrease: () -> Unit,
    onRepsDecrease: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Ajustar Série",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                NumberPickerItem(
                    label = "Peso (kg)",
                    value = String.format("%.1f", weight),
                    onIncrease = onWeightIncrease,
                    onDecrease = onWeightDecrease
                )
                NumberPickerItem(
                    label = "Reps",
                    value = reps.toString(),
                    onIncrease = onRepsIncrease,
                    onDecrease = onRepsDecrease
                )
            }
        }
    }
}

@Composable
private fun NumberPickerItem(
    label: String,
    value: String,
    onIncrease: () -> Unit,
    onDecrease: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = onDecrease,
                modifier = Modifier.size(56.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Remove,
                    contentDescription = "Diminuir $label"
                )
            }
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(64.dp),
                textAlign = TextAlign.Center
            )
            IconButton(
                onClick = onIncrease,
                modifier = Modifier.size(56.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Aumentar $label"
                )
            }
        }
    }
}

@Composable
private fun CompletedSetItem(setLog: SetLog) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Série ${setLog.setNumber}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "${setLog.reps} reps × ${setLog.weight} kg",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
    Divider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun WorkoutCompletionSummary(
    completedSets: List<SetLog>,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val totalVolume = completedSets.sumOf { (it.weight * it.reps).toInt() }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "🎉 Treino Concluído!",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Séries completadas: ${completedSets.size}",
            style = MaterialTheme.typography.bodyLarge
        )
        Text(
            text = "Volume total: ${totalVolume} kg",
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = onDismiss,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Text(
                text = "Voltar ao Início",
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}

private fun isLastSet(state: WorkoutExecutionState, totalSetsForCurrentExercise: Int): Boolean {
    val plan = state.currentPlan ?: return false
    val day = plan.trainingDays.getOrNull(state.currentDayIndex) ?: return false
    val isLastExercise = state.currentExerciseIndex == day.exercises.lastIndex
    val isLastSetOfExercise = state.currentSetIndex == totalSetsForCurrentExercise - 1
    return isLastExercise && isLastSetOfExercise && !state.isRestTimerActive
}
