package app.logdate.wear.reminders

import app.logdate.client.sync.peer.state.Presence
import app.logdate.client.sync.peer.state.PresenceState
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class WearPresenceSamplerTest {
    private val clock =
        object : Clock {
            override fun now(): Instant = Instant.fromEpochMilliseconds(10_000)
        }

    @Test
    fun `on body reading is returned and sensor is stopped`() =
        runTest {
            val sensor = Readings()
            val result = async { WearPresenceSampler(sensor, clock).sample() }
            runCurrent()
            sensor.emit(1f)
            assertEquals(Presence(PresenceState.ON_WRIST, 10_000), result.await())
            assertTrue(sensor.closed)
        }

    @Test
    fun `off body reading is returned and sensor is stopped`() =
        runTest {
            val sensor = Readings()
            val result = async { WearPresenceSampler(sensor, clock).sample() }
            runCurrent()
            sensor.emit(0f)
            assertEquals(Presence(PresenceState.OFF_WRIST, 10_000), result.await())
            assertTrue(sensor.closed)
        }

    @Test
    fun `missing sensor returns unknown immediately`() =
        runTest {
            assertEquals(Presence(), WearPresenceSampler(OffBodyReadings { null }, clock).sample())
            assertEquals(0, testScheduler.currentTime)
        }

    @Test
    fun `timeout stops the sensor after two seconds`() =
        runTest {
            val sensor = Readings()
            val result = async { WearPresenceSampler(sensor, clock).sample() }
            runCurrent()
            advanceTimeBy(1_999)
            assertFalse(result.isCompleted)
            advanceTimeBy(1)
            runCurrent()
            assertEquals(Presence(), result.await())
            assertTrue(sensor.closed)
        }

    @Test
    fun `cancelling the request stops the sensor`() =
        runTest {
            val sensor = Readings()
            val result = async { WearPresenceSampler(sensor, clock).sample() }
            runCurrent()
            result.cancel()
            result.join()
            assertTrue(result.isCancelled)
            assertTrue(sensor.closed)
        }

    @Test
    fun `malformed sensor readings stay unknown`() =
        runTest {
            for (reading in listOf(null, Float.NaN, -1f, 0.5f, 2f)) {
                val sensor = Readings()
                val result = async { WearPresenceSampler(sensor, clock).sample() }
                runCurrent()
                sensor.emit(reading)
                assertEquals(Presence(), result.await())
                assertTrue(sensor.closed)
            }
        }

    @Test
    fun `sensor denied by the system stays unknown`() =
        runTest {
            val sampler = WearPresenceSampler(OffBodyReadings { throw SecurityException("denied") }, clock)
            assertEquals(Presence(), sampler.sample())
        }

    @Test
    fun `immediate registration reading still closes sensor`() =
        runTest {
            var closed = false
            val sampler =
                WearPresenceSampler(
                    OffBodyReadings { callback ->
                        callback(1f)
                        PresenceRegistration { closed = true }
                    },
                    clock,
                )
            assertEquals(Presence(PresenceState.ON_WRIST, 10_000), sampler.sample())
            assertTrue(closed)
        }

    private class Readings : OffBodyReadings {
        private var listener: ((Float?) -> Unit)? = null
        var closed = false

        override fun subscribe(onReading: (Float?) -> Unit): PresenceRegistration {
            listener = onReading
            return PresenceRegistration { closed = true }
        }

        fun emit(value: Float?) {
            listener?.invoke(value)
        }
    }
}
