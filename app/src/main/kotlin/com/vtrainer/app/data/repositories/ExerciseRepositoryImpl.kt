package com.vtrainer.app.data.repositories

import com.google.firebase.firestore.FirebaseFirestore
import com.vtrainer.app.data.local.dao.ExerciseDao
import com.vtrainer.app.data.mappers.toDomain
import com.vtrainer.app.data.mappers.toEntity
import com.vtrainer.app.domain.models.Difficulty
import com.vtrainer.app.domain.models.Equipment
import com.vtrainer.app.domain.models.Exercise
import com.vtrainer.app.domain.models.MediaType
import com.vtrainer.app.domain.models.MuscleGroup
import com.vtrainer.app.util.VTrainerLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Implementation of ExerciseRepository with offline-first strategy.
 *
 * This repository follows the offline-first pattern:
 * 1. Return data from Room cache immediately via Flow
 * 2. Fetch from Firestore in background and update Room cache
 * 3. If Firestore fetch fails, use cached data
 * 4. Cache exercise data with a cachedAt timestamp
 *
 * Requirements:
 * - 2.1: Maintain exercise library in Firestore
 * - 2.2: Search and filter by muscle group
 * - 2.5: Cache exercise data locally for offline access
 * - 12.1: Room database for local caching
 */
class ExerciseRepositoryImpl(
    private val firestore: FirebaseFirestore,
    private val roomDao: ExerciseDao
) : ExerciseRepository {

    private companion object {
        const val COLLECTION_EXERCISES = "exercises"

        /**
         * Maximum number of exercises to keep in the local Room cache.
         * When exceeded, the oldest entries (by cachedAt) are evicted (LRU strategy).
         *
         * Requirements: 9.2, 10.2 - Cache size limit for performance and battery optimization
         */
        const val MAX_CACHE_SIZE = 500
    }

    /**
     * Get all exercises with offline-first strategy.
     *
     * Flow behavior:
     * 1. Immediately emits cached data from Room
     * 2. Triggers a background Firestore fetch to refresh the cache
     * 3. Room Flow automatically emits updated data after cache refresh
     * 4. If Firestore is unavailable, cached data continues to be served
     *
     * Requirements: 2.1, 2.5, 12.1
     */
    override fun getExercises(): Flow<List<Exercise>> {
        // Trigger background refresh from Firestore
        CoroutineScope(Dispatchers.IO).launch {
            refreshCacheFromFirestore()
        }

        // Return reactive Room Flow (emits immediately and on cache updates)
        return roomDao.getAllExercises()
            .map { entities -> entities.map { it.toDomain() } }
    }

    /**
     * Search exercises by name (case-insensitive) from local cache.
     *
     * Requirements: 2.2
     */
    override suspend fun searchExercises(query: String): List<Exercise> {
        return roomDao.searchExercisesByName(query)
            .map { it.toDomain() }
    }

    /**
     * Filter exercises by muscle group from local cache.
     * Matches both primary muscleGroup and secondaryMuscles.
     *
     * Requirements: 2.2
     */
    override suspend fun filterByMuscleGroup(group: MuscleGroup): List<Exercise> {
        return roomDao.getExercisesByMuscleGroup(group.name)
            .map { it.toDomain() }
    }

    /**
     * Fetch exercises from Firestore and update the Room cache.
     * Silently ignores errors to preserve offline-first behavior.
     *
     * Uses `.orderBy("name")` to leverage the (muscleGroup, name) composite index
     * and avoid full-collection scans.
     *
     * After inserting, enforces the LRU cache size limit by evicting the oldest
     * entries when the cache exceeds MAX_CACHE_SIZE.
     *
     * Requirements: 9.2, 10.2 - Optimized Firestore query + LRU eviction
     */
    private suspend fun refreshCacheFromFirestore() {
        val trace = VTrainerLogger.startTrace("exercise_cache_refresh")
        try {
            val snapshot = firestore.collection(COLLECTION_EXERCISES)
                .orderBy("name") // leverage (muscleGroup, name) composite index
                .get()
                .await()

            val exercises = snapshot.documents.mapNotNull { document ->
                try {
                    document.toExercise()
                } catch (e: Exception) {
                    VTrainerLogger.logNetworkError(
                        context = "ExerciseRepository.parseExercise",
                        errorCode = e.javaClass.simpleName,
                        message = "Error parsing exercise document"
                    )
                    null
                }
            }

            if (exercises.isNotEmpty()) {
                val cachedAt = System.currentTimeMillis()
                val entities = exercises.map { it.toEntity(cachedAt) }
                roomDao.insertAll(entities)
                VTrainerLogger.addTraceMetric(trace, "exercises_cached", exercises.size.toLong())

                // Enforce LRU cache size limit
                evictOldestIfNeeded()
            }
        } catch (e: Exception) {
            // Firestore unavailable — cached data will continue to be served
            VTrainerLogger.logNetworkError(
                context = "ExerciseRepository.refreshCache",
                errorCode = e.javaClass.simpleName,
                message = "Firestore exercise fetch failed"
            )
        } finally {
            VTrainerLogger.stopTrace(trace)
        }
    }

    /**
     * Evicts the oldest cached exercises when the cache exceeds MAX_CACHE_SIZE.
     *
     * Uses LRU (Least Recently Used) strategy: exercises with the smallest
     * cachedAt timestamp are removed first.
     *
     * Requirements: 9.2, 10.2 - LRU eviction for cache size management
     */
    private suspend fun evictOldestIfNeeded() {
        val count = roomDao.getExerciseCount()
        if (count > MAX_CACHE_SIZE) {
            val excess = count - MAX_CACHE_SIZE
            val toEvict = roomDao.getOldestExercises(excess)
            if (toEvict.isNotEmpty()) {
                roomDao.deleteByIds(toEvict.map { it.exerciseId })
                VTrainerLogger.d(message = "LRU eviction: removed $excess exercises from cache (limit=$MAX_CACHE_SIZE)")
            }
        }
    }
}

/**
 * Extension function to convert a Firestore document to an Exercise domain model.
 */
private fun com.google.firebase.firestore.DocumentSnapshot.toExercise(): Exercise {
    val data = this.data ?: throw IllegalArgumentException("Document data is null for ${this.id}")

    @Suppress("UNCHECKED_CAST")
    val secondaryMuscleNames = (data["secondaryMuscles"] as? List<String>) ?: emptyList()

    @Suppress("UNCHECKED_CAST")
    val equipmentNames = (data["equipment"] as? List<String>) ?: emptyList()

    return Exercise(
        exerciseId = this.id,
        name = data["name"] as? String ?: "",
        muscleGroup = MuscleGroup.valueOf(
            ((data["muscleGroup"] as? String) ?: "").uppercase()
        ),
        secondaryMuscles = secondaryMuscleNames.mapNotNull { name ->
            runCatching { MuscleGroup.valueOf(name.uppercase()) }.getOrNull()
        },
        instructions = data["instructions"] as? String ?: "",
        mediaUrl = data["mediaUrl"] as? String ?: "",
        mediaType = MediaType.valueOf(
            ((data["mediaType"] as? String) ?: "GIF").uppercase()
        ),
        difficulty = Difficulty.valueOf(
            ((data["difficulty"] as? String) ?: "BEGINNER").uppercase()
        ),
        equipment = equipmentNames.mapNotNull { name ->
            runCatching { Equipment.valueOf(name.uppercase()) }.getOrNull()
        }
    )
}
