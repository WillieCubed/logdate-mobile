package app.logdate.wear.reminders

import android.hardware.Sensor
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SensorManagerOffBodyReadingsTest {
    private val manager = mockk<SensorManager>(relaxed = true)
    private val sensor = mockk<Sensor>()

    @Test
    fun `unsupported watch returns unknown without registering`() =
        runTest {
            every { manager.getDefaultSensor(Sensor.TYPE_LOW_LATENCY_OFFBODY_DETECT) } returns null
            assertEquals(
                app.logdate.client.sync.peer.state
                    .Presence(),
                WearPresenceSampler(SensorManagerOffBodyReadings(manager)).sample(),
            )
            verify(exactly = 0) { manager.registerListener(any<SensorEventListener>(), any(), any()) }
        }

    @Test
    fun `native registration uses off body sensor and cleanup unregisters that listener`() {
        val listener = slot<SensorEventListener>()
        every { manager.getDefaultSensor(Sensor.TYPE_LOW_LATENCY_OFFBODY_DETECT) } returns sensor
        every { manager.registerListener(capture(listener), sensor, SensorManager.SENSOR_DELAY_NORMAL) } returns true

        val registration = SensorManagerOffBodyReadings(manager).subscribe { }
        assertNotNull(registration)
        registration.close()

        verify(exactly = 1) { manager.unregisterListener(listener.captured, sensor) }
    }

    @Test
    fun `failed registration returns unknown`() {
        every { manager.getDefaultSensor(Sensor.TYPE_LOW_LATENCY_OFFBODY_DETECT) } returns sensor
        every { manager.registerListener(any<SensorEventListener>(), sensor, SensorManager.SENSOR_DELAY_NORMAL) } returns false
        assertNull(SensorManagerOffBodyReadings(manager).subscribe { })
    }
}
