package app.logdate.wear.haptic

import android.os.VibrationEffect
import android.os.Vibrator
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Comprehensive tests for [WearHapticEngine], validating that tactile feedback aligns with
 * user intensity preferences.
 *
 * This suite ensures that the engine correctly filters haptic events based on the active
 * [HapticPreference], distinguishing between "critical" feedback (like recording starts)
 * and "cosmetic" feedback (like scroll ticks) to respect the user's desired level of
 * physical interaction.
 */
class WearHapticEngineTest {
    private lateinit var vibrator: Vibrator
    private lateinit var engine: WearHapticEngine
    private val mockEffect: VibrationEffect = mockk()

    @Before
    fun setUp() {
        mockkStatic(VibrationEffect::class)
        every { VibrationEffect.createOneShot(any(), any()) } returns mockEffect
        every { VibrationEffect.createWaveform(any(), any<IntArray>(), any()) } returns mockEffect

        vibrator = mockk(relaxed = true)
        engine = WearHapticEngine(vibrator)
    }

    @After
    fun tearDown() {
        unmockkStatic(VibrationEffect::class)
    }

    @Test
    fun `no haptics fire in OFF mode`() {
        engine.setPreference(HapticPreference.OFF)
        engine.startRecording()
        engine.stopRecording()
        engine.success()
        engine.rejection()
        engine.warning()
        engine.scrollTick()
        verify(exactly = 0) { vibrator.vibrate(any<VibrationEffect>()) }
    }

    @Test
    fun `critical haptics fire in REDUCED mode`() {
        engine.setPreference(HapticPreference.REDUCED)

        engine.startRecording()
        engine.stopRecording()
        engine.success()
        engine.rejection()
        engine.warning()

        verify(exactly = 5) { vibrator.vibrate(any<VibrationEffect>()) }
    }

    @Test
    fun `non-critical haptics suppressed in REDUCED mode`() {
        engine.setPreference(HapticPreference.REDUCED)

        engine.scrollTick()
        engine.transition()
        engine.heartbeat()
        engine.outgoingPulse()
        engine.incomingPulse()
        engine.cameraShutter()
        engine.pause()
        engine.resume()
        engine.celebration()

        verify(exactly = 0) { vibrator.vibrate(any<VibrationEffect>()) }
    }

    @Test
    fun `all haptics fire in FULL mode`() {
        engine.setPreference(HapticPreference.FULL)

        engine.startRecording()
        engine.stopRecording()
        engine.pause()
        engine.resume()
        engine.rejection()
        engine.success()
        engine.celebration()
        engine.scrollTick()
        engine.transition()
        engine.heartbeat()
        engine.outgoingPulse()
        engine.incomingPulse()
        engine.cameraShutter()
        engine.warning()

        verify(exactly = 14) { vibrator.vibrate(any<VibrationEffect>()) }
    }

    @Test
    fun `preference defaults to FULL`() {
        engine.success()
        verify(exactly = 1) { vibrator.vibrate(any<VibrationEffect>()) }
    }
}
