package com.vtrainer.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.vtrainer.app.data.local.dao.ExerciseDao
import com.vtrainer.app.data.local.dao.TrainingLogDao
import com.vtrainer.app.data.local.dao.WorkoutPlanDao
import com.vtrainer.app.data.local.entities.ExerciseEntity
import com.vtrainer.app.data.local.entities.TrainingLogEntity
import com.vtrainer.app.data.local.entities.WorkoutPlanEntity

/**
 * Room database for V-Trainer application.
 * Provides local caching for offline-first architecture.
 * 
 * This database integrates:
 * - WorkoutPlanEntity with WorkoutPlanDao for workout plan management
 * - TrainingLogEntity with TrainingLogDao for training history
 * - ExerciseEntity with ExerciseDao for exercise library caching
 * 
 * Requirements: 12.1 - Offline-First Data Architecture with Room database
 */

@Database(
    entities = [
        WorkoutPlanEntity::class,
        TrainingLogEntity::class,
        ExerciseEntity::class
    ],
    version = 1,
    exportSchema = false
)

@TypeConverters(Converters::class)
abstract class VTrainerDatabase : RoomDatabase() {
    
    /**
     * Provides access to workout plan operations.
     */
    abstract fun workoutPlanDao(): WorkoutPlanDao
    
    /**
     * Provides access to training log operations.
     */
    abstract fun trainingLogDao(): TrainingLogDao
    
    /**
     * Provides access to exercise library operations.
     */
    abstract fun exerciseDao(): ExerciseDao
    
    companion object {
        private const val DATABASE_NAME = "vtrainer_database"
        
        @Volatile
        private var INSTANCE: VTrainerDatabase? = null
        
        /**
         * Gets the singleton instance of VTrainerDatabase.
         * 
         * Uses double-checked locking pattern for thread-safe initialization.
         * Configures database with fallback strategies for data integrity:
         * - fallbackToDestructiveMigration: Recreates database if migration fails
         *   (acceptable for cache data that can be re-synced from Firestore)
         * 
         * @param context Application context
         * @return Singleton database instance
         */
        fun getInstance(context: Context): VTrainerDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }
        }
        
        /**
         * Builds the Room database with appropriate configuration.
         * 
         * Fallback Strategy:
         * - Uses fallbackToDestructiveMigration for version 1
         * - This is acceptable because all data is synced with Firestore
         * - In case of migration failure, data can be re-downloaded from cloud
         * - Future versions should implement proper migrations for better UX
         * 
         * @param context Application context
         * @return Configured database instance
         */
        private fun buildDatabase(context: Context): VTrainerDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                VTrainerDatabase::class.java,
                DATABASE_NAME
            )
                .fallbackToDestructiveMigration() // Recreate DB if migration fails
                .build()
        }
        
        /**
         * Clears the database instance.
         * Used for testing purposes only.
         */
        @androidx.annotation.VisibleForTesting
        fun clearInstance() {
            INSTANCE = null
        }
    }
}
