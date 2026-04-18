package app.logdate.wear.health

import android.content.Context
import androidx.health.services.client.HealthServices
import androidx.health.services.client.PassiveListenerCallback
import androidx.health.services.client.clearPassiveListenerCallback
import androidx.health.services.client.data.DataPointContainer
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.PassiveListenerConfig
import androidx.health.services.client.getCapabilities
import io.github.aakira.napier.Napier
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Implementation of [WearHealthSensorManager] backed by Wear Health Services.
 * Passively monitors heart rate and step count, caching the latest values
 * so they can be sampled at note-save time.
 */
class HealthServicesWearHealthSensorManager(
    private val context: Context,
) : WearHealthSensorManager {
    private val client by lazy {
        HealthServices.getClient(context).passiveMonitoringClient
    }

    private val mutex = Mutex()
    private var latestHeartRate: Int? = null
    private var latestStepCount: Int? = null
    private var monitoring = false

    private val callback =
        object : PassiveListenerCallback {
            override fun onNewDataPointsReceived(dataPoints: DataPointContainer) {
                dataPoints.getData(DataType.HEART_RATE_BPM).lastOrNull()?.let { sample ->
                    latestHeartRate = sample.value.toInt()
                }
                dataPoints.getData(DataType.STEPS_DAILY).lastOrNull()?.let { sample ->
                    latestStepCount = sample.value.toInt()
                }
            }
        }

    override suspend fun isAvailable(): Boolean =
        try {
            val capabilities = client.getCapabilities()
            capabilities.supportedDataTypesPassiveMonitoring.contains(DataType.HEART_RATE_BPM)
        } catch (e: Exception) {
            Napier.w("Health Services not available", e)
            false
        }

    override suspend fun sampleCurrent(): HealthSnapshot =
        mutex.withLock {
            HealthSnapshot(
                heartRateBpm = latestHeartRate,
                stepCount = latestStepCount,
            )
        }

    override suspend fun startPassiveMonitoring() {
        mutex.withLock {
            if (monitoring) return
            try {
                val config =
                    PassiveListenerConfig
                        .builder()
                        .setDataTypes(setOf(DataType.HEART_RATE_BPM, DataType.STEPS_DAILY))
                        .build()
                client.setPassiveListenerCallback(config, callback)
                monitoring = true
                Napier.d("Health Services passive monitoring started")
            } catch (e: Exception) {
                Napier.w("Failed to start health passive monitoring", e)
            }
        }
    }

    override suspend fun stopPassiveMonitoring() {
        mutex.withLock {
            if (!monitoring) return
            try {
                client.clearPassiveListenerCallback()
                monitoring = false
                Napier.d("Health Services passive monitoring stopped")
            } catch (e: Exception) {
                Napier.w("Failed to stop health passive monitoring", e)
            }
        }
    }
}
