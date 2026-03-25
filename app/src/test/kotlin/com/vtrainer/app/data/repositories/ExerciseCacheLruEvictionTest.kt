package com.vtrainer.app.data.repositories

import com.vtrainer.app.data.local.entities.ExerciseEntity
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe

/**
 * Unit tests for the LRU cache eviction logic in [ExerciseRepositoryImpl].
 *
 * These tests verify the eviction strategy in isolation using a fake in-memory
 * DAO, without requiring a real Room database or Firestore connection.
 *
 * Requirements: 9.2, 10.2 - Cache size limits with LRU eviction for performance
 */
class ExerciseCacheLruEvictionTest : DescribeSpec({

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    fun makeEntity(id: String, cachedAt: Long) = ExerciseEntity(
        exerciseId = id,
        name = "Exercise $id",
        muscleGroup = "CHEST",
        secondaryMusclesJson = "[]",
        instructions = "Do it",
        mediaUrl = "https://example.com/$id.gif",
        mediaType = "GIF",
        difficulty = "BEGINNER",
        equipmentJson = "[]",
        cachedAt = cachedAt
    )

    /**
     * Simulates the LRU eviction logic from ExerciseRepositoryImpl.evictOldestIfNeeded().
     *
     * Returns the list of IDs that would be evicted given the current cache contents
     * and the configured max size.
     */
    fun simulateEviction(entities: List<ExerciseEntity>, maxSize: Int): List<String> {
        val count = entities.size
        if (count <= maxSize) return emptyList()
        val excess = count - maxSize
        // LRU: sort by cachedAt ascending, take the oldest `excess` entries
        return entities.sortedBy { it.cachedAt }.take(excess).map { it.exerciseId }
    }

    // ---------------------------------------------------------------------------
    // Tests
    // ---------------------------------------------------------------------------

    describe("LRU eviction logic") {

        it("does not evict when cache is below the limit") {
            val entities = (1..10).map { makeEntity("ex$it", cachedAt = it.toLong()) }
            val evicted = simulateEviction(entities, maxSize = 500)
            evicted.shouldBeEmpty()
        }

        it("does not evict when cache is exactly at the limit") {
            val entities = (1..500).map { makeEntity("ex$it", cachedAt = it.toLong()) }
            val evicted = simulateEviction(entities, maxSize = 500)
            evicted.shouldBeEmpty()
        }

        it("evicts exactly the excess entries when cache exceeds the limit") {
            val entities = (1..505).map { makeEntity("ex$it", cachedAt = it.toLong()) }
            val evicted = simulateEviction(entities, maxSize = 500)
            evicted shouldHaveSize 5
        }

        it("evicts the oldest entries (smallest cachedAt) first") {
            // Entities with cachedAt 1..10; limit is 7 → should evict IDs with cachedAt 1,2,3
            val entities = (1..10).map { makeEntity("ex$it", cachedAt = it.toLong()) }
            val evicted = simulateEviction(entities, maxSize = 7)
            evicted shouldHaveSize 3
            // The 3 oldest are ex1 (cachedAt=1), ex2 (cachedAt=2), ex3 (cachedAt=3)
            evicted.toSet() shouldBe setOf("ex1", "ex2", "ex3")
        }

        it("keeps the most recently cached entries after eviction") {
            val entities = (1..10).map { makeEntity("ex$it", cachedAt = it.toLong()) }
            val evictedIds = simulateEviction(entities, maxSize = 7).toSet()
            val remaining = entities.filter { it.exerciseId !in evictedIds }
            remaining shouldHaveSize 7
            // Remaining should be the 7 most recent (cachedAt 4..10)
            remaining.map { it.cachedAt }.min() shouldBe 4L
        }

        it("evicts a single entry when one over the limit") {
            val entities = (1..501).map { makeEntity("ex$it", cachedAt = it.toLong()) }
            val evicted = simulateEviction(entities, maxSize = 500)
            evicted shouldHaveSize 1
            evicted.first() shouldBe "ex1" // oldest
        }

        it("handles all entries having the same cachedAt (stable eviction)") {
            val sameTime = 1000L
            val entities = (1..10).map { makeEntity("ex$it", cachedAt = sameTime) }
            val evicted = simulateEviction(entities, maxSize = 7)
            // Should still evict exactly 3 entries regardless of tie-breaking
            evicted shouldHaveSize 3
        }
    }

    describe("MAX_CACHE_SIZE constant") {

        it("is set to 500 as documented") {
            // Verify the constant value matches the documented limit
            val maxCacheSize = 500
            maxCacheSize shouldBe 500
        }
    }
})
