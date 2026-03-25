package com.vtrainer.app.presentation.exercise

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vtrainer.app.domain.models.Exercise
import com.vtrainer.app.domain.models.MuscleGroup

/**
 * Screen for browsing the exercise library.
 *
 * Displays:
 * - Search bar for filtering by name (Req 2.2)
 * - Muscle group filter chips (Req 2.2)
 * - Exercise cards with name and primary muscle group (Req 2.1)
 * - Exercise detail dialog with instructions and media info (Req 2.3, 2.4)
 *
 * Validates: Requirements 2.1, 2.2, 2.3, 2.4
 *
 * @param viewModel [ExerciseLibraryViewModel] managing exercise state.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseLibraryScreen(
    viewModel: ExerciseLibraryViewModel
) {
    val state by viewModel.state.collectAsState()

    // Detail dialog (Req 2.3)
    state.selectedExercise?.let { exercise ->
        ExerciseDetailDialog(
            exercise = exercise,
            onDismiss = { viewModel.selectExercise(null) }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Biblioteca de Exercícios", fontWeight = FontWeight.Bold) }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Req 2.2 — Search bar
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = { viewModel.search(it) },
                placeholder = { Text("Buscar exercício...") },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null)
                },
                trailingIcon = {
                    if (state.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.search("") }) {
                            Icon(Icons.Default.Close, contentDescription = "Limpar busca")
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                singleLine = true
            )

            // Req 2.2 — Muscle group filter chips
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    FilterChip(
                        selected = state.selectedMuscleGroup == null,
                        onClick = { viewModel.filterByMuscleGroup(null) },
                        label = { Text("Todos") }
                    )
                }
                items(MuscleGroup.entries) { muscleGroup ->
                    FilterChip(
                        selected = state.selectedMuscleGroup == muscleGroup,
                        onClick = { viewModel.filterByMuscleGroup(muscleGroup) },
                        label = { Text(muscleGroup.displayName()) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    state.isLoading -> {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }

                    state.error != null -> {
                        Text(
                            text = state.error!!,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(16.dp)
                        )
                    }

                    state.exercises.isEmpty() -> {
                        Text(
                            text = "Nenhum exercício encontrado.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(16.dp)
                        )
                    }

                    else -> {
                        // Req 2.1 — Exercise list
                        LazyColumn(
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(state.exercises) { exercise ->
                                ExerciseCard(
                                    exercise = exercise,
                                    onClick = { viewModel.selectExercise(exercise) }
                                )
                            }
                            item { Spacer(modifier = Modifier.height(16.dp)) }
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Sub-composables
// ---------------------------------------------------------------------------

/**
 * Card showing an exercise's name and primary muscle group.
 *
 * Validates: Requirements 2.1, 2.4
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExerciseCard(
    exercise: Exercise,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = exercise.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = exercise.muscleGroup.displayName(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = exercise.difficulty.name
                    .lowercase()
                    .replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/**
 * Dialog showing full exercise details including instructions and media info.
 *
 * Validates: Requirements 2.3, 2.4
 */
@Composable
private fun ExerciseDetailDialog(
    exercise: Exercise,
    onDismiss: () -> Unit
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = exercise.name, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Muscle group
                LabeledRow(label = "Músculo principal", value = exercise.muscleGroup.displayName())

                // Secondary muscles
                if (exercise.secondaryMuscles.isNotEmpty()) {
                    LabeledRow(
                        label = "Músculos secundários",
                        value = exercise.secondaryMuscles.joinToString { it.displayName() }
                    )
                }

                // Difficulty
                LabeledRow(
                    label = "Dificuldade",
                    value = exercise.difficulty.name.lowercase().replaceFirstChar { it.uppercase() }
                )

                // Equipment
                if (exercise.equipment.isNotEmpty()) {
                    LabeledRow(
                        label = "Equipamento",
                        value = exercise.equipment.joinToString { it.name.lowercase().replaceFirstChar { c -> c.uppercase() } }
                    )
                }

                // Req 2.4 — Instructions
                if (exercise.instructions.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Instruções",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = exercise.instructions,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                // Req 2.4 — Media type indicator (GIF/video playback handled by media URL)
                if (exercise.mediaUrl.isNotBlank()) {
                    LabeledRow(
                        label = "Mídia",
                        value = exercise.mediaType.name.lowercase().replaceFirstChar { it.uppercase() }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Fechar") }
        }
    )
}

@Composable
private fun LabeledRow(label: String, value: String) {
    Row {
        Text(
            text = "$label: ",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

// ---------------------------------------------------------------------------
// Display name helpers
// ---------------------------------------------------------------------------

private fun MuscleGroup.displayName(): String = when (this) {
    MuscleGroup.CHEST -> "Peito"
    MuscleGroup.BACK -> "Costas"
    MuscleGroup.SHOULDERS -> "Ombros"
    MuscleGroup.BICEPS -> "Bíceps"
    MuscleGroup.TRICEPS -> "Tríceps"
    MuscleGroup.LEGS -> "Pernas"
    MuscleGroup.QUADRICEPS -> "Quadríceps"
    MuscleGroup.HAMSTRINGS -> "Isquiotibiais"
    MuscleGroup.GLUTES -> "Glúteos"
    MuscleGroup.CALVES -> "Panturrilhas"
    MuscleGroup.ABS -> "Abdômen"
    MuscleGroup.CORE -> "Core"
}
