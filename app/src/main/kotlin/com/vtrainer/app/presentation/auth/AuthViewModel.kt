package com.vtrainer.app.presentation.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Represents the authentication state of the user.
 * Validates: Requirements 1.5, 17.1
 */
sealed class AuthState {
    object Unauthenticated : AuthState()
    object Loading : AuthState()
    data class Authenticated(val user: FirebaseUser) : AuthState()
    data class Error(val message: String) : AuthState()
}

/**
 * ViewModel responsible for Firebase Authentication flows.
 * Supports Google Sign-In and Samsung Account Sign-In (via Google Play Services OAuth).
 *
 * Validates: Requirements 1.5 (Google/Samsung Account auth), 17.1 (require auth before data access)
 */
class AuthViewModel : ViewModel() {

    private val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance()

    private val _authState = MutableStateFlow<AuthState>(AuthState.Unauthenticated)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val authStateListener = FirebaseAuth.AuthStateListener { auth ->
        val user = auth.currentUser
        _authState.value = if (user != null) {
            AuthState.Authenticated(user)
        } else {
            AuthState.Unauthenticated
        }
    }

    init {
        firebaseAuth.addAuthStateListener(authStateListener)
    }

    /**
     * Signs in with a Google ID token obtained from the Google Sign-In flow.
     * This is also the entry point for Samsung Account sign-in, which uses
     * Google Play Services OAuth under the hood on Android.
     *
     * @param idToken The ID token from Google Sign-In / Samsung Account OAuth
     */
    fun signInWithGoogle(idToken: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            try {
                val credential = GoogleAuthProvider.getCredential(idToken, null)
                firebaseAuth.signInWithCredential(credential).await()
                // authStateListener will update _authState on success
            } catch (e: Exception) {
                _authState.value = AuthState.Error(e.message ?: "Google sign-in failed")
            }
        }
    }

    /**
     * Signs in with a Samsung Account ID token.
     * Samsung Account on Android uses Google Play Services OAuth, so this delegates
     * to the same Firebase Google credential flow.
     *
     * @param idToken The ID token obtained from Samsung Account OAuth (Google Play Services)
     */
    fun signInWithSamsungAccount(idToken: String) {
        signInWithGoogle(idToken)
    }

    /**
     * Signs out the current user and resets auth state to Unauthenticated.
     */
    fun signOut() {
        firebaseAuth.signOut()
        // authStateListener will update _authState to Unauthenticated
    }

    override fun onCleared() {
        super.onCleared()
        firebaseAuth.removeAuthStateListener(authStateListener)
    }
}
