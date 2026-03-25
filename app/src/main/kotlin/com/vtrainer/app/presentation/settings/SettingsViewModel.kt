package com.vtrainer.app.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.vtrainer.app.presentation.auth.FitnessLevel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * UI state for the Settings screen.
 *
 * Validates: Requirements 1.1, 1.2, 15.5, 16.1, 16.5
 */
data class SettingsState(
    // Profile fields
    val name: String = "",
    val currentWeight: String = "",
    val fitnessLevel: FitnessLevel = FitnessLevel.BEGINNER,
    val trainingGoal: String = "",
    // Reminder preferences
    val reminderEnabled: Boolean = false,
    val reminderTime: String = "08:00",
    // Auto-detect preference
    val autoDetectEnabled: Boolean = true,
    // UI state
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val saveSuccess: Boolean = false,
    val error: String? = null
)

/**
 * ViewModel for the Settings screen.
 *
 * Loads user profile and preferences from Firestore, allows editing, and
 * syncs changes back to Firestore within 5 seconds (Req 1.2).
 *
 * Validates: Requirements 1.1, 1.2, 15.5, 16.1, 16.5
 */
class SettingsViewModel(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    init {
        loadSettings()
    }

    /** Loads the user profile and preferences from Firestore. */
    fun loadSettings() {
        val userId = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                val doc = firestore.collection("users").document(userId).get().await()
                val prefs = doc.get("preferences") as? Map<*, *>

                val fitnessLevelStr = doc.getString("fitnessLevel") ?: "beginner"
                val fitnessLevel = FitnessLevel.entries.firstOrNull {
                    it.firestoreValue == fitnessLevelStr
                } ?: FitnessLevel.BEGINNER

                _state.value = SettingsState(
                    name = doc.getString("name") ?: "",
                    currentWeight = (doc.getDouble("currentWeight") ?: 0.0).let {
                        if (it == 0.0) "" else it.toString()
                    },
                    fitnessLevel = fitnessLevel,
                    trainingGoal = doc.getString("trainingGoal") ?: "",
                    reminderEnabled = prefs?.get("reminderEnabled") as? Boolean ?: false,
                    reminderTime = prefs?.get("reminderTime") as? String ?: "08:00",
                    autoDetectEnabled = prefs?.get("autoDetectEnabled") as? Boolean ?: true,
                    isLoading = false
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = e.message ?: "Falha ao carregar configurações"
                )
            }
        }
    }

    // --- Field update functions ---

    fun onNameChange(value: String) {
        _state.value = _state.value.copy(name = value, saveSuccess = false)
    }

    fun onWeightChange(value: String) {
        _state.value = _state.value.copy(currentWeight = value, saveSuccess = false)
    }

    fun onFitnessLevelChange(level: FitnessLevel) {
        _state.value = _state.value.copy(fitnessLevel = level, saveSuccess = false)
    }

    fun onTrainingGoalChange(value: String) {
        _state.value = _state.value.copy(trainingGoal = value, saveSuccess = false)
    }

    fun onReminderEnabledChange(enabled: Boolean) {
        _state.value = _state.value.copy(reminderEnabled = enabled, saveSuccess = false)
    }

    fun onReminderTimeChange(time: String) {
        _state.value = _state.value.copy(reminderTime = time, saveSuccess = false)
    }

    fun onAutoDetectEnabledChange(enabled: Boolean) {
        _state.value = _state.value.copy(autoDetectEnabled = enabled, saveSuccess = false)
    }

    /**
     * Saves the current settings to Firestore.
     * Syncs within 5 seconds as required by Req 1.2.
     */
    fun saveSettings() {
        val userId = auth.currentUser?.uid ?: return
        val current = _state.value
        viewModelScope.launch {
            _state.value = current.copy(isSaving = true, error = null, saveSuccess = false)
            try {
                val data = mapOf(
                    "name" to current.name,
                    "currentWeight" to (current.currentWeight.toDoubleOrNull() ?: 0.0),
                    "fitnessLevel" to current.fitnessLevel.firestoreValue,
                    "trainingGoal" to current.trainingGoal,
                    "updatedAt" to com.google.firebase.Timestamp.now(),
                    "preferences" to mapOf(
                        "reminderEnabled" to current.reminderEnabled,
                        "reminderTime" to current.reminderTime,
                        "autoDetectEnabled" to current.autoDetectEnabled
                    )
                )
                firestore.collection("users").document(userId)
                    .update(data)
                    .await()
                _state.value = _state.value.copy(isSaving = false, saveSuccess = true)
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isSaving = false,
                    error = e.message ?: "Falha ao salvar configurações"
                )
            }
        }
    }
}
