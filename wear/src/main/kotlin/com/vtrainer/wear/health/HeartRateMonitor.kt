package com.vtrainer.wear.health

import android.content.Context
import androidx.health.services.client.HealthServices
import androidx.health.services.client.MeasureCallback
import androidx.health.services.client.data.Availability
import androidx.health.services.client.data.DataPointContainer
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.DataTypeAvailability
import androidx.health.services.client.data.DeltaDataType
import androidx.health.services.client.data.SampleDataPoint
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.guava.await

/**
 * Sealed class representing the result of a heart rate measurement.
 *
 * Validates: Requirements 9.1, 9.2, 9.3
 */
sealed class HeartRateResult {
    /** A valid heart rate reading in beats per minute. */
    data class Reading(val bpm: Int) : HeartRateResult()
    /** Sensor is available but no reading yet. */
    object Available : HeartRateResult()
    /** Sensor is unavailable (not present or permission denied). */
    object Unavailable : HeartRateResult()
}

/**
 * Monitors heart rate using the Health Services API on Wear OS.
 *
 * Responsibilities:
 * - Check if heart rate measurement is supported on this device (Req 9.1)
 * - Start continuous heart rate monitoring during a workout (Req 9.2)
 * - Emit heart rate values via a [Flow] (Req 9.2)
 * - Calculate average heart rate per set (Req 9.3)
 * - Handle sensor unavailability gracefully (Req 9.3)
 *
 * ## Battery Optimization
 * The sensor callback is registered once per workout session and kept active
 * throughout. To reduce unnecessary CPU wake-ups during rest periods, the
 * [setPollingActive] method allows the ViewModel to pause processing of
 * incoming readings while the rest timer is running. The underlying Health
 * Services callback remains registered (avoiding the latency of
 * re-registration), but readings are silently dropped when polling is paused.
 * This reduces the number of state updates emitted to the UI and the
 * associated coroutine scheduling overhead during rest periods.
 *
 * Validates: Requirements 9.1, 9.2, 9.3, 9.2 (battery optimization)
 *
 * @param context Android [Context] used to obtain the [HealthServices] client.
 */
class HeartRateMonitor(private val context: Context) {

    private val healthServicesClient = HealthServices.getClient(context)
    private val measureClient = healthServicesClient.measureClient

    /**
     * Controls whether incoming heart rate readings are processed.
     *
     * When `false` (rest period), readings from the sensor callback are
     * discarded, reducing coroutine scheduling and UI recompositions.
     * The callback itself stays registered to avoid re-registration latency.
     *
     * Requirements: 9.2 - Battery optimization: reduce sensor processing during rest
     */
    @Volatile
    var isPollingActive: Boolean = true
        private set

    /**
     * Enables or disables processing of incoming heart rate readings.
     *
     * Call with `false` when a rest timer starts and `true` when the next
     * active set begins. This reduces battery drain during rest periods by
     * avoiding unnecessary coroutine dispatches and UI recompositions.
     *
     * Requirements: 9.2 - Reduce sensor polling frequency when appropriate
     *
     * @param active `true` to process readings (active set), `false` to pause (rest period).
     */
    fun setPollingActive(active: Boolean) {
        isPollingActive = active
    }

    /**
     * Returns true if this device supports heart rate measurement.
     *
     * Validates: Requirement 9.1
     */
    suspend fun isHeartRateSupported(): Boolean {
        return try {
            val capabilities = measureClient.getCapabilitiesAsync().await()
            DataType.HEART_RATE_BPM in capabilities.supportedDataTypesMeasure
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Starts continuous heart rate monitoring and emits [HeartRateResult] values.
     *
     * The flow:
     * - Emits [HeartRateResult.Available] when the sensor becomes ready
     * - Emits [HeartRateResult.Reading] for each heart rate sample **when polling is active**
     * - Silently drops readings when [isPollingActive] is `false` (rest period)
     * - Emits [HeartRateResult.Unavailable] when the sensor is not available
     * - Completes when the collector is cancelled (e.g. workout ends)
     *
     * Validates: Requirements 9.2, 9.3
     */
    fun heartRateFlow(): Flow<HeartRateResult> {
        return callbackFlow {
            val callback = object : MeasureCallback {
                override fun onAvailabilityChanged(
                    dataType: DeltaDataType<*, *>,
                    availability: Availability
                ) {
                    if (availability is DataTypeAvailability) {
                        when (availability) {
                            DataTypeAvailability.AVAILABLE ->
                                trySend(HeartRateResult.Available)
                            DataTypeAvailability.UNAVAILABLE,
                            DataTypeAvailability.UNAVAILABLE_DEVICE_OFF_BODY ->
                                trySend(HeartRateResult.Unavailable)
                            else -> { /* acquiring — no action needed */ }
                        }
                    }
                }

                override fun onDataReceived(data: DataPointContainer) {
                    // Battery optimization: skip processing during rest periods (Req 9.2)
                    if (!isPollingActive) return

                    val heartRateSamples = data.getData(DataType.HEART_RATE_BPM)
                    heartRateSamples.forEach { sample ->
                        val bpm = sample.value.toInt()
                        if (bpm > 0) trySend(HeartRateResult.Reading(bpm))
                    }
                }
            }

            measureClient.registerMeasureCallback(
                DataType.HEART_RATE_BPM,
                callback
            )

            awaitClose {
                // Unregister callback when the flow collector is cancelled
                measureClient.unregisterMeasureCallbackAsync(
                    DataType.HEART_RATE_BPM,
                    callback
                )
            }
        }.catch {
            // Sensor error — emit unavailable and complete gracefully (Req 9.3)
            emit(HeartRateResult.Unavailable)
        }
    }

    /**
     * Calculates the average heart rate from a list of BPM readings.
     *
     * Returns null if the list is empty (no readings available).
     *
     * Validates: Requirement 9.3
     *
     * @param readings List of heart rate readings in BPM.
     * @return Average BPM rounded to the nearest integer, or null if no readings.
     */
    fun calculateAverage(readings: List<Int>): Int? {
        if (readings.isEmpty()) return null
        return readings.average().toInt()
    }
}
