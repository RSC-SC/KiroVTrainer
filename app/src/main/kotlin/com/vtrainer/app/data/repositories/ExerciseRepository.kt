package com.vtrainer.app.data.repositories

import com.vtrainer.app.domain.models.Exercise
import com.vtrainer.app.domain.models.MuscleGroup
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for exercise library operations.
 * Implements offline-first strategy with local Room cache and Firestore fallback.
 *
 * Requirements:
 * - 2.1: Maintain exercise library in Firestore
 * - 2.2: Display exercise library with search and filter by muscle group
 * - 2.5: Cache exercise data locally for offline access
 * - 12.1: Room database for local caching
 */
interface ExerciseRepository {

    /**
     * Get all exercises from the library.
     * Returns a Flow that emits cached data from Room immediately,
     * then refreshes from Firestore in the background.
     *
     * Strategy:
     * 1. Emit cached data from Room immediately
     * 2. Fetch from Firestore in background and update Room cache
     * 3. If Firestore fetch fails, continue serving cached data
     *
     * Requirements: 2.1, 2.5, 12.1
     *
     * @return Flow of exercise lists
     */
    fun getExercises(): Flow<List<Exercise>>

    /**
     * Search exercises by name (case-insensitive).
     * Searches the local Room cache.
     *
     * Requirements: 2.2
     *
     * @param query The search query string
     * @return List of exercises matching the query
     */
    suspend fun searchExercises(query: String): List<Exercise>

    /**
     * Filter exercises by muscle group.
     * Matches both primary muscleGroup and secondaryMuscles.
     *
     * Requirements: 2.2
     *
     * @param group The muscle group to filter by
     * @return List of exercises targeting the given muscle group
     */
    suspend fun filterByMuscleGroup(group: MuscleGroup): List<Exercise>
}
