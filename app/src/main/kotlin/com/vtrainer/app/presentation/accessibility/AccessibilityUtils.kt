package com.vtrainer.app.presentation.accessibility

import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

/**
 * Accessibility utilities for the V-Trainer app.
 *
 * Validates: Requirements 18.1, 18.2, 18.4, 18.5
 *
 * Guidelines:
 * - All interactive elements must have a minimum touch target of 56dp (mobile) / 48dp (watch)
 * - All non-decorative icons must have a contentDescription
 * - Decorative icons (alongside text) should use contentDescription = null
 * - Cards and complex composables should use semantics { contentDescription = "..." }
 *   when the visual content is not self-describing for screen readers
 */
object AccessibilityUtils {

    /**
     * Minimum touch target size for mobile (Req 18.4).
     */
    const val MIN_TOUCH_TARGET_DP = 56

    /**
     * Minimum touch target size for Wear OS (Req 18.3).
     */
    const val MIN_TOUCH_TARGET_WATCH_DP = 48
}

/**
 * Applies a semantic content description to a composable for screen reader support.
 *
 * Use this on cards and complex composables where the visual content is not
 * self-describing for TalkBack / accessibility services.
 *
 * Validates: Requirements 18.1, 18.5
 *
 * @param description Human-readable description of the composable's content.
 */
fun Modifier.accessibilityDescription(description: String): Modifier =
    this.semantics { contentDescription = description }
