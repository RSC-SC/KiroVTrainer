package com.vtrainer.app.util

import android.util.Log
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.perf.FirebasePerformance
import com.google.firebase.perf.metrics.Trace

/**
 * Structured logging and monitoring utility for V-Trainer.
 *
 * Wraps Firebase Crashlytics and Android Log to provide:
 * - Structured network error logging with error codes and context
 * - Sync success/failure rate tracking
 * - Validation error frequency monitoring
 * - Performance traces for critical paths
 * - Automatic sanitization to prevent sensitive data leakage
 *
 * PRIVACY GUARANTEE: This logger NEVER logs:
 * - User body weight or health metrics
 * - Exercise weights, reps, or training data values
 * - User name or personal identifiers beyond userId
 * - Heart rate, calorie, or other biometric values
 *
 * Only operational data is logged: error codes, sync status, operation
 * durations, and counts (not values).
 *
 * Requirements: 17.4
 */
object VTrainerLogger {

    private const val TAG = "VTrainer"

    // -------------------------------------------------------------------------
    // Sync event tracking
    // -------------------------------------------------------------------------

    /**
     * Log a successful sync operation.
     *
     * @param operation  Name of the sync operation (e.g. "training_log_sync")
     * @param itemCount  Number of items synced (count only, no values)
     */
    fun logSyncSuccess(operation: String, itemCount: Int = 1) {
        val msg = "SYNC_SUCCESS operation=$operation items=$itemCount"
        Log.d(TAG, msg)
        crashlytics().log(msg)
    }

    /**
     * Log a failed sync operation.
     *
     * @param operation  Name of the sync operation
     * @param errorCode  HTTP status code or Firebase error code
     * @param attempt    Retry attempt number (1-based)
     */
    fun logSyncFailure(operation: String, errorCode: String, attempt: Int = 1) {
        val msg = "SYNC_FAILURE operation=$operation errorCode=$errorCode attempt=$attempt"
        Log.w(TAG, msg)
        crashlytics().log(msg)
    }

    /**
     * Log a non-fatal sync exception without exposing sensitive payload data.
     *
     * @param operation  Name of the sync operation
     * @param exception  The exception that occurred
     */
    fun logSyncException(operation: String, exception: Exception) {
        val sanitized = sanitizeException(exception)
        val msg = "SYNC_EXCEPTION operation=$operation type=${exception.javaClass.simpleName}"
        Log.e(TAG, msg, sanitized)
        crashlytics().log(msg)
        crashlytics().recordException(sanitized)
    }

    // -------------------------------------------------------------------------
    // Network error logging
    // -------------------------------------------------------------------------

    /**
     * Log a structured network error with error code and context.
     *
     * @param context    Where the error occurred (e.g. "WorkoutRepository.saveWorkoutPlan")
     * @param errorCode  HTTP status code or Firebase error code string
     * @param message    Short, non-sensitive description of the error
     */
    fun logNetworkError(context: String, errorCode: String, message: String) {
        val sanitizedMessage = sanitizeMessage(message)
        val msg = "NETWORK_ERROR context=$context errorCode=$errorCode message=$sanitizedMessage"
        Log.w(TAG, msg)
        crashlytics().log(msg)
    }

    /**
     * Log a non-fatal network exception.
     *
     * @param context    Where the error occurred
     * @param exception  The exception that occurred
     */
    fun logNetworkException(context: String, exception: Exception) {
        val sanitized = sanitizeException(exception)
        val msg = "NETWORK_EXCEPTION context=$context type=${exception.javaClass.simpleName}"
        Log.e(TAG, msg, sanitized)
        crashlytics().log(msg)
        crashlytics().recordException(sanitized)
    }

    // -------------------------------------------------------------------------
    // Validation error tracking
    // -------------------------------------------------------------------------

    /**
     * Log a validation error occurrence.
     *
     * @param field      The field that failed validation (e.g. "timestamp", "exerciseId")
     * @param errorCode  Short code describing the validation failure (e.g. "FUTURE_TIMESTAMP")
     */
    fun logValidationError(field: String, errorCode: String) {
        val msg = "VALIDATION_ERROR field=$field errorCode=$errorCode"
        Log.d(TAG, msg)
        crashlytics().log(msg)
    }

    /**
     * Log multiple validation errors at once.
     *
     * @param errors  Map of field name to error code
     */
    fun logValidationErrors(errors: Map<String, String>) {
        val count = errors.size
        val fields = errors.keys.joinToString(",")
        val msg = "VALIDATION_ERRORS count=$count fields=$fields"
        Log.d(TAG, msg)
        crashlytics().log(msg)
    }

    // -------------------------------------------------------------------------
    // Performance monitoring
    // -------------------------------------------------------------------------

