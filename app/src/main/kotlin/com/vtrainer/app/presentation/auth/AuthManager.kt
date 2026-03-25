package com.vtrainer.app.presentation.auth

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.vtrainer.app.util.VTrainerLogger
import kotlinx.coroutines.tasks.await

/**
 * Helper class that wraps FirebaseAuth and provides convenience methods
 * for checking authentication state and retrieving tokens for API calls.
 *
 * Validates: Requirements 17.1 (require authentication before accessing user data)
 */
class AuthManager(
    private val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance()
) {

    /**
     * Returns the currently authenticated Firebase user, or null if not authenticated.
     */
    fun getCurrentUser(): FirebaseUser? = firebaseAuth.currentUser

    /**
     * Returns true if a user is currently authenticated.
     */
    fun isAuthenticated(): Boolean = firebaseAuth.currentUser != null

    /**
     * Retrieves a fresh Firebase ID token for the current user.
     * This token can be used to authenticate API calls to Cloud Functions.
     *
     * @return The ID token string, or null if the user is not authenticated or token retrieval fails.
     */
    suspend fun getIdToken(): String? {
        val user = firebaseAuth.currentUser ?: return null
        return try {
            user.getIdToken(/* forceRefresh = */ true).await().token
        } catch (e: Exception) {
            VTrainerLogger.logNetworkError(
                context = "AuthManager.getIdToken",
                errorCode = e.javaClass.simpleName,
                message = "Failed to retrieve ID token"
            )
            null
        }
    }

    /**
     * Set the current user ID in Crashlytics for crash correlation.
     * Only the opaque Firebase UID is set — never name, email, or other PII.
     */
    fun onUserSignedIn() {
        firebaseAuth.currentUser?.uid?.let { uid ->
            VTrainerLogger.setUserId(uid)
        }
    }

    /**
     * Clear the user ID from Crashlytics on sign-out.
     */
    fun onUserSignedOut() {
        VTrainerLogger.clearUserId()
    }
}
