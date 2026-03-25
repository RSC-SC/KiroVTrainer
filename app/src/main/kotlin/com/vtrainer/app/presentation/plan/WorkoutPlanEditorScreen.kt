package com.vtrainer.app.presentation.plan

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.vtrainer.app.data.serialization.WorkoutPlanSerializer
import com.vtrainer.app.domain.models.PlannedExercise

/**
 * Screen for creating and editing a [WorkoutPlan].
 *
 * Displays:
 * - Plan name and description fields
 * - List of training days with their exercises
 * - Controls to add/remove days and exercises
 * - Per-exercise sets, reps, and rest time configuration
 * - Save and delete actions
 * - Export (share) and import actions (Req 19.5, 19.6)
 *
 * Validates: Requirements 3.1, 3.2, 3.3, 3.4, 3.6, 19.5, 19.6
 *
 * @param viewModel [WorkoutPlanViewModel] managing plan state.
 * @param onNavigateBack Called when the user cancels or after a successful save.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutPlanEditorScreen(
    viewModel: WorkoutPlanViewModel,
    onNavigateBack: () -> Unit = {}
) {
    val state by viewModel.state.collectAsState()
    val plan = state.editingPlan
    val context = LocalContext.current

    // Import dialog state
    var showImportDialog by remember { mutableStateOf(false) }

    // Navigate back automatically after a successful save (Req 3.4)
    if (state.saveSuccess) {
        onNavigateBack()
        return
    }

    if (showImportDialog) {
        ImportPlanDialog(
            onConfirm = { json ->
                viewModel.importPlanFromJson(json)
                showImportDialog = false
            },
            onDismiss = { showImportDialog = false }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (plan?.name?.isNotBlank() == true) plan.name else "Novo Plano",
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    // Import action — Req 19.6
                    IconButton(onClick = { showImportDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.FileDownload,
                            contentDescription = "Importar plano"
                        )
                    }
                    // Export/share action — only shown when editing an existing plan (Req 19.5)
                    if (plan != null) {
                        IconButton(onClick = {
                            sharePlanAsJson(context, plan.name, WorkoutPlanSerializer.exportToJson(plan))
                        }) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Exportar plano"
                            )
                        }
                    }
                    // Delete action — only shown when editing an existing plan (Req 3.6)
                    if (plan != null && plan.planId.isNotBlank()) {
                        IconButton(onClick = {
                            viewModel.deletePlan(plan.planId)
                            onNavigateBack()
                        }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Excluir plano"
                            )
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        if (plan == null) {
            CircularProgressIndicator(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(32.dp)
            )
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { Spacer(modifier = Modifier.height(4.dp)) }

            // Req 3.1 — Plan name field
            item {
                OutlinedTextField(
                    value = plan.name,
                    onValueChange = { viewModel.updatePlanName(it) },
                    label = { Text("Nome do Plano") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            // Req 3.1 — Plan description field
            item {
                OutlinedTextField(
                    value = plan.description ?: "",
                    onValueChange = { viewModel.updatePlanDescription(it.ifBlank { null }) },
                    label = { Text("Descrição (opcional)") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3
                )
            }

            // Req 3.2 — Training days section header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Dias de Treino",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    // Add training day button (Req 3.2)
                    var showAddDay by remember { mutableStateOf(false) }
                    if (showAddDay) {
                        AddDayDialog(
                            onConfirm = { dayName ->
                                viewModel.addTrainingDay(dayName)
                                showAddDay = false
                            },
                            onDismiss = { showAddDay = false }
                        )
                    }
                    FilledTonalButton(onClick = { showAddDay = true }) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Adicionar Dia")
                    }
                }
            }

            // Req 3.2, 3.3 — Training days list with exercises
            itemsIndexed(plan.trainingDays) { dayIndex, day ->
                TrainingDayCard(
                    dayName = day.dayName,
                    exercises = day.exercises,
                    onAddExercise = { exerciseId ->
                        val exercise = PlannedExercise(
                            exerciseId = exerciseId,
                            order = day.exercises.size,
                            sets = 3,
                            reps = 10,
                            restSeconds = 60,
                            notes = null
                        )
                        viewModel.addExercise(dayIndex, exercise)
                    },
                    onRemoveExercise = { exerciseIndex ->
                        viewModel.removeExercise(dayIndex, exerciseIndex)
                    },
                    onUpdateExerciseConfig = { exerciseIndex, sets, reps, rest ->
                        viewModel.updateExerciseConfig(dayIndex, exerciseIndex, sets, reps, rest)
                    },
                    onRemoveDay = { viewModel.removeTrainingDay(dayIndex) }
                )
            }

            // Error message
            if (state.error != null) {
                item {
                    Text(
                        text = state.error!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            // Req 3.4 — Save and Cancel buttons
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            viewModel.cancelEdit()
                            onNavigateBack()
                        },
                        modifier = Modifier.weight(1f).height(56.dp)
                    ) {
                        Text("Cancelar")
                    }
                    Button(
                        onClick = { viewModel.savePlan() },
                        enabled = plan.name.isNotBlank() && !state.isSaving,
                        modifier = Modifier.weight(1f).height(56.dp)
                    ) {
                        if (state.isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.width(20.dp).height(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("Salvar Plano")
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

// ---------------------------------------------------------------------------
// Sub-composables
// ---------------------------------------------------------------------------

/**
 * Card displaying a single training day with its exercises.
 *
 * Validates: Requirements 3.2, 3.3
 */
