package app.logdate.client.sensor

import kotlinx.coroutines.flow.SharedFlow

interface GyroSensorProvider {
    val currentValue: SharedFlow<GyroOffset>

    /**
     * Releases resources and observers associated with the sensor provider.
     */
    fun cleanup()
}