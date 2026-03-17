package com.vtrainer.app.data.local

import androidx.room.TypeConverter
import com.vtrainer.app.domain.models.SyncStatus

/**
 * Type converters for Room database.
 * Converts complex types to primitive types that SQLite can store.
 */
class Converters {
    
    /**
     * Converts SyncStatus enum to String for database storage.
     * 
     * @param syncStatus The SyncStatus enum value
     * @return String representation of the enum
     */
    @TypeConverter
    fun fromSyncStatus(syncStatus: SyncStatus): String {
        return syncStatus.name
    }
    
    /**
     * Converts String from database to SyncStatus enum.
     * 
     * @param value String representation of the enum
     * @return SyncStatus enum value
     */
    @TypeConverter
    fun toSyncStatus(value: String): SyncStatus {
        return SyncStatus.valueOf(value)
    }
}
