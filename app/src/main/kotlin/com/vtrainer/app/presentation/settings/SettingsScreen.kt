package com.vtrainer.app.presentation.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.vtrainer.app.presentation.auth.FitnessLevel

/**
 * Settings screen for editing user profile and preferences.
 *
 * Sections:
 * - Profile: name, weight, fitness level, training goal (Req 1.1, 1.2)
 * - Reminders: enable/disable toggle + time input (Req 16.1, 16.5)
 * - Auto-detect: enable/disable toggle (Req 15.5)
 *
 * Validates: Requirements 1.1, 1.2, 15.5, 16.1, 16.5
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit = {}
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Show success/error feedback via snackbar
    LaunchedEffect(state.saveSuccess) {
        if (state.saveSuccess) {
            snackbarHostState.showSnackbar("Configurações salvas com sucesso")
        }
    }
    LaunchedEffect(state.error) {
        state.error?.let { snackbarHostState.showSnackbar(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Configurações") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Voltar"
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        if (state.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // ── Profile section ──────────────────────────────────────────────
            SectionHeader(title = "Perfil")

            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::onNameChange,
                label = { Text("Nome") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = state.currentWeight,
                onValueChange = viewModel::onWeightChange,
                label = { Text("Peso atual (kg)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )

            Text(
                text = "Nível de condicionamento",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(top = 4.dp)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FitnessLevel.entries.forEach { level ->
                    FilterChip(
                        selected = state.fitnessLevel == level,
                        onClick = { viewModel.onFitnessLevelChange(level) },
                        label = { Text(level.label) }
                    )
                }
            }

            OutlinedTextField(
                value = state.trainingGoal,
                onValueChange = viewModel::onTrainingGoalChange,
                label = { Text("Objetivo de treino") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Divider(modifier = Modifier.padding(vertical = 8.dp))

            // ── Reminders section ────────────────────────────────────────────
            SectionHeader(title = "Lembretes de Treino")

            // Req 16.1: enable/disable reminder toggle
            PreferenceRow(
                label = "Ativar lembretes",
                description = "Receba notificações no horário configurado",
                checked = state.reminderEnabled,
                onCheckedChange = viewModel::onReminderEnabledChange,
                contentDesc = "Ativar lembretes de treino"
            )

            // Req 16.1: reminder time configuration
            if (state.reminderEnabled) {
                OutlinedTextField(
                    value = state.reminderTime,
                    onValueChange = viewModel::onReminderTimeChange,
                    label = { Text("Horário do lembrete (HH:MM)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    supportingText = { Text("Formato 24h, ex: 08:00") }
                )
            }

            Divider(modifier = Modifier.padding(vertical = 8.dp))

            // ── Auto-detect section ──────────────────────────────────────────
            SectionHeader(title = "Detecção Automática")

            // Req 15.5: enable/disable auto-detect toggle
            PreferenceRow(
                label = "Detecção automática de treino",
                description = "O watch sugere iniciar treino ao detectar movimentos repetitivos",
                checked = state.autoDetectEnabled,
                onCheckedChange = viewModel::onAutoDetectEnabledChange,
                contentDesc = "Ativar detecção automática de treino"
            )

            Divider(modifier = Modifier.padding(vertical = 8.dp))

            // ── Save button ──────────────────────────────────────────────────
            Button(
                onClick = viewModel::saveSettings,
                enabled = !state.isSaving,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp) // Req 18.4: min 56dp touch target
            ) {
                if (state.isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.height(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Salvar configurações")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
    )
}

@Composable
private fun PreferenceRow(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    contentDesc: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .semantics { contentDescription = contentDesc },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}
