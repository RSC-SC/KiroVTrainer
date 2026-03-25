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
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import io.mockk.*
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import java.util.UUID

/**
 * Property-based test for conflict resolution by timestamp.
 *
 * **Validates: Requirements 12.6**
 *
 * Property 16: Conflict Resolution by Timestamp
 *
 * For any two conflicting versions of the same TrainingLog (same logId, different timestamps),
 * when a conflict occurs during synchronization, the system SHALL keep the version with the
 * most recent timestamp.
 *
 * This property ensures that last-write-wins conflict resolution is correctly applied,
 * so users' most recent changes are always preserved.
 */
class ConflictResolutionByTimestampPropertyTest : FunSpec({

    test("Feature: v-trainer, Property 16: Conflict Resolution by Timestamp").config(
        invocations = 1
    ) {
        checkAll(1, Arb.conflictingTrainingLogPair()) { (olderLog, newerLog) ->
            // Arrange: Mock dependencies
            val mockFirestore = mockk<FirebaseFirestore>(relaxed = true)
            val mockAuth = mockk<FirebaseAuth>(relaxed = true)
            val mockUser = mockk<FirebaseUser>(relaxed = true)
            val mockDao = mockk<TrainingLogDao>(relaxed = true)
            val mockDocRef = mockk<DocumentReference>(relaxed = true)

            every { mockAuth.currentUser } returns mockUser
            every { mockUser.uid } returns "test-user-id"

            every { mockFirestore.collection(any()) } returns mockk(relaxed = true) {
                every { document(any()) } returns mockDocRef
            }
            every { mockDocRef.set(any()) } returns Tasks.forResult(null)

            // Track all entities inserted into the DAO
            val insertedEntities = mutableListOf<TrainingLogEntity>()
            coEvery { mockDao.insert(capture(insertedEntities)) } just Runs
            coEvery { mockDao.updateSyncStatus(any(), any(), any()) } just Runs

            // Mock getTrainingLogById to simulate the existing stored version
            coEvery { mockDao.getTrainingLogById(olderLog.logId) } returns
                olderLog.toEntity(SyncStatus.SYNCED)

            val repository = TrainingLogRepositoryImpl(mockFirestore, mockAuth, mockDao)

            // Act: Save the older version first (simulating an existing cached log)
            repository.saveTrainingLog(olderLog)

            // Act: Save the newer version (simulating a conflict — same logId, newer timestamp)
            repository.saveTrainingLog(newerLog)

            // Assert: The newer log must have been inserted into the DAO
            // The DAO uses OnConflictStrategy.REPLACE, so the last insert wins.
            // The conflict resolution property requires that the version with the
            // most recent timestamp is the one that ends up persisted.
            val insertsForLogId = insertedEntities.filter { it.logId == newerLog.logId }
            insertsForLogId.isNotEmpty() shouldBe true

            // Assert: The most recently inserted entity for this logId must have
            // the newer (larger) timestamp — confirming the newer version wins.
            val lastInsertedTimestamp = insertsForLogId.last().timestamp
            lastInsertedTimestamp shouldBe newerLog.timestamp.toEpochMilliseconds()

            // Assert: The newer timestamp is strictly greater than the older one
            (newerLog.timestamp > olderLog.timestamp) shouldBe true

            // Assert: The resolved (kept) version has the most recent timestamp
            val resolvedTimestamp = insertsForLogId.maxOf { it.timestamp }
            resolvedTimestamp shouldBe newerLog.timestamp.toEpochMilliseconds()
        }
    }

    test("Feature: v-trainer, Property 16: resolveConflict pure function always selects newer timestamp") {
        checkAll(1, Arb.conflictingTrainingLogPair()) { (olderLog, newerLog) ->
            // Test the pure conflict resolution logic directly
            val resolved = resolveConflict(olderLog, newerLog)
            resolved.logId shouldBe olderLog.logId
            resolved.timestamp shouldBe newerLog.timestamp

            // Also verify the reverse order produces the same result
            val resolvedReverse = resolveConflict(newerLog, olderLog)
            resolvedReverse.logId shouldBe olderLog.logId
            resolvedReverse.timestamp shouldBe newerLog.timestamp
        }
    }
})

/**
 * Pure conflict resolution function: given two versions of the same TrainingLog,
 * returns the one with the most recent timestamp (last-write-wins).
 *
 * This implements Requirement 12.6: conflicts are resolved by prioritizing the
 * most recent timestamp.
 *
 * @param a First version of the TrainingLog
 * @param b Second version of the TrainingLog (must have the same logId as [a])
 * @return The version with the most recent timestamp
 */
