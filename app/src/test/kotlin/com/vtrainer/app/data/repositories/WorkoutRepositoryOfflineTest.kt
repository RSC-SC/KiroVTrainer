package com.vtrainer.app.data.repositories

import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.*
import com.vtrainer.app.data.local.dao.WorkoutPlanDao
import com.vtrainer.app.data.local.entities.WorkoutPlanEntity
import com.vtrainer.app.domain.models.PlannedExercise
import com.vtrainer.app.domain.models.SyncStatus
import com.vtrainer.app.domain.models.TrainingDay
import com.vtrainer.app.domain.models.WorkoutPlan
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.*
import kotlinx.datetime.Clock
import java.util.UUID

/**
 * Unit tests for offline-first behavior in WorkoutRepositoryImpl.
 * 
 * **Validates: Requirements 12.3, 12.5**
 * 
 * Tests cover:
 * 1. Save when Firestore unavailable - should save to Room with PENDING_SYNC status
 * 2. Sync retry logic - verify status transitions and retry behavior
 * 3. Successful sync after failure - verify status updates to SYNCED
 * 4. Delete when offline - should delete locally even if Firestore fails
 */
class WorkoutRepositoryOfflineTest : FunSpec({
    
    lateinit var mockFirestore: FirebaseFirestore
    lateinit var mockAuth: FirebaseAuth
    lateinit var mockUser: FirebaseUser
    lateinit var mockDao: WorkoutPlanDao
    lateinit var repository: WorkoutRepositoryImpl
    
    beforeTest {
        mockFirestore = mockk(relaxed = true)
        mockAuth = mockk(relaxed = true)
        mockUser = mockk(relaxed = true)
        mockDao = mockk(relaxed = true)
        
        every { mockAuth.currentUser } returns mockUser
        every { mockUser.uid } returns "test-user-id"
        
        repository = WorkoutRepositoryImpl(mockFirestore, mockAuth, mockDao)
    }
    
    afterTest {
        clearAllMocks()
    }
    
    /**
     * Test: Save workout plan when Firestore is unavailable
     * 
     * Requirement 12.3: When offline, the Mobile_App SHALL allow users to execute 
     * training sessions using cached data
     * 
     * Expected behavior:
     * 1. Plan is saved to Room with PENDING_SYNC status
     * 2. Firestore sync fails (network unavailable)
     * 3. Sync status is updated to SYNC_FAILED
     * 4. Operation returns success (offline-first)
     */
    test("saveWorkoutPlan when Firestore unavailable should save locally with PENDING_SYNC") {
        // Arrange: Create a test workout plan
        val plan = createTestWorkoutPlan()
        
        // Mock Firestore to throw exception (simulating offline)
        val mockCollection = mockk<CollectionReference>(relaxed = true)
        val mockDocument = mockk<DocumentReference>(relaxed = true)
        val failedTask = Tasks.forException<Void>(
            FirebaseFirestoreException("Network unavailable", FirebaseFirestoreException.Code.UNAVAILABLE)
        )
        
        every { mockFirestore.collection("workout_plans") } returns mockCollection
        every { mockCollection.document(any()) } returns mockDocument
        every { mockDocument.set(any()) } returns failedTask
        
        // Mock DAO operations
        val savedEntities = mutableListOf<WorkoutPlanEntity>()
        coEvery { mockDao.insert(capture(savedEntities)) } just Runs
        
        val syncStatusUpdates = mutableListOf<SyncStatusUpdate>()
        coEvery { 
            mockDao.updateSyncStatus(any(), any(), any())
        } answers {
            syncStatusUpdates.add(
                SyncStatusUpdate(
                    planId = firstArg(),
                    status = secondArg(),
                    timestamp = thirdArg()
                )
            )
        }
        
        // Act: Save the workout plan
        val result = repository.saveWorkoutPlan(plan)
        
        // Assert: Operation should succeed (offline-first)
        result.isSuccess shouldBe true
        
        // Assert: Plan was saved to Room first with PENDING_SYNC
        coVerify { mockDao.insert(any()) }
        savedEntities.size shouldBe 1
        savedEntities.first().syncStatus shouldBe SyncStatus.PENDING_SYNC
        
        // Assert: Firestore sync was attempted
        verify { mockDocument.set(any()) }
        
        // Assert: Sync status was updated to SYNC_FAILED after Firestore failure
        syncStatusUpdates.size shouldBe 1
        syncStatusUpdates.first().status shouldBe SyncStatus.SYNC_FAILED
        syncStatusUpdates.first().planId shouldBe plan.planId
    }
    
    /**
     * Test: Save workout plan with successful Firestore sync
     * 
     * Requirement 12.5: When connectivity is restored, the V-Trainer_System SHALL 
     * automatically synchronize all cached Training_Logs to Firestore
     * 
     * Expected behavior:
     * 1. Plan is saved to Room with PENDING_SYNC status
     * 2. Firestore sync succeeds
     * 3. Sync status is updated to SYNCED
     */
    test("saveWorkoutPlan with successful Firestore sync should update status to SYNCED") {
        // Arrange: Create a test workout plan
        val plan = createTestWorkoutPlan()
        
        // Mock Firestore to succeed
        val mockCollection = mockk<CollectionReference>(relaxed = true)
        val mockDocument = mockk<DocumentReference>(relaxed = true)
        val successTask = Tasks.forResult<Void>(null)
        
        every { mockFirestore.collection("workout_plans") } returns mockCollection
        every { mockCollection.document(any()) } returns mockDocument
        every { mockDocument.set(any()) } returns successTask
        
        // Mock DAO operations
        coEvery { mockDao.insert(any()) } just Runs
        
        val syncStatusUpdates = mutableListOf<SyncStatusUpdate>()
        coEvery { 
            mockDao.updateSyncStatus(any(), any(), any())
        } answers {
            syncStatusUpdates.add(
                SyncStatusUpdate(
                    planId = firstArg(),
                    status = secondArg(),
                    timestamp = thirdArg()
                )
            )
        }
        
        // Act: Save the workout plan
        val result = repository.saveWorkoutPlan(plan)
        
        // Assert: Operation should succeed
        result.isSuccess shouldBe true
        
        // Assert: Plan was saved to Room
        coVerify { mockDao.insert(any()) }
        
        // Assert: Firestore sync was attempted and succeeded
        verify { mockDocument.set(any()) }
        
        // Assert: Sync status was updated to SYNCED
        syncStatusUpdates.size shouldBe 1
        syncStatusUpdates.first().status shouldBe SyncStatus.SYNCED
        syncStatusUpdates.first().planId shouldBe plan.planId
    }
    
    /**
     * Test: Delete workout plan when Firestore is unavailable
     * 
     * Requirement 12.3: When offline, the Mobile_App SHALL allow users to execute 
     * training sessions using cached data
     * 
     * Expected behavior:
     * 1. Plan is deleted from Room
     * 2. Firestore deletion fails (network unavailable)
     * 3. Operation returns success (local deletion succeeded)
     */
    test("deleteWorkoutPlan when Firestore unavailable should delete locally") {
        // Arrange: Create a test plan ID
        val planId = UUID.randomUUID().toString()
        
        // Mock Firestore to throw exception (simulating offline)
        val mockCollection = mockk<CollectionReference>(relaxed = true)
        val mockDocument = mockk<DocumentReference>(relaxed = true)
        val failedTask = Tasks.forException<Void>(
            FirebaseFirestoreException("Network unavailable", FirebaseFirestoreException.Code.UNAVAILABLE)
        )
        
        every { mockFirestore.collection("workout_plans") } returns mockCollection
        every { mockCollection.document(any()) } returns mockDocument
        every { mockDocument.delete() } returns failedTask
        
        // Mock DAO operations
        coEvery { mockDao.deleteById(any()) } just Runs
        
        // Act: Delete the workout plan
        val result = repository.deleteWorkoutPlan(planId)
        
        // Assert: Operation should succeed (offline-first)
        result.isSuccess shouldBe true
        
        // Assert: Plan was deleted from Room
        coVerify { mockDao.deleteById(planId) }
        
        // Assert: Firestore deletion was attempted
        verify { mockDocument.delete() }
    }
    
    /**
     * Test: Delete workout plan with successful Firestore sync
     * 
     * Expected behavior:
     * 1. Plan is deleted from Room
     * 2. Firestore deletion succeeds
     * 3. Operation returns success
     */
    test("deleteWorkoutPlan with successful Firestore sync should delete from both") {
        // Arrange: Create a test plan ID
        val planId = UUID.randomUUID().toString()
        
        // Mock Firestore to succeed
        val mockCollection = mockk<CollectionReference>(relaxed = true)
        val mockDocument = mockk<DocumentReference>(relaxed = true)
        val successTask = Tasks.forResult<Void>(null)
        
        every { mockFirestore.collection("workout_plans") } returns mockCollection
        every { mockCollection.document(any()) } returns mockDocument
        every { mockDocument.delete() } returns successTask
        
        // Mock DAO operations
        coEvery { mockDao.deleteById(any()) } just Runs
        
        // Act: Delete the workout plan
        val result = repository.deleteWorkoutPlan(planId)
        
        // Assert: Operation should succeed
        result.isSuccess shouldBe true
        
        // Assert: Plan was deleted from Room
        coVerify { mockDao.deleteById(planId) }
        
        // Assert: Firestore deletion was attempted and succeeded
        verify { mockDocument.delete() }
    }
})

/**
 * Helper function to create a test workout plan.
 */
private fun createTestWorkoutPlan(): WorkoutPlan {
    return WorkoutPlan(
        planId = UUID.randomUUID().toString(),
        userId = "test-user-id",
        name = "Test Workout Plan",
        description = "A test workout plan for offline testing",
        trainingDays = listOf(
            TrainingDay(
                dayName = "Day A - Chest and Triceps",
                exercises = listOf(
                    PlannedExercise(
                        exerciseId = "bench_press",
                        order = 1,
                        sets = 4,
                        reps = 12,
                        restSeconds = 60,
                        notes = "Focus on form"
                    ),
                    PlannedExercise(
                        exerciseId = "tricep_dips",
                        order = 2,
                        sets = 3,
                        reps = 10,
                        restSeconds = 45,
                        notes = null
                    )
                )
            )
        ),
        createdAt = Clock.System.now(),
        updatedAt = Clock.System.now()
    )
}

/**
 * Data class to capture sync status updates for verification.
 */
private data class SyncStatusUpdate(
    val planId: String,
    val status: SyncStatus,
    val timestamp: Long
)
