package com.vtrainer.wear.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.vtrainer.wear.MainActivity

class VTrainerWearMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d(TAG, "From: ${remoteMessage.from}")

        WearNotificationHelper.createChannels(this)

        if (remoteMessage.data.isNotEmpty()) {
            Log.d(TAG, "Message data payload: ${remoteMessage.data}")
            when (remoteMessage.data["type"]) {
                "personal_record" -> showPersonalRecordNotification(remoteMessage.data)
                "workout_reminder" -> showWorkoutReminderNotification(remoteMessage.data)
            }
        } else {
            remoteMessage.notification?.let {
                Log.d(TAG, "Message Notification Body: ${it.body}")
            }
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "Refreshed token: $token")
        sendRegistrationToServer(token)
    }

    private fun sendRegistrationToServer(token: String) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        FirebaseFirestore.getInstance()
            .collection("users")
            .document(userId)
            .collection("fcmTokens")
            .document("wear")
            .set(mapOf("token" to token, "updatedAt" to System.currentTimeMillis()))
            .addOnSuccessListener { Log.d(TAG, "Wear FCM token saved to Firestore") }
            .addOnFailureListener { e -> Log.e(TAG, "Failed to save wear FCM token", e) }
    }

    private fun showPersonalRecordNotification(data: Map<String, String>) {
        val exerciseName = data["exerciseName"] ?: "Exercise"
        val recordType = data["recordType"] ?: ""
        val newValue = data["newValue"] ?: ""
        val body = buildString {
            append(exerciseName)
            if (recordType.isNotEmpty()) append(" — $recordType")
            if (newValue.isNotEmpty()) append(": $newValue")
        }

        val notification = NotificationCompat.Builder(this, WearNotificationHelper.CHANNEL_PERSONAL_RECORDS)
            .setSmallIcon(android.R.drawable.star_on)
            .setContentTitle("Personal Record!")
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .extend(
                NotificationCompat.WearableExtender()
                    .setHintShowBackgroundOnly(false)
            )
            .build()

        NotificationManagerCompat.from(this).notify(NOTIF_ID_PR, notification)
    }

    private fun showWorkoutReminderNotification(data: Map<String, String>) {
        val workoutName = data["workoutName"] ?: "Your workout"

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val startAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_media_play,
            "Start Workout",
            pendingIntent
        ).build()

        val notification = NotificationCompat.Builder(this, WearNotificationHelper.CHANNEL_WORKOUT_REMINDERS)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Workout Reminder")
            .setContentText(workoutName)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .extend(
                NotificationCompat.WearableExtender()
                    .addAction(startAction)
            )
            .build()

        NotificationManagerCompat.from(this).notify(NOTIF_ID_REMINDER, notification)
    }

    companion object {
        private const val TAG = "VTrainerWearMsg"
        private const val NOTIF_ID_PR = 2001
        private const val NOTIF_ID_REMINDER = 2002
    }
}

object WearNotificationHelper {
    const val CHANNEL_PERSONAL_RECORDS = "personal_records"
    const val CHANNEL_WORKOUT_REMINDERS = "workout_reminders"

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_PERSONAL_RECORDS,
                    "Personal Records",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply { description = "Notifications for new personal records" }
            )
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_WORKOUT_REMINDERS,
                    "Workout Reminders",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply { description = "Scheduled workout reminder notifications" }
            )
        }
    }
}