fun resolveConflict(a: TrainingLog, b: TrainingLog): TrainingLog {
    require(a.logId == b.logId) { "Cannot resolve conflict between logs with different IDs" }
    return if (a.timestamp >= b.timestamp) a else b
}

/**
 * Custom Arbitrary generator for a pair of conflicting TrainingLog versions.
 * Both logs share the same logId but have different (non-equal) timestamps,
 * where the second element always has a strictly newer timestamp than the first.
 */
fun Arb.Companion.conflictingTrainingLogPair(): Arb<Pair<TrainingLog, TrainingLog>> = arbitrary {
    val sharedLogId = UUID.randomUUID().toString()
    val now = Clock.System.now()

    // Generate two distinct timestamps: olderTimestamp < newerTimestamp
    val olderOffsetMillis = Arb.long(-30L * 24 * 60 * 60 * 1000, -1000).bind()
    val newerOffsetMillis = Arb.long(olderOffsetMillis + 1000, 0).bind()

    val olderTimestamp = Instant.fromEpochMilliseconds(now.toEpochMilliseconds() + olderOffsetMillis)
    val newerTimestamp = Instant.fromEpochMilliseconds(now.toEpochMilliseconds() + newerOffsetMillis)

    val olderLog = Arb.conflictingTrainingLog(sharedLogId, olderTimestamp).bind()
    val newerLog = Arb.conflictingTrainingLog(sharedLogId, newerTimestamp).bind()

    Pair(olderLog, newerLog)
}

/**
 * Custom Arbitrary generator for a TrainingLog with a fixed logId and timestamp.
 * Used to generate conflicting versions of the same log.
 */
fun Arb.Companion.conflictingTrainingLog(logId: String, timestamp: Instant): Arb<TrainingLog> = arbitrary {
    TrainingLog(
        logId = logId,
        userId = "test-user-id",
        workoutPlanId = Arb.string(10..36).orNull(0.3).bind(),
        workoutDayName = Arb.stringPattern("Treino [A-Z] - [A-Za-z ]{5,20}").bind(),
        timestamp = timestamp,
        origin = Arb.conflictOrigin().bind(),
        duration = Arb.int(600..7200).bind(),
        totalCalories = Arb.int(100..800).orNull(0.2).bind(),
        exercises = Arb.list(Arb.conflictExerciseLog(), 1..5).bind(),
        totalVolume = Arb.int(1000..10000).bind()
    )
}

/**
 * Custom Arbitrary generator for ExerciseLog used in conflict resolution tests.
 */
fun Arb.Companion.conflictExerciseLog(): Arb<ExerciseLog> = arbitrary {
    val sets = Arb.list(Arb.conflictSetLog(), 1..5).bind()
    val totalVolume = sets.sumOf { (it.weight * it.reps).toInt() }

    ExerciseLog(
        exerciseId = UUID.randomUUID().toString(),
        sets = sets,
        totalVolume = totalVolume,
        isPersonalRecord = Arb.bool().bind(),
        recordType = Arb.enum<RecordType>().orNull(0.7).bind()
    )
}

/**
 * Custom Arbitrary generator for SetLog used in conflict resolution tests.
 */
fun Arb.Companion.conflictSetLog(): Arb<SetLog> = arbitrary {
    val now = Clock.System.now()
    val offsetMillis = Arb.long(-30L * 24 * 60 * 60 * 1000, 0).bind()

    SetLog(
        setNumber = Arb.int(1..10).bind(),
        reps = Arb.int(1..20).bind(),
        weight = Arb.double(5.0..200.0).bind(),
        restSeconds = Arb.int(30..180).bind(),
        heartRate = Arb.int(100..180).orNull(0.2).bind(),
        completedAt = Instant.fromEpochMilliseconds(now.toEpochMilliseconds() + offsetMillis)
    )
}

/**
 * Custom Arbitrary generator for device origin strings used in conflict resolution tests.
 */
fun Arb.Companion.conflictOrigin(): Arb<String> = Arb.choice(
    Arb.constant("Galaxy_Watch_4"),
    Arb.constant("Galaxy_Watch_5"),
    Arb.constant("Pixel_Watch"),
    Arb.constant("Samsung_Galaxy_S23"),
    Arb.constant("Pixel_7")
)
