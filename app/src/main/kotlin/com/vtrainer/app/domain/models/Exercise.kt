package com.vtrainer.app.domain.models

/**
 * Domain model representing an exercise in the library.
 */
data class Exercise(
    val exerciseId: String,
    val name: String,
    val muscleGroup: MuscleGroup,
    val secondaryMuscles: List<MuscleGroup>,
    val instructions: String,
    val mediaUrl: String,
    val mediaType: MediaType,
    val difficulty: Difficulty,
    val equipment: List<Equipment>
)
