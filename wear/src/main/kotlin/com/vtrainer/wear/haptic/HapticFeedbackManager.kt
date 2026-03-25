package com.vtrainer.wear.haptic

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.os.Build

/**
 * Manages haptic feedback patterns for the watch workout screen.
 *
 * Patterns:
 * - Set completion: short double-pulse (Req 5.4, 13.2)
 * - Timer expiration: long vibration ≥ 1 second (Req 5.4, 13.3)
 *
 * Validates: Requirements 5.4, 13.2, 13.3
 */
class HapticFeedbackManager(context: Context) {

    private val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        manager.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    /**
     * Short double-pulse to signal set completion.
     *
     * Pattern: 100ms on, 100ms off, 100ms on
     *
     * Validates: Requirements 5.4, 13.2
     */
    fun vibrateSetComplete() {
        if (!vibrator.hasVibrator()) return
        val pattern = longArrayOf(0L, 100L, 100L, 100L)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(pattern, -1)
        }
    }

    /**
     * Long vibration (1 second) to signal rest timer expiration.
     *
     * Validates: Requirements 5.4, 13.3
     */
    fun vibrateTimerExpired() {
        if (!vibrator.hasVibrator()) return
        val durationMs = 1_000L
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(durationMs)
        }
    }

    /**
     * Cancels any ongoing vibration.
     */
    fun cancel() {
        vibrator.cancel()
    }
}
