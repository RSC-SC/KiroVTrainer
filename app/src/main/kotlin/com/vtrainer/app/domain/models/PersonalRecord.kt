package com.vtrainer.app.domain.models

import kotlinx.datetime.Instant

/**
 * Domain model representing a personal record achievement.
 */
data class PersonalRecord(
    val exerciseId: String,
    val maxWeight: Double,
    val maxVolume: Int,
    val achievedAt: Instant
)