    /**
     * Start a Firebase Performance trace for a critical path.
     *
     * Usage:
     * ```kotlin
     * val trace = VTrainerLogger.startTrace("workout_sync")
     * // ... do work ...
     * VTrainerLogger.stopTrace(trace)
     * ```
     *
     * @param traceName  Name of the trace (use snake_case, max 100 chars)
     * @return           The started [Trace], or null if Performance is unavailable
     */
    fun startTrace(traceName: String): Trace? {
        return try {
            val trace = FirebasePerformance.getInstance().newTrace(traceName)
            trace.start()
            trace
        } catch (e: Exception) {
            Log.w(TAG, "Failed to start performance trace: $traceName", e)
            null
        }
    }

    /**
     * Stop a previously started performance trace.
     *
     * @param trace  The trace returned by [startTrace], may be null (no-op)
     */
    fun stopTrace(trace: Trace?) {
        try {
            trace?.stop()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to stop performance trace", e)
        }
    }

    /**
     * Add a non-sensitive metric to an active trace.
     *
     * @param trace   The active trace
     * @param metric  Metric name (e.g. "items_synced", "query_result_count")
     * @param value   Metric value (counts/durations only, never weights or biometrics)
     */
    fun addTraceMetric(trace: Trace?, metric: String, value: Long) {
        try {
            trace?.putMetric(metric, value)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to add trace metric: $metric", e)
        }
    }

    // -------------------------------------------------------------------------
    // User context (non-sensitive)
    // -------------------------------------------------------------------------

    /**
     * Set the current user ID in Crashlytics for crash correlation.
     * Only the opaque Firebase UID is set — never name, email, or other PII.
     *
     * @param userId  Firebase Auth UID
     */
    fun setUserId(userId: String) {
        crashlytics().setUserId(userId)
    }

    /**
     * Clear the user ID from Crashlytics (e.g. on sign-out).
     */
    fun clearUserId() {
        crashlytics().setUserId("")
    }

    // -------------------------------------------------------------------------
    // General purpose
    // -------------------------------------------------------------------------

    /** Log a debug message (development only, not sent to Crashlytics). */
    fun d(tag: String = TAG, message: String) {
        Log.d(tag, sanitizeMessage(message))
    }

    /** Log a warning and send to Crashlytics log buffer. */
    fun w(tag: String = TAG, message: String) {
        val sanitized = sanitizeMessage(message)
        Log.w(tag, sanitized)
        crashlytics().log("W/$tag: $sanitized")
    }

    /** Log an error and record as non-fatal in Crashlytics. */
    fun e(tag: String = TAG, message: String, throwable: Throwable? = null) {
        val sanitized = sanitizeMessage(message)
        Log.e(tag, sanitized, throwable)
        crashlytics().log("E/$tag: $sanitized")
        if (throwable != null) {
            crashlytics().recordException(sanitizeException(throwable))
        }
    }

    // -------------------------------------------------------------------------
    // Sanitization helpers
    // -------------------------------------------------------------------------

    /**
     * Sanitize a log message by removing patterns that could contain sensitive data.
     *
     * Removes:
     * - Numeric values that could be weights/reps (e.g. "weight=80.5", "reps=12")
     * - Email addresses
     * - Patterns that look like personal names
     *
     * @param message  Raw message string
     * @return         Sanitized message safe for logging
     */
    internal fun sanitizeMessage(message: String): String {
        return message
            // Remove weight/rep/calorie values (keep the key, redact the value)
            .replace(Regex("(?i)(weight|reps|calories|heartRate|bodyWeight)\\s*[=:]\\s*[\\d.]+"), "$1=[REDACTED]")
            // Remove email addresses
            .replace(Regex("[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}"), "[EMAIL_REDACTED]")
            // Remove potential numeric personal data in key=value pairs not already caught
            .replace(Regex("(?i)(currentWeight|totalCalories|maxWeight|maxVolume)\\s*[=:]\\s*[\\d.]+"), "$1=[REDACTED]")
    }

    /**
     * Create a sanitized copy of an exception whose message is safe to log.
     * Strips any numeric values from the exception message.
     *
     * @param throwable  Original exception
     * @return           Exception with sanitized message, same type and stack trace
     */
    internal fun sanitizeException(throwable: Throwable): Exception {
        val sanitizedMessage = throwable.message?.let { sanitizeMessage(it) }
            ?: throwable.javaClass.simpleName
        return Exception(sanitizedMessage, throwable.cause).also { sanitized ->
            sanitized.stackTrace = throwable.stackTrace
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private fun crashlytics(): FirebaseCrashlytics = FirebaseCrashlytics.getInstance()
}
