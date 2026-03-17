package com.vtrainer.app.domain.models

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
