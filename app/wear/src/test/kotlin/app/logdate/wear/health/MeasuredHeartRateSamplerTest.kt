package app.logdate.wear.health

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class MeasuredHeartRateSamplerTest {
    private var permitted = true
    private var collected = false

    @Test
    fun `the first plausible reading is the heart rate`() =
        runTest {
            assertEquals(72, sampler(HeartRateReading.Bpm(0), HeartRateReading.Bpm(72), HeartRateReading.Bpm(75)).sample())
        }

    @Test
    fun `a sensor that cannot read gives no heart rate`() =
        runTest {
            assertNull(sampler(HeartRateReading.Unavailable, HeartRateReading.Bpm(72)).sample())
        }

    @Test
    fun `without the permission the sensor is never read`() =
        runTest {
            permitted = false

            assertNull(sampler(HeartRateReading.Bpm(72)).sample())
            assertFalse(collected)
        }

    @Test
    fun `nothing within eight seconds gives no heart rate`() =
        runTest {
            val result = async { sampler(waitAfterwards = true).sample() }

            advanceTimeBy(8_001)

            assertTrue(result.isCompleted)
            assertNull(result.await())
        }

    @Test
    fun `a sensor that fails gives no heart rate`() =
        runTest {
            val failing =
                MeasuredHeartRateSampler(
                    readings = { flow { throw IllegalStateException("registration failed") } },
                    hasPermission = { true },
                )

            assertNull(failing.sample())
        }

    private fun sampler(
        vararg readings: HeartRateReading,
        waitAfterwards: Boolean = false,
    ) = MeasuredHeartRateSampler(
        readings = {
            flow {
                collected = true
                readings.forEach { emit(it) }
                if (waitAfterwards) awaitCancellation()
            }
        },
        hasPermission = { permitted },
    )
}
