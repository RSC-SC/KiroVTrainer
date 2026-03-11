package com.vtrainer.app.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity for storing workout plans locally.
 * Supports offline-first architecture with sync status tracking.
 */
@Entity(tableName = "workout_plans")
data class WorkoutPlanEntity(
    @PrimaryKey
    val planId: String,
    val userId: String,
    val name: String,
    val description: String?,
    val trainingDaysJson: String, // JSON serialized list of TrainingDay objects
    val createdAt: Long,
    val updatedAt: Long,
    val syncStatus: SyncStatus,
    val lastSyncAttempt: Long?
)

/**
 * Sync status for offline-first data synchronization.
 */
enum class SyncStatus {
    /**
     * Data is synchronized with Firestore
     */
    SYNCED,
    
    /**
     * Data is waiting to be synchronized to Firestore
     */
    PENDING_SYNC,
    
    /**
     * Synchronization attempt failed, will retry
     */
    SYNC_FAILED
}
