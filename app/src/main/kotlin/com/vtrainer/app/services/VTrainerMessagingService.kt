package com.vtrainer.app.services

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import android.util.Log

class VTrainerMessagingService : FirebaseMessagingService() {
    
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        
        Log.d(TAG, "From: ${remoteMessage.from}")
        
        // Handle notification payload
        remoteMessage.notification?.let {
            Log.d(TAG, "Message Notification Body: ${it.body}")
            // TODO: Show notification
        }
        
        // Handle data payload
        if (remoteMessage.data.isNotEmpty()) {
            Log.d(TAG, "Message data payload: ${remoteMessage.data}")
            
            when (remoteMessage.data["type"]) {
                "personal_record" -> {
                    // TODO: Handle personal record notification
                }
                "workout_reminder" -> {
                    // TODO: Handle workout reminder
                }
            }
        }
    }
    
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "Refreshed token: $token")
        
        // TODO: Send token to Firestore
        sendRegistrationToServer(token)
    }
    
    private fun sendRegistrationToServer(token: String) {
        // TODO: Implement token registration
    }
    
    companion object {
        private const val TAG = "VTrainerMessaging"
    }
}
