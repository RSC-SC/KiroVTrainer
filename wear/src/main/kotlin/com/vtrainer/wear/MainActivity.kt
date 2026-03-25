package com.vtrainer.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.material.MaterialTheme
import com.vtrainer.wear.haptic.HapticFeedbackManager
import com.vtrainer.wear.presentation.WatchWorkoutScreen
import com.vtrainer.wear.presentation.WatchWorkoutViewModel

class MainActivity : ComponentActivity() {

    private lateinit var hapticManager: HapticFeedbackManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hapticManager = HapticFeedbackManager(this)

        setContent {
            MaterialTheme {
                // In a real app the ViewModel factory would inject dependencies.
                // For now we rely on the default factory (no-arg constructor not
                // available here — this wiring is illustrative; DI would handle it).
                WatchWorkoutScreen(
                    viewModel = viewModel(),
                    onHapticSetComplete = { hapticManager.vibrateSetComplete() },
                    onHapticTimerExpired = { hapticManager.vibrateTimerExpired() }
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        hapticManager.cancel()
    }
}