@Composable
private fun TrainingDayCard(
    dayName: String,
    exercises: List<PlannedExercise>,
    onAddExercise: (String) -> Unit,
    onRemoveExercise: (Int) -> Unit,
    onUpdateExerciseConfig: (Int, Int, Int, Int) -> Unit,
    onRemoveDay: () -> Unit
) {
    var showAddExercise by remember { mutableStateOf(false) }

    if (showAddExercise) {
        AddExerciseDialog(
            onConfirm = { exerciseId ->
                onAddExercise(exerciseId)
                showAddExercise = false
            },
            onDismiss = { showAddExercise = false }
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = dayName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row {
                    TextButton(onClick = { showAddExercise = true }) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Exercício")
                    }
                    IconButton(onClick = onRemoveDay) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Remover dia $dayName",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            if (exercises.isEmpty()) {
                Text(
                    text = "Nenhum exercício adicionado.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                exercises.forEachIndexed { index, exercise ->
                    Divider(modifier = Modifier.padding(vertical = 8.dp))
                    ExerciseConfigRow(
                        exercise = exercise,
                        onRemove = { onRemoveExercise(index) },
                        onConfigChange = { sets, reps, rest ->
                            onUpdateExerciseConfig(index, sets, reps, rest)
                        }
                    )
                }
            }
        }
    }
}

/**
 * Row for configuring a single planned exercise (sets, reps, rest).
 *
 * Validates: Requirement 3.3
 */
@Composable
private fun ExerciseConfigRow(
    exercise: PlannedExercise,
    onRemove: () -> Unit,
    onConfigChange: (sets: Int, reps: Int, rest: Int) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = exercise.exerciseId,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onRemove) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Remover ${exercise.exerciseId}",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Sets field
            OutlinedTextField(
                value = exercise.sets.toString(),
                onValueChange = { v ->
                    v.toIntOrNull()?.let { onConfigChange(it, exercise.reps, exercise.restSeconds) }
                },
                label = { Text("Séries") },
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true
            )
            // Reps field
            OutlinedTextField(
                value = exercise.reps.toString(),
                onValueChange = { v ->
                    v.toIntOrNull()?.let { onConfigChange(exercise.sets, it, exercise.restSeconds) }
                },
                label = { Text("Reps") },
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true
            )
            // Rest field
            OutlinedTextField(
                value = exercise.restSeconds.toString(),
                onValueChange = { v ->
                    v.toIntOrNull()?.let { onConfigChange(exercise.sets, exercise.reps, it) }
                },
                label = { Text("Descanso (s)") },
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true
            )
        }
    }
}

/**
 * Inline dialog for adding a new training day.
 */
@Composable
private fun AddDayDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var dayName by remember { mutableStateOf("") }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Novo Dia de Treino") },
        text = {
            OutlinedTextField(
                value = dayName,
                onValueChange = { dayName = it },
                label = { Text("Nome do dia (ex: Segunda-feira)") },
                singleLine = true
            )
        },
        confirmButton = {
            Button(
                onClick = { if (dayName.isNotBlank()) onConfirm(dayName) },
                enabled = dayName.isNotBlank()
            ) { Text("Adicionar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

/**
 * Inline dialog for adding a new exercise to a training day.
 */
@Composable
private fun AddExerciseDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var exerciseId by remember { mutableStateOf("") }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Adicionar Exercício") },
        text = {
            OutlinedTextField(
                value = exerciseId,
                onValueChange = { exerciseId = it },
                label = { Text("Nome do exercício") },
                singleLine = true
            )
        },
        confirmButton = {
            Button(
                onClick = { if (exerciseId.isNotBlank()) onConfirm(exerciseId) },
                enabled = exerciseId.isNotBlank()
            ) { Text("Adicionar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

/**
 * Dialog for importing a workout plan from a JSON string.
 *
 * Validates: Requirement 19.6
 */
@Composable
private fun ImportPlanDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var jsonText by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Importar Plano de Treino") },
        text = {
            OutlinedTextField(
                value = jsonText,
                onValueChange = { jsonText = it },
                label = { Text("Cole o JSON do plano aqui") },
                minLines = 4,
                maxLines = 8,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = { if (jsonText.isNotBlank()) onConfirm(jsonText) },
                enabled = jsonText.isNotBlank()
            ) { Text("Importar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

/**
 * Launches the Android share sheet with the workout plan JSON.
 *
 * Validates: Requirement 19.5
 */
private fun sharePlanAsJson(context: Context, planName: String, json: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/json"
        putExtra(Intent.EXTRA_SUBJECT, "Plano de Treino: $planName")
        putExtra(Intent.EXTRA_TEXT, json)
    }
    context.startActivity(Intent.createChooser(intent, "Exportar plano de treino"))
}
