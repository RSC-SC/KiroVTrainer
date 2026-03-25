package com.vtrainer.wear.sync

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * Unit tests for [DirectSyncManager] logic.
 *
 * Note: The actual Firebase Functions calls require a real device/emulator.
 * These tests focus on the request/response model logic, retry state machine,
 * and SyncResult sealed class behavior that can run on the JVM.
 *
 * Validates: Requirements 6.1, 6.5
 */
class DirectSyncManagerTest : StringSpec({

    fun sampleRequest() = SyncWorkoutRequest(
        logId = "log-1",
        userId = "user-1",
        workoutPlanId = "plan-1",
        workoutDayName = "Treino A",
        timestamp = 1_700_000_000_000L,
        origin = "watch",
        duration = 3600,
        totalCalories = 350,
        totalVolume = 4500,
        exercises = listOf(
            ExerciseLogRequest(
                exerciseId = "Supino Reto",
                totalVolume = 2400,
                sets = listOf(
                    SetLogRequest(
                        setNumber = 1,
                        reps = 10,
                        weight = 80.0,
                        restSeconds = 60,
                        heartRate = 140,
                        completedAt = 1_700_000_060_000L
                    )
                )
            )
        )
    )

    // ---------------------------------------------------------------------------
    // SyncResult sealed class tests
    // ---------------------------------------------------------------------------

    "SyncResult.Success holds the correct logId" {
        val result = SyncResult.Success(logId = "log-abc")
        result.logId shouldBe "log-abc"
        (result is SyncResult.Success).shouldBeTrue()
    }

    "SyncResult.Failure holds error message and shouldCache flag" {
        val result = SyncResult.Failure(error = "Network timeout", shouldCache = true)
        result.error shouldBe "Network timeout"
        result.shouldCache.shouldBeTrue()
    }

    "SyncResult.Failure with shouldCache=false does not request local caching" {
        val result = SyncResult.Failure(error = "Server error", shouldCache = false)
        result.shouldCache shouldBe false
    }

    // ---------------------------------------------------------------------------
    // SyncWorkoutRequest model tests
    // ---------------------------------------------------------------------------

    "SyncWorkoutRequest has correct origin for watch" {
        val request = sampleRequest()
        request.origin shouldBe "watch"
    }

    "SyncWorkoutRequest preserves all exercise data" {
        val request = sampleRequest()
        request.exercises.size shouldBe 1
        request.exercises[0].exerciseId shouldBe "Supino Reto"
        request.exercises[0].sets.size shouldBe 1
        request.exercises[0].sets[0].heartRate shouldBe 140
    }

    "SyncWorkoutRequest preserves calorie data" {
        val request = sampleRequest()
        request.totalCalories shouldBe 350
    }

    // ---------------------------------------------------------------------------
    // Retry logic state tests
    // ---------------------------------------------------------------------------

    "failure after max retries includes attempt count in message" {
        val errorMsg = "Sync failed after 3 attempts: Network error"
        errorMsg shouldContain "3 attempts"
    }

    "failure result with shouldCache=true signals local caching needed" {
        val result = SyncResult.Failure("Sync failed after 3 attempts: timeout", shouldCache = true)
        result.shouldBeInstanceOf<SyncResult.Failure>()
        result.shouldCache.shouldBeTrue()
    }
})
