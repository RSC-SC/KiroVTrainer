package com.vtrainer.app.data.local.dao

import androidx.room.*
import com.vtrainer.app.data.local.entities.ExerciseEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for ExerciseEntity.
 * Provides queries for CRUD operations and exercise library filtering.
 * 
 * Requirements: 12.1 - Room database for local caching with offline-first strategy
 */
@Dao
interface ExerciseDao {
    
    /**
     * Get all exercises from the local cache.
     * Returns a Flow for reactive updates.
     */
    @Query("SELECT * FROM exercises ORDER BY name ASC")
    fun getAllExercises(): Flow<List<ExerciseEntity>>
    
    /**
     * Get a specific exercise by ID.
     */
    @Query("SELECT * FROM exercises WHERE exerciseId = :exerciseId")
    suspend fun getExerciseById(exerciseId: String): ExerciseEntity?
    
    /**
     * Get exercises by muscle group.
     * Searches both primary muscle group and secondary muscles JSON.
     */
    @Query("SELECT * FROM exercises WHERE muscleGroup = :muscleGroup OR secondaryMusclesJson LIKE '%' || :muscleGroup || '%' ORDER BY name ASC")
    suspend fun getExercisesByMuscleGroup(muscleGroup: String): List<ExerciseEntity>
    
    /**
     * Search exercises by name.
     * Case-insensitive search using LIKE operator.
     */
    @Query("SELECT * FROM exercises WHERE name LIKE '%' || :query || '%' ORDER BY name ASC")
    suspend fun searchExercisesByName(query: String): List<ExerciseEntity>
    
    /**
     * Get exercises by difficulty level.
     */
    @Query("SELECT * FROM exercises WHERE difficulty = :difficulty ORDER BY name ASC")
    suspend fun getExercisesByDifficulty(difficulty: String): List<ExerciseEntity>
    
    /**
     * Get exercises by equipment type.
     * Searches the equipment JSON field.
     */
    @Query("SELECT * FROM exercises WHERE equipmentJson LIKE '%' || :equipment || '%' ORDER BY name ASC")
    suspend fun getExercisesByEquipment(equipment: String): List<ExerciseEntity>
    
    /**
     * Get exercises cached before a specific timestamp.
     * Used for cache eviction (LRU strategy).
     */
    @Query("SELECT * FROM exercises WHERE cachedAt < :timestamp ORDER BY cachedAt ASC")
    suspend fun getExercisesCachedBefore(timestamp: Long): List<ExerciseEntity>
    
    /**
     * Insert a new exercise.
     * If an exercise with the same ID exists, it will be replaced.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(exercise: ExerciseEntity)
    
    /**
     * Insert multiple exercises.
     * Used for bulk caching of exercise library.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(exercises: List<ExerciseEntity>)
    
    /**
     * Update an existing exercise.
     */
    @Update
    suspend fun update(exercise: ExerciseEntity)
    
    /**
     * Update the cached timestamp for an exercise.
     * Used to track last access time for LRU cache eviction.
     */
    @Query("UPDATE exercises SET cachedAt = :cachedAt WHERE exerciseId = :exerciseId")
    suspend fun updateCachedAt(exerciseId: String, cachedAt: Long)
    
    /**
     * Delete a specific exercise.
     */
    @Delete
    suspend fun delete(exercise: ExerciseEntity)
    
    /**
     * Delete an exercise by ID.
     */
    @Query("DELETE FROM exercises WHERE exerciseId = :exerciseId")
    suspend fun deleteById(exerciseId: String)
    
    /**
     * Delete all exercises from the cache.
     * Used for cache cleanup operations.
     */
    @Query("DELETE FROM exercises")
    suspend fun deleteAll()
    
    /**
     * Get the count of cached exercises.
     */
    @Query("SELECT COUNT(*) FROM exercises")
    suspend fun getExerciseCount(): Int
    
    /**
     * Get the oldest cached exercise timestamp.
     * Used for cache management.
     */
    @Query("SELECT MIN(cachedAt) FROM exercises")
    suspend fun getOldestCacheTimestamp(): Long?

    /**
     * Get exercises ordered by cachedAt ascending (least recently cached first).
     * Used for LRU eviction: returns the N oldest entries to delete.
     *
     * Requirements: 9.2, 10.2 - Performance optimization via cache size limits
     */
    @Query("SELECT * FROM exercises ORDER BY cachedAt ASC LIMIT :limit")
    suspend fun getOldestExercises(limit: Int): List<ExerciseEntity>

    /**
     * Delete exercises by their IDs.
     * Used for LRU eviction after selecting the oldest entries.
     *
     * Requirements: 9.2, 10.2 - Performance optimization via cache size limits
     */
    @Query("DELETE FROM exercises WHERE exerciseId IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)
}
