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
    fun `confirmTap vibrates in FULL mode`() {
        engine.setPreference(HapticPreference.FULL)
        engine.confirmTap()
        verify(exactly = 1) { vibrator.vibrate(any<VibrationEffect>()) }
    }

    @Test
    fun `confirmTap does not vibrate in REDUCED mode`() {
        engine.setPreference(HapticPreference.REDUCED)
        engine.confirmTap()
        verify(exactly = 0) { vibrator.vibrate(any<VibrationEffect>()) }
    }

    @Test
    fun `no haptics fire in OFF mode`() {
        engine.setPreference(HapticPreference.OFF)
        engine.startRecording()
        engine.stopRecording()
        engine.success()
        engine.rejection()
        engine.warning()
        engine.confirmTap()
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

        engine.confirmTap()
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

        engine.confirmTap()
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

        verify(exactly = 15) { vibrator.vibrate(any<VibrationEffect>()) }
    }

    @Test
    fun `preference defaults to FULL`() {
        engine.confirmTap()
        verify(exactly = 1) { vibrator.vibrate(any<VibrationEffect>()) }
    }
}
