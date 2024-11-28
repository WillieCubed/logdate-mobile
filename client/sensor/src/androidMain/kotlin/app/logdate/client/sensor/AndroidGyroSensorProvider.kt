package app.logdate.client.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.shareIn

class AndroidGyroSensorProvider(
    coroutineScope: CoroutineScope,
    context: Context,
) : GyroSensorProvider {
    private val sensorManager = context.getSystemService(SensorManager::class.java)
    private val gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val _currentValue = MutableSharedFlow<GyroOffset>()

    private val gyroscopeListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val (x, y) = event.values
            _currentValue.tryEmit(GyroOffset(x, y))
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
            // No-op
        }
    }

    init {
        if (gyroscope != null) {
            sensorManager.registerListener(
                gyroscopeListener,
                gyroscope,
                SensorManager.SENSOR_DELAY_NORMAL
            )
        }
    }

    override val currentValue: SharedFlow<GyroOffset> = _currentValue.shareIn(
        coroutineScope,
        started = SharingStarted.WhileSubscribed(),
    )

    override fun cleanup() {
        sensorManager.unregisterListener(gyroscopeListener)
    }
}