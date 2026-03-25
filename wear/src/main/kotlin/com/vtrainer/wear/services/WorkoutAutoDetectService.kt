package com.vtrainer.wear.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.vtrainer.wear.MainActivity
import kotlin.math.sqrt

/**
 * Foreground service that monitors the accelerometer for repetitive movement
 * patterns indicative of strength training.
 *
 * Detection logic:
 * - Samples accelerometer at NORMAL rate
 * - Counts peaks above [MOVEMENT_THRESHOLD] within a sliding window
 * - If [MIN_PEAKS_IN_WINDOW] peaks detected over [DETECTION_WINDOW_MS], triggers notification
 * - Rate-limited to max 1 notification per [RATE_LIMIT_MS] (Req 15.4)
 * - Respects enable/disable preference (Req 15.5)
 *
 * Validates: Requirements 15.1, 15.2, 15.3, 15.4, 15.5
 */
class WorkoutAutoDetectService : Service(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null

    /** Timestamps (ms) of recent movement peaks within the detection window. */
    private val peakTimestamps = mutableListOf<Long>()

    /** Timestamp of the last notification sent — used for rate limiting (Req 15.4). */
    private var lastNotificationTimeMs: Long = 0L

    /** Whether auto-detect is enabled (Req 15.5). */
    private var isEnabled: Boolean = true

    // -------------------------------------------------------------------------
    // Service lifecycle
    // -------------------------------------------------------------------------

    override fun onCreate() {
        super.onCreate()
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        isEnabled = intent?.getBooleanExtra(EXTRA_ENABLED, true) ?: true

        if (isEnabled) {
            accelerometer?.let {
                sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
            }
        } else {
            stopSelf()
        }

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        sensorManager.unregisterListener(this)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // -------------------------------------------------------------------------
    // Accelerometer processing (Req 15.1, 15.2)
    // -------------------------------------------------------------------------

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return

        val magnitude = calculateMagnitude(event.values[0], event.values[1], event.values[2])
        val nowMs = System.currentTimeMillis()

        if (magnitude > MOVEMENT_THRESHOLD) {
            peakTimestamps.add(nowMs)
        }

        // Remove peaks outside the detection window
        peakTimestamps.removeAll { nowMs - it > DETECTION_WINDOW_MS }

        if (peakTimestamps.size >= MIN_PEAKS_IN_WINDOW) {
            maybeNotify(nowMs)
            peakTimestamps.clear()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    // -------------------------------------------------------------------------
    // Notification (Req 15.3, 15.4)
    // -------------------------------------------------------------------------

    /**
     * Sends a workout detection notification if rate limit allows (Req 15.4).
     */
    private fun maybeNotify(nowMs: Long) {
        if (nowMs - lastNotificationTimeMs < RATE_LIMIT_MS) return
        lastNotificationTimeMs = nowMs
        sendWorkoutDetectedNotification()
    }

    private fun sendWorkoutDetectedNotification() {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("Workout detected")
            .setContentText("Tap to start tracking your workout")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Workout Auto-Detect",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifies when workout movement is detected"
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun calculateMagnitude(x: Float, y: Float, z: Float): Double =
        sqrt((x * x + y * y + z * z).toDouble())

    companion object {
        const val EXTRA_ENABLED = "extra_enabled"

        /** Accelerometer magnitude threshold to count as a movement peak (m/s²). */
        const val MOVEMENT_THRESHOLD = 12.0

        /** Sliding window duration for peak counting (Req 15.2 — 30 seconds). */
        const val DETECTION_WINDOW_MS = 30_000L

        /** Minimum peaks within the window to trigger detection (Req 15.2). */
        const val MIN_PEAKS_IN_WINDOW = 10

        /** Rate limit: max 1 notification per hour (Req 15.4). */
        const val RATE_LIMIT_MS = 60 * 60 * 1_000L

        private const val CHANNEL_ID = "workout_auto_detect"
        private const val NOTIFICATION_ID = 1001
    }
}
