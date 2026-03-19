package com.vtrainer.app.data.repositories

import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.vtrainer.app.data.local.dao.TrainingLogDao
import com.vtrainer.app.data.local.entities.TrainingLogEntity
import com.vtrainer.app.data.mappers.toEntity
import com.vtrainer.app.domain.models.*
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import io.mockk.*
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import java.util.UUID

/**
 * Property-based test for offline cache synchronization completeness.
 * 
 * **Validates: Requirements 6.5, 12.5**
 * 
 * Property 15: Offline Cache Synchronization Completeness
 * 
 * For any TrainingLog cached locally while offline, when connectivity is restored,
 * the log SHALL be synchronized to Firestore and marked as SYNCED.
 * 
 * This property ensures no workout data is lost due to connectivity issues.
 * All offline workouts must eventually reach the cloud for backup and cross-device access.
 */
class OfflineCacheSynchronizationPropertyTest : FunSpec({
    
    test("Feature: v-trainer, Property 15: Offline Cache Synchronization Completeness").config(
        invocations = 100
    ) {
        checkAll(100, Arb.trainingLogList()) { trainingLogs ->
            // Arrange: Mock dependencies
            val mockFirestore = mockk<FirebaseFirestore>(relaxed = true)
            val mockAuth = mockk<FirebaseAuth>(relaxed = true)
            val mockUser = mockk<FirebaseUser>(relaxed = true)
            val mockDao = mockk<TrainingLogDao>(relaxed = true)
            val mockDocRef = mockk<DocumentReference>(relaxed = true)
            
            every { mockAuth.currentUser } returns mockUser
            every { mockUser.uid } returns "test-user-id"
            
            // Mock Firestore collection and document operations
            every { mockFirestore.collection(any()) } returns mockk(relaxed = true) {
                every { document(any()) } returns mockDocRef
            }
            
            // Mock successful Firestore writes
            every { mockDocRef.set(any()) } returns Tasks.forResult(null)
            
            // Convert training logs to entities with PENDING_SYNC status
            val pendingEntities = trainingLogs.map { log ->
                log.toEntity(SyncStatus.PENDING_SYNC)
            }
            
            // Mock DAO to return pending logs
            coEvery { 
                mockDao.getPendingSyncLogs(
                    userId = "test-user-id",
                    syncStatuses = listOf(SyncStatus.PENDING_SYNC, SyncStatus.SYNC_FAILED)
                )
            } returns pendingEntities
            
            // Track sync status updates
            val syncStatusUpdates = mutableMapOf<String, SyncStatus>()
            coEvery { 
                mockDao.updateSyncStatus(
                    logId = capture(slot<String>()),
                    syncStatus = capture(slot<SyncStatus>()),
                    lastSyncAttempt = any()
                )
            } answers {
                val logId = firstArg<String>()
                val status = secondArg<SyncStatus>()
                syncStatusUpdates[logId] = status
            }
            
            val repository = TrainingLogRepositoryImpl(mockFirestore, mockAuth, mockDao)
            
            // Act: Synchronize pending logs (simulating connectivity restoration)
            val result = repository.syncPendingLogs()
            
            // Assert: Sync should succeed
            result.isSuccess shouldBe true
            
            // Assert: All logs should be successfully synced
            val syncedCount = result.getOrNull()
            syncedCount shouldBe trainingLogs.size
            
            // Assert: All logs should have their sync status updated to SYNCED
            val allLogIds = trainingLogs.map { it.logId }
            syncStatusUpdates.keys shouldContainAll allLogIds
            
            // Assert: All sync status updates should be SYNCED (not SYNC_FAILED)
            syncStatusUpdates.values.forEach { status ->
                status shouldBe SyncStatus.SYNCED
            }
            
            // Assert: Firestore set was called for each log
            verify(exactly = trainingLogs.size) { mockDocRef.set(any()) }
        }
    }
})

/**
 * Custom Arbitrary generator for a list of TrainingLog objects.
 * Generates 1-10 training logs for property testing.
 */
fun Arb.Companion.trainingLogList(): Arb<List<TrainingLog>> = 
    Arb.list(Arb.trainingLog(), 1..10)

/**
 * Custom Arbitrary generator for TrainingLog.
 * Generates realistic training log data for property testing.
 */
fun Arb.Companion.trainingLog(): Arb<TrainingLog> = arbitrary {
    TrainingLog(
        logId = UUID.randomUUID().toString(),
        userId = "test-user-id",
        workoutPlanId = Arb.string(10..36).orNull(0.3).bind(),
        workoutDayName = Arb.stringPattern("Treino [A-Z] - [A-Za-z ]{5,20}").bind(),
        timestamp = Arb.trainingLogInstant().bind(),
        origin = Arb.deviceOrigin().bind(),
        duration = Arb.int(600..7200).bind(), // 10 minutes to 2 hours
        totalCalories = Arb.int(100..800).orNull(0.2).bind(),
        exercises = Arb.list(Arb.exerciseLog(), 1..8).bind(),
        totalVolume = Arb.int(1000..10000).bind()
    )
}

/**
 * Custom Arbitrary generator for ExerciseLog.
 */
fun Arb.Companion.exerciseLog(): Arb<ExerciseLog> = arbitrary {
    val sets = Arb.list(Arb.setLog(), 1..5).bind()
    val totalVolume = sets.sumOf { (it.weight * it.reps).toInt() }
    
    ExerciseLog(
        exerciseId = UUID.randomUUID().toString(),
        sets = sets,
        totalVolume = totalVolume,
        isPersonalRecord = Arb.bool().bind(),
        recordType = Arb.recordType().orNull(0.7).bind()
    )
}

/**
 * Custom Arbitrary generator for SetLog.
 */
fun Arb.Companion.setLog(): Arb<SetLog> = arbitrary {
    SetLog(
        setNumber = Arb.int(1..10).bind(),
        reps = Arb.int(1..20).bind(),
        weight = Arb.double(5.0..200.0).bind(),
        restSeconds = Arb.int(30..180).bind(),
        heartRate = Arb.int(100..180).orNull(0.2).bind(),
        completedAt = Arb.trainingLogInstant().bind()
    )
}

/**
 * Custom Arbitrary generator for RecordType.
 */
fun Arb.Companion.recordType(): Arb<RecordType> = Arb.enum()

/**
 * Custom Arbitrary generator for device origin strings.
 */
fun Arb.Companion.deviceOrigin(): Arb<String> = Arb.choice(
    Arb.constant("Galaxy_Watch_4"),
    Arb.constant("Galaxy_Watch_5"),
    Arb.constant("Pixel_Watch"),
    Arb.constant("Samsung_Galaxy_S23"),
    Arb.constant("Pixel_7")
)

/**
 * Custom Arbitrary generator for Instant within training log context.
 * Generates timestamps within the last 30 days.
 */
fun Arb.Companion.trainingLogInstant(): Arb<Instant> = arbitrary {
    val now = Clock.System.now()
    val offsetMillis = Arb.long(-30L * 24 * 60 * 60 * 1000, 0).bind() // Last 30 days
    Instant.fromEpochMilliseconds(now.toEpochMilliseconds() + offsetMillis)
}
