package com.vtrainer.app.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity for caching exercise library data locally.
 * Enables offline access to exercise information.
 */
@Entity(tableName = "exercises")
data class ExerciseEntity(
    @PrimaryKey
    val exerciseId: String,
    val name: String,
    val muscleGroup: String,
    val secondaryMusclesJson: String, // JSON serialized list of muscle groups
    val instructions: String,
    val mediaUrl: String,
    val mediaType: String,
    val difficulty: String,
    val equipmentJson: String, // JSON serialized list of equipment
    val cachedAt: Long
)
