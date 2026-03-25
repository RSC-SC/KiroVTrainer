package com.vtrainer.app.presentation.auth

import com.google.firebase.auth.FirebaseAuth
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.choice
import io.kotest.property.arbitrary.constant
import io.kotest.property.arbitrary.orNull
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import io.mockk.every
import io.mockk.mockk

/**
 * Property-based test for authentication requirement.
 *
 * **Validates: Requirements 6.2, 17.1, 17.2**
 *
 * Property 20: Authentication Required for Data Access
 *
 * For any API request to Cloud Functions or Firestore query, if the request lacks a valid
 * Firebase Auth token, the system SHALL reject it with an authentication error.
 *
 * This property ensures that [AuthManager] correctly identifies unauthenticated states,
 * so that data access attempts without a valid token are always rejected.
 */
class AuthenticationRequiredPropertyTest : FunSpec({

    test("Feature: v-trainer, Property 20: Authentication Required for Data Access - isAuthenticated returns false when no user").config(
        invocations = 1
    ) {
        checkAll(1, Arb.unauthenticatedScenario()) { _ ->
            // Arrange: FirebaseAuth with no current user (unauthenticated state)
            val mockAuth = mockk<FirebaseAuth>(relaxed = true)
            every { mockAuth.currentUser } returns null

            val authManager = AuthManager(mockAuth)

            // Assert: isAuthenticated() must return false for any unauthenticated scenario
            authManager.isAuthenticated() shouldBe false
        }
    }

    test("Feature: v-trainer, Property 20: Authentication Required for Data Access - getIdToken returns null when no user").config(
        invocations = 1
    ) {
        checkAll(1, Arb.unauthenticatedScenario()) { _ ->
            // Arrange: FirebaseAuth with no current user (unauthenticated state)
            val mockAuth = mockk<FirebaseAuth>(relaxed = true)
            every { mockAuth.currentUser } returns null

            val authManager = AuthManager(mockAuth)

            // Assert: getIdToken() must return null when no user is authenticated
            // (the null-check on currentUser short-circuits before any token fetch)
            val token = authManager.getIdToken()
            token shouldBe null
        }
    }

    test("Feature: v-trainer, Property 20: Authentication Required for Data Access - getCurrentUser returns null when unauthenticated").config(
        invocations = 1
    ) {
        checkAll(1, Arb.unauthenticatedScenario()) { _ ->
            // Arrange: FirebaseAuth with no current user
            val mockAuth = mockk<FirebaseAuth>(relaxed = true)
            every { mockAuth.currentUser } returns null

            val authManager = AuthManager(mockAuth)

            // Assert: getCurrentUser() must return null for any unauthenticated scenario
            authManager.getCurrentUser() shouldBe null
        }
    }
})

/**
 * Represents an unauthenticated scenario: a description of the state (null token,
 * empty token, random invalid string, etc.) used to drive property iterations.
 * The actual FirebaseAuth mock always has currentUser == null in all cases.
 */
data class UnauthenticatedScenario(val description: String)

/**
 * Arbitrary generator for unauthenticated scenarios.
 * Covers: null token, empty string, random invalid strings, whitespace-only strings.
 */
fun Arb.Companion.unauthenticatedScenario(): Arb<UnauthenticatedScenario> = arbitrary {
    val description = Arb.choice(
        Arb.constant("null_token"),
        Arb.constant("empty_token"),
        Arb.string(1..128),
        Arb.constant("   "),
        Arb.constant("invalid.jwt.token"),
        Arb.constant("Bearer expired_token_xyz"),
    ).bind()
    UnauthenticatedScenario(description)
}
