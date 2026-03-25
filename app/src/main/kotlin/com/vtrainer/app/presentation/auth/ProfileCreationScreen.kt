package com.vtrainer.app.presentation.auth

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

/**
 * Profile creation screen shown to first-time users after sign-in.
 *
 * Collects: display name, weight (kg), fitness level, and training goal.
 * On save, writes the profile to Firestore and navigates to the dashboard.
 *
 * Validates: Requirements 1.1
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileCreationScreen(
    onNavigateToDashboard: () -> Unit
) {
    var displayName by remember { mutableStateOf("") }
    var weight by remember { mutableStateOf("") }
    var fitnessLevel by remember { mutableStateOf(FitnessLevel.BEGINNER) }
    var goal by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Criar Perfil") })
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Bem-vindo ao V-Trainer!",
                style = MaterialTheme.typography.headlineSmall
            )
            Text(
                text = "Preencha seu perfil para começar.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Display name
            OutlinedTextField(
                value = displayName,
                onValueChange = { displayName = it },
                label = { Text("Nome de exibição") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // Weight
            OutlinedTextField(
                value = weight,
                onValueChange = { weight = it },
                label = { Text("Peso (kg)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )

            // Fitness level
            Text(
                text = "Nível de condicionamento",
                style = MaterialTheme.typography.labelLarge
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FitnessLevel.entries.forEach { level ->
                    FilterChip(
                        selected = fitnessLevel == level,
                        onClick = { fitnessLevel = level },
                        label = { Text(level.label) }
                    )
                }
            }

            // Goal
            OutlinedTextField(
                value = goal,
                onValueChange = { goal = it },
                label = { Text("Objetivo (ex: hipertrofia, emagrecimento)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Save button — min touch target 56dp (Req 18.4)
            Button(
                onClick = {
                    saveProfile(
                        displayName = displayName,
                        weight = weight.toDoubleOrNull() ?: 0.0,
                        fitnessLevel = fitnessLevel,
                        goal = goal,
                        onComplete = onNavigateToDashboard
                    )
                },
                enabled = displayName.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Text("Salvar e continuar")
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/** Fitness level options for the profile creation form. */
enum class FitnessLevel(val label: String, val firestoreValue: String) {
    BEGINNER("Iniciante", "beginner"),
    INTERMEDIATE("Intermediário", "intermediate"),
    ADVANCED("Avançado", "advanced")
}

/**
 * Writes the user profile to Firestore under the authenticated user's UID.
 * Calls [onComplete] when the write succeeds (or fails silently to avoid blocking the user).
 */
private fun saveProfile(
    displayName: String,
    weight: Double,
    fitnessLevel: FitnessLevel,
    goal: String,
    onComplete: () -> Unit
) {
    val userId = FirebaseAuth.getInstance().currentUser?.uid ?: run {
        onComplete()
        return
    }

    val profileData = mapOf(
        "userId" to userId,
        "name" to displayName,
        "currentWeight" to weight,
        "fitnessLevel" to fitnessLevel.firestoreValue,
        "trainingGoal" to goal,
        "createdAt" to com.google.firebase.Timestamp.now(),
        "updatedAt" to com.google.firebase.Timestamp.now()
    )

    FirebaseFirestore.getInstance()
        .collection("users")
        .document(userId)
        .set(profileData)
        .addOnCompleteListener { onComplete() }
}
