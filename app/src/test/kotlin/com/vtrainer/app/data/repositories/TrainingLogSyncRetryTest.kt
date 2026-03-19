package com.vtrainer.app.data.repositories

import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.vtrainer.app.data.local.dao.TrainingLogDao
import com.vtrainer.app.data.mappers.toEntity
import com.vtrainer.app.domain.models.*
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.longs.shouldBeGreaterThanOrEqual
import io.kotest.matchers.shouldBe
import io.mockk.*
import kotlinx.datetime.Clock
import java.util.UUID

/**
 * Unit tests for sync retry logic in TrainingLogRepositoryImpl.
 *
 * **Validates: Requirements 6.5**
 *
 * Tests cover:
 * 1. Exponential backoff - verify that retry delays increase exponentially (1s, 2s)
 * 2. Max retry attempts - verify that sync stops after 3 failed attempts and marks log as SYNC_FAILED
 */
class TrainingLogSyncRetryTest : FunSpec({

    lateinit var mockFirestore: FirebaseFirestore
    lateinit var mockAuth: FirebaseAuth
    lateinit var mockUser: FirebaseUser
    lateinit var mockDao: TrainingLogDao
    lateinit var mockDocRef: DocumentReference
    lateinit var repository: TrainingLogRepositoryImpl

    beforeTest {
        mockFirestore = mockk(relaxed = true)
        mockAuth = mockk(relaxed = true)
        mockUser = mockk(relaxed = true)
        mockDao = mockk(relaxed = true)
        mockDocRef = mockk(relaxed = true)

        every { mockAuth.currentUser } returns mockUser
        every { mockUser.uid } returns "test-user-id"

        every { mockFirestore.collection(any()) } returns mockk(relaxed = true) {
            every { document(any()) } returns mockDocRef
        }

        repository = TrainingLogRepositoryImpl(mockFirestore, mockAuth, mockDao)
    }

    afterTest {
        clearAllMocks()
    }

    /**
     * Test: Exponential backoff delays increase with each retry attempt.
     *
     * Requirement 6.5: The Watch_App SHALL cache the Training_Log locally and retry
     * synchronization when connectivity is restored.
     *
     * The implementation uses getBackoffDelay(attempt) = 2^(attempt-1) * 1000ms:
     * - Attempt 1: 0ms (no delay, first try)
     * - Attempt 2: 1000ms (1 second)
     * - Attempt 3: 2000ms (2 seconds)
     *
     * We verify this by measuring elapsed time across retries.
     */
    test("syncPendingLogs uses exponential backoff - delays increase between retries") {
        val log = createTestTrainingLog()
        val pendingEntity = log.toEntity(SyncStatus.PENDING_SYNC)

        coEvery {
            mockDao.getPendingSyncLogs(userId = "test-user-id", syncStatuses = any())
        } returns listOf(pendingEntity)

        coEvery { mockDao.updateSyncStatus(any(), any(), any()) } just Runs

        // Track call timestamps to verify delays
        val callTimestamps = mutableListOf<Long>()

        // Fail twice, succeed on third attempt
        var callCount = 0
        every { mockDocRef.set(any()) } answers {
            callTimestamps.add(System.currentTimeMillis())
            callCount++
            if (callCount < 3) {
                Tasks.forException(RuntimeException("Network error: UNAVAILABLE"))
            } else {
                Tasks.forResult(null)
            }
        }

        repository.syncPendingLogs()

        // Should have made exactly 3 attempts
        callTimestamps.size shouldBe 3

        // Delay between attempt 1 and 2 should be >= 1000ms (backoff for attempt 2)
        val delay1to2 = callTimestamps[1] - callTimestamps[0]
        delay1to2 shouldBeGreaterThanOrEqual 1000L

        // Delay between attempt 2 and 3 should be >= 2000ms (backoff for attempt 3)
        val delay2to3 = callTimestamps[2] - callTimestamps[1]
        delay2to3 shouldBeGreaterThanOrEqual 2000L
    }

    /**
     * Test: Sync stops after max 3 failed attempts and marks log as SYNC_FAILED.
     *
     * Requirement 6.5: The Watch_App SHALL cache the Training_Log locally and retry
     * synchronization when connectivity is restored.
     *
     * Expected behavior:
     * - Firestore fails on all 3 attempts
     * - syncPendingLogs returns success with 0 synced logs
     * - The log's sync status is updated to SYNC_FAILED
     */
    test("syncPendingLogs stops after max 3 attempts and marks log as SYNC_FAILED") {
        val log = createTestTrainingLog()
        val pendingEntity = log.toEntity(SyncStatus.PENDING_SYNC)

        coEvery {
            mockDao.getPendingSyncLogs(userId = "test-user-id", syncStatuses = any())
        } returns listOf(pendingEntity)

        val syncStatusUpdates = mutableListOf<Pair<String, SyncStatus>>()
        coEvery {
            mockDao.updateSyncStatus(
                logId = capture(slot<String>()),
                syncStatus = capture(slot<SyncStatus>()),
                lastSyncAttempt = any()
            )
        } answers {
            syncStatusUpdates.add(firstArg<String>() to secondArg())
        }

        // Always fail
        every { mockDocRef.set(any()) } returns Tasks.forException(
            RuntimeException("Network error: UNAVAILABLE")
        )

        val result = repository.syncPendingLogs()

        // Result should be success (the operation itself succeeded, just no logs synced)
        result.isSuccess shouldBe true

        // No logs were successfully synced
        result.getOrNull() shouldBe 0

        // Firestore was attempted exactly 3 times (max retries)
        verify(exactly = 3) { mockDocRef.set(any()) }

        // Sync status should be updated to SYNC_FAILED
        syncStatusUpdates.size shouldBe 1
        syncStatusUpdates.first().first shouldBe log.logId
        syncStatusUpdates.first().second shouldBe SyncStatus.SYNC_FAILED
    }

    /**
     * Test: Sync succeeds on first attempt - no retries needed.
     *
     * Requirement 6.5: When connectivity is available, logs sync immediately.
     *
     * Expected behavior:
     * - Firestore succeeds on first attempt
     * - syncPendingLogs returns 1 synced log
     * - The log's sync status is updated to SYNCED
     */
    test("syncPendingLogs succeeds on first attempt without retries") {
        val log = createTestTrainingLog()
        val pendingEntity = log.toEntity(SyncStatus.PENDING_SYNC)

        coEvery {
            mockDao.getPendingSyncLogs(userId = "test-user-id", syncStatuses = any())
        } returns listOf(pendingEntity)

        val syncStatusUpdates = mutableListOf<Pair<String, SyncStatus>>()
        coEvery {
            mockDao.updateSyncStatus(any(), capture(slot<SyncStatus>()), any())
        } answers {
            syncStatusUpdates.add(firstArg<String>() to secondArg())
        }

        // Always succeed
        every { mockDocRef.set(any()) } returns Tasks.forResult(null)

        val result = repository.syncPendingLogs()

        result.isSuccess shouldBe true
        result.getOrNull() shouldBe 1

        // Firestore was called exactly once (no retries needed)
        verify(exactly = 1) { mockDocRef.set(any()) }

        // Status updated to SYNCED
        syncStatusUpdates.size shouldBe 1
        syncStatusUpdates.first().second shouldBe SyncStatus.SYNCED
    }

    /**
     * Test: Sync succeeds on second attempt after one failure.
     *
     * Requirement 6.5: Retry synchronization when connectivity is restored.
     *
     * Expected behavior:
     * - First attempt fails
     * - Second attempt succeeds
     * - syncPendingLogs returns 1 synced log
     * - The log's sync status is updated to SYNCED
     */
    test("syncPendingLogs succeeds on second attempt after one failure") {
        val log = createTestTrainingLog()
        val pendingEntity = log.toEntity(SyncStatus.PENDING_SYNC)

        coEvery {
            mockDao.getPendingSyncLogs(userId = "test-user-id", syncStatuses = any())
        } returns listOf(pendingEntity)

        val syncStatusUpdates = mutableListOf<Pair<String, SyncStatus>>()
        coEvery {
            mockDao.updateSyncStatus(any(), capture(slot<SyncStatus>()), any())
        } answers {
            syncStatusUpdates.add(firstArg<String>() to secondArg())
        }

        // Fail once, then succeed
        var callCount = 0
        every { mockDocRef.set(any()) } answers {
            callCount++
            if (callCount == 1) {
                Tasks.forException(RuntimeException("Network error: UNAVAILABLE"))
            } else {
                Tasks.forResult(null)
            }
        }

        val result = repository.syncPendingLogs()

        result.isSuccess shouldBe true
        result.getOrNull() shouldBe 1

        // Firestore was called exactly twice (1 failure + 1 success)
        verify(exactly = 2) { mockDocRef.set(any()) }

        // Status updated to SYNCED
        syncStatusUpdates.size shouldBe 1
        syncStatusUpdates.first().second shouldBe SyncStatus.SYNCED
    }

    /**
     * Test: Multiple logs - each gets up to 3 retry attempts independently.
     *
     * Requirement 6.5: All pending logs should be retried.
     *
     * Expected behavior:
     * - Log A fails all 3 attempts -> SYNC_FAILED
     * - Log B succeeds on first attempt -> SYNCED
     * - syncPendingLogs returns 1 (only Log B synced)
     */
    test("syncPendingLogs handles multiple logs independently - failed log does not affect successful log") {
        val logA = createTestTrainingLog(logId = "log-a")
        val logB = createTestTrainingLog(logId = "log-b")

        val entityA = logA.toEntity(SyncStatus.PENDING_SYNC)
        val entityB = logB.toEntity(SyncStatus.PENDING_SYNC)

        coEvery {
            mockDao.getPendingSyncLogs(userId = "test-user-id", syncStatuses = any())
        } returns listOf(entityA, entityB)

        val syncStatusUpdates = mutableMapOf<String, SyncStatus>()
        coEvery {
            mockDao.updateSyncStatus(any(), any(), any())
        } answers {
            syncStatusUpdates[firstArg<String>()] = secondArg()
        }

        // logA's document always fails, logB's always succeeds
        val mockDocRefA = mockk<DocumentReference>(relaxed = true)
        val mockDocRefB = mockk<DocumentReference>(relaxed = true)

        every { mockDocRefA.set(any()) } returns Tasks.forException(
            RuntimeException("Network error: UNAVAILABLE")
        )
        every { mockDocRefB.set(any()) } returns Tasks.forResult(null)

        every { mockFirestore.collection(any()) } returns mockk(relaxed = true) {
            every { document("log-a") } returns mockDocRefA
            every { document("log-b") } returns mockDocRefB
        }

        val result = repository.syncPendingLogs()

        result.isSuccess shouldBe true
        result.getOrNull() shouldBe 1

        // logA should be SYNC_FAILED, logB should be SYNCED
        syncStatusUpdates["log-a"] shouldBe SyncStatus.SYNC_FAILED
        syncStatusUpdates["log-b"] shouldBe SyncStatus.SYNCED

        // logA was retried 3 times, logB only once
        verify(exactly = 3) { mockDocRefA.set(any()) }
        verify(exactly = 1) { mockDocRefB.set(any()) }
    }

    /**
     * Test: Empty pending logs list returns success with 0 count.
     *
     * Edge case: No logs to sync.
     */
    test("syncPendingLogs with no pending logs returns success with count 0") {
        coEvery {
            mockDao.getPendingSyncLogs(userId = "test-user-id", syncStatuses = any())
        } returns emptyList()

        val result = repository.syncPendingLogs()

        result.isSuccess shouldBe true
        result.getOrNull() shouldBe 0

        // No Firestore calls should be made
        verify(exactly = 0) { mockDocRef.set(any()) }
    }
})

/**
 * Helper to create a minimal TrainingLog for testing.
 */
private fun createTestTrainingLog(logId: String = UUID.randomUUID().toString()): TrainingLog {
    val now = Clock.System.now()
    return TrainingLog(
        logId = logId,
        userId = "test-user-id",
        workoutPlanId = null,
        workoutDayName = "Test Day",
        timestamp = now,
        origin = "test",
        duration = 3600,
        totalCalories = 300,
        exercises = listOf(
            ExerciseLog(
                exerciseId = "squat",
                sets = listOf(
                    SetLog(
                        setNumber = 1,
                        reps = 10,
                        weight = 100.0,
                        restSeconds = 60,
                        heartRate = null,
                        completedAt = now
                    )
                ),
                totalVolume = 1000,
                isPersonalRecord = false,
                recordType = null
            )
        ),
        totalVolume = 1000
    )
}
