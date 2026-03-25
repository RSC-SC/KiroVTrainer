package com.vtrainer.app

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.vtrainer.app.presentation.auth.AuthViewModel
import com.vtrainer.app.presentation.navigation.AppNavigation
import com.vtrainer.app.services.NotificationHelper
import com.vtrainer.app.services.NotificationPermissionManager

class MainActivity : ComponentActivity() {

    private lateinit var notificationPermissionManager: NotificationPermissionManager
    private lateinit var googleSignInClient: GoogleSignInClient
    private val authViewModel: AuthViewModel by viewModels()

    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            val idToken = account.idToken
            if (idToken != null) {
                authViewModel.signInWithGoogle(idToken)
            } else {
                Log.e("MainActivity", "Google Sign-In: idToken is null")
                authViewModel.onGoogleSignInError("Token de autenticação não recebido. Verifique a configuração do Firebase.")
            }
        } catch (e: ApiException) {
            // DEVELOPER_ERROR (Status 10) indicates a configuration issue
            Log.e("MainActivity", "Google Sign-In failed: statusCode=${e.statusCode}", e)
            val errorMessage = when (e.statusCode) {
                10 -> "Erro de Configuração (Status 10). Verifique o SHA-1 e o E-mail de suporte no Console do Firebase."
                7 -> "Erro de Rede. Verifique sua conexão."
                else -> "Falha no login com Google (código ${e.statusCode})"
            }
            authViewModel.onGoogleSignInError(errorMessage)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Ensure Firebase is initialized
        FirebaseApp.initializeApp(this)
        
        // FORCE SIGNOUT to clear potentially corrupted tokens causing DEVELOPER_ERROR
        // This is safe to remove after the first successful login
        // FirebaseAuth.getInstance().signOut()

        FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(!BuildConfig.DEBUG)
        
        NotificationHelper.createChannels(this)
        notificationPermissionManager = NotificationPermissionManager(this)
        notificationPermissionManager.requestIfNeeded()

        // Configure Google Sign-In
        val webClientId = getString(R.string.default_web_client_id)
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(webClientId)
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)

        setContent {
            VTrainerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation(
                        authViewModel = authViewModel,
                        onSignInWithGoogle = {
                            // Sign out of Google to force account selection
                            googleSignInClient.signOut().addOnCompleteListener {
                                googleSignInLauncher.launch(googleSignInClient.signInIntent)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun VTrainerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        content = content
    )
}
