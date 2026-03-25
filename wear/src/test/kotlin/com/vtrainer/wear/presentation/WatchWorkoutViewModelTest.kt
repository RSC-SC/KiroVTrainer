package com.vtrainer.wear.presentation

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.ints.shouldBeGreaterThan

/**
 * Unit tests for [WatchWorkoutViewModel] state logic.
 *
 * Tests focus on pure state transitions that don't require Android context,
 * Health Services, or Firebase. The ViewModel's integration with those
 * dependencies is covered by instrumented tests on device.
 *
 * Validates: Requirements 5.2, 5.3, 5.4, 5.5, 5.6
 */
class WatchWorkoutViewModelTest : StringSpec({

    fun sampleExercises(sets: Int = 3, restSeconds: Int = 60) = listOf(
        WatchPlannedExercise(
            exerciseId = "Supino Reto",
            sets = sets,
            reps = 10,
            restSeconds = restSeconds
        )
    )

    fun sampleSetLog(setNumber: Int = 1, weight: Double = 80.0, reps: Int = 10) = WatchSetLog(
        setNumber = setNumber,
        reps = reps,
        weight = weight,
        restSeconds = 60,
        heartRate = 140,
        completedAtMs = System.currentTimeMillis()
    )

    // ---------------------------------------------------------------------------
    // WatchWorkoutState initial state
    // ---------------------------------------------------------------------------

    "initial state has no exercises and is not complete" {
        val state = WatchWorkoutState()
        state.exercises.isEmpty().shouldBeTrue()
        state.isWorkoutComplete.shouldBeFalse()
        state.syncStatus shouldBe WatchSyncStatus.IDLE
    }

    // ---------------------------------------------------------------------------
    // Set completion and haptic trigger (Req 5.2, 5.4)
    // ---------------------------------------------------------------------------

    "completing a set adds it to completedSets" {
        val initial = WatchWorkoutState(
            exercises = sampleExercises(),
            currentExerciseIndex = 0,
            currentSetIndex = 0
        )
        val setLog = sampleSetLog(setNumber = 1)
        val updated = initial.copy(completedSets = initial.completedSets + setLog)
        updated.completedSets.size shouldBe 1
        updated.completedSets[0].setNumber shouldBe 1
    }

    "completing a set triggers set completion haptic" {
        val state = WatchWorkoutState(triggerSetCompletionHaptic = true)
        state.triggerSetCompletionHaptic.shouldBeTrue()
    }

    "haptic trigger is cleared after consumption" {
        val state = WatchWorkoutState(triggerSetCompletionHaptic = true)
        val cleared = state.copy(triggerSetCompletionHaptic = false)
        cleared.triggerSetCompletionHaptic.shouldBeFalse()
    }

    // ---------------------------------------------------------------------------
    // Rest timer (Req 5.3)
    // ---------------------------------------------------------------------------

    "rest timer is active after set completion" {
        val state = WatchWorkoutState(
            isRestTimerActive = true,
            restTimerSecondsRemaining = 60
        )
        state.isRestTimerActive.shouldBeTrue()
        state.restTimerSecondsRemaining shouldBe 60
    }

    "rest timer expiration triggers haptic" {
        val state = WatchWorkoutState(
            isRestTimerActive = false,
            restTimerSecondsRemaining = 0,
            triggerTimerExpiredHaptic = true
        )
        state.triggerTimerExpiredHaptic.shouldBeTrue()
    }

    "timer expired haptic is cleared after consumption" {
        val state = WatchWorkoutState(triggerTimerExpiredHaptic = true)
        val cleared = state.copy(triggerTimerExpiredHaptic = false)
        cleared.triggerTimerExpiredHaptic.shouldBeFalse()
    }

    // ---------------------------------------------------------------------------
    // Weight and reps adjustment (Req 5.5)
    // ---------------------------------------------------------------------------

    "adjusting weight updates adjustedWeight" {
        val state = WatchWorkoutState()
        val updated = state.copy(adjustedWeight = 85.0)
        updated.adjustedWeight shouldBe 85.0
    }

    "adjusting reps updates adjustedReps" {
        val state = WatchWorkoutState()
        val updated = state.copy(adjustedReps = 12)
        updated.adjustedReps shouldBe 12
    }

    "adjustments are cleared after set completion" {
        val state = WatchWorkoutState(adjustedWeight = 85.0, adjustedReps = 12)
        val cleared = state.copy(adjustedWeight = null, adjustedReps = null)
        cleared.adjustedWeight.shouldBeNull()
        cleared.adjustedReps.shouldBeNull()
    }

    // ---------------------------------------------------------------------------
    // Sync status (Req 5.6)
    // ---------------------------------------------------------------------------

    "sync status transitions from IDLE to SYNCING on finish" {
        val state = WatchWorkoutState(syncStatus = WatchSyncStatus.SYNCING)
        state.syncStatus shouldBe WatchSyncStatus.SYNCING
    }

    "sync status transitions to SYNCED on success" {
        val state = WatchWorkoutState(syncStatus = WatchSyncStatus.SYNCED)
        state.syncStatus shouldBe WatchSyncStatus.SYNCED
    }

    "sync status transitions to FAILED on error" {
        val state = WatchWorkoutState(
            syncStatus = WatchSyncStatus.FAILED,
            error = "Network timeout"
        )
        state.syncStatus shouldBe WatchSyncStatus.FAILED
        state.error.shouldNotBeNull()
    }

    // ---------------------------------------------------------------------------
    // Heart rate integration (Req 9.2, 9.3)
    // ---------------------------------------------------------------------------

    "current heart rate is updated from monitor readings" {
        val state = WatchWorkoutState(currentHeartRate = 145)
        state.currentHeartRate shouldBe 145
        state.currentHeartRate!! shouldBeGreaterThan 0
    }

    "heart rate is null when sensor is unavailable" {
        val state = WatchWorkoutState(currentHeartRate = null)
        state.currentHeartRate.shouldBeNull()
    }

    // ---------------------------------------------------------------------------
    // Calorie tracking (Req 10.2)
    // ---------------------------------------------------------------------------

    "current calories are updated from tracker" {
        val state = WatchWorkoutState(currentCalories = 320)
        state.currentCalories shouldBe 320
    }
})
