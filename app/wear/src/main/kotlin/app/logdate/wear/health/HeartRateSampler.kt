package app.logdate.wear.health

import androidx.health.services.client.MeasureCallback
import androidx.health.services.client.MeasureClient
import androidx.health.services.client.data.Availability
import androidx.health.services.client.data.DataPointContainer
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.DataTypeAvailability
import androidx.health.services.client.data.DeltaDataType
import androidx.health.services.client.getCapabilities
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.roundToInt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** One heart rate reading, for a mark. */
fun interface HeartRateSampler {
    /** Beats per minute, or null when none can be read in time. */
    suspend fun sample(): Int?

    companion object {
        /** For a watch without Health Services. */
        val None = HeartRateSampler { null }
    }
}

/** What the heart rate sensor reports while it is read. */
sealed interface HeartRateReading {
    data class Bpm(
        val value: Int,
    ) : HeartRateReading

    /** The sensor can't read now: the watch is off the wrist, or the sensor is unavailable. */
    data object Unavailable : HeartRateReading
}

/** Live readings from the sensor, for as long as they are collected. */
fun interface HeartRateReadings {
    fun readings(): Flow<HeartRateReading>
}

/**
 * Reads the heart rate once: the first plausible reading within [timeout], or null when the
 * permission is missing, the sensor reports it can't read, or nothing arrives in time.
 */
class MeasuredHeartRateSampler(
    private val readings: HeartRateReadings,
    private val hasPermission: () -> Boolean,
    private val timeout: Duration = DEFAULT_TIMEOUT,
) : HeartRateSampler {
    override suspend fun sample(): Int? {
        if (!hasPermission()) return null
        return try {
            withTimeoutOrNull(timeout) { firstReading() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Napier.w("Couldn't read the heart rate", e)
            null
        }
    }

    private suspend fun firstReading(): Int? {
        val reading =
            readings.readings().first { candidate ->
                candidate == HeartRateReading.Unavailable || (candidate is HeartRateReading.Bpm && candidate.value in PLAUSIBLE_BPM)
            }
        return (reading as? HeartRateReading.Bpm)?.value
    }

    companion object {
        val DEFAULT_TIMEOUT: Duration = 8.seconds
        private val PLAUSIBLE_BPM = 30..240
    }
}

/** [HeartRateReadings] from Health Services' MeasureClient, which runs the sensor only while collected. */
class MeasureClientHeartRateReadings(
    private val measureClient: MeasureClient,
) : HeartRateReadings {
    override fun readings(): Flow<HeartRateReading> =
        callbackFlow {
            if (DataType.HEART_RATE_BPM !in measureClient.getCapabilities().supportedDataTypesMeasure) {
                send(HeartRateReading.Unavailable)
                close()
                return@callbackFlow
            }
            val callback =
                object : MeasureCallback {
                    override fun onAvailabilityChanged(
                        dataType: DeltaDataType<*, *>,
                        availability: Availability,
                    ) {
                        if (availability in UNAVAILABLE) trySend(HeartRateReading.Unavailable)
                    }

                    override fun onDataReceived(data: DataPointContainer) {
                        val point = data.getData(DataType.HEART_RATE_BPM).lastOrNull() ?: return
                        trySend(HeartRateReading.Bpm(point.value.roundToInt()))
                    }

                    override fun onRegistrationFailed(throwable: Throwable) {
                        close(throwable)
                    }
                }
            measureClient.registerMeasureCallback(DataType.HEART_RATE_BPM, callback)
            awaitClose { measureClient.unregisterMeasureCallbackAsync(DataType.HEART_RATE_BPM, callback) }
        }

    private companion object {
        val UNAVAILABLE: Set<Availability> = setOf(DataTypeAvailability.UNAVAILABLE, DataTypeAvailability.UNAVAILABLE_DEVICE_OFF_BODY)
    }
}
