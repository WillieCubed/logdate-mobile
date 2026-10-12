package app.logdate.wear.reminders

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager

class SensorManagerOffBodyReadings(
    private val sensors: SensorManager,
) : OffBodyReadings {
    override fun subscribe(onReading: (Float?) -> Unit): PresenceRegistration? {
        val sensor = sensors.getDefaultSensor(Sensor.TYPE_LOW_LATENCY_OFFBODY_DETECT) ?: return null
        val listener =
            object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    if (event.sensor.type == Sensor.TYPE_LOW_LATENCY_OFFBODY_DETECT) onReading(event.values.firstOrNull())
                }

                override fun onAccuracyChanged(
                    sensor: Sensor?,
                    accuracy: Int,
                ) = Unit
            }
        if (!sensors.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)) return null
        return PresenceRegistration { sensors.unregisterListener(listener, sensor) }
    }
}
