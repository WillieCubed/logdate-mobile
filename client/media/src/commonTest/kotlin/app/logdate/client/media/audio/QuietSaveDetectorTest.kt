package app.logdate.client.media.audio

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class QuietSaveDetectorTest {
    @Test
    fun `an automatic microphone change starts a new window`() {
        val detector = QuietSaveDetector()
        for (time in 0L..119_900L step 100) assertFalse(detector.observe(0f, time, "bluetooth"))
        assertFalse(detector.observe(0f, 120_000, "built-in"))
        for (time in 120_100L..239_900L step 100) assertFalse(detector.observe(0f, time, "built-in"))
        assertTrue(detector.observe(0f, 240_000, "built-in"))
    }

    @Test
    fun `soft speech above a very quiet room resets the timer`() {
        val detector = QuietSaveDetector()
        for (time in 0L..119_900L step 100) assertFalse(detector.observe(0.0002f, time))
        assertFalse(detector.observe(0.004f, 120_000))
        assertFalse(detector.observe(0.0002f, 120_100))
    }

    @Test
    fun `quiet stops at two minutes and never before`() {
        val detector = QuietSaveDetector()
        assertFalse(detector.observe(0f, 0))
        for (time in 100L..119_900L step 100) assertFalse(detector.observe(0f, time))
        assertFalse(detector.observe(0f, 119_999))
        assertTrue(detector.observe(0f, 120_000))
    }

    @Test
    fun `quiet speech resets the window despite steady room noise`() {
        val detector = QuietSaveDetector()
        for (time in 0L..119_900L step 100) assertFalse(detector.observe(0.003f, time))
        assertFalse(detector.observe(0.04f, 120_000))
        for (time in 120_100L..240_000L step 100) assertFalse(detector.observe(0.003f, time))
        assertTrue(detector.observe(0.003f, 240_100))
    }

    @Test
    fun `invalid input and missed samples cannot establish quiet`() {
        val detector = QuietSaveDetector()
        detector.observe(0f, 0)
        assertFalse(detector.observe(null, 120_000))
        assertFalse(detector.observe(Float.NaN, 240_000))
        assertFalse(detector.observe(0f, 360_000))
        assertFalse(detector.observe(0f, 480_000))
    }

    @Test
    fun `pause or route change starts a fresh quiet window`() {
        val detector = QuietSaveDetector()
        for (time in 0L..119_900L step 100) detector.observe(0f, time)
        detector.reset()
        assertFalse(detector.observe(0f, 120_000))
    }

    @Test
    fun `continuous soft speech cannot raise the quiet threshold past itself`() {
        val detector = QuietSaveDetector()
        for (time in 0L..600_000L step 100) assertFalse(detector.observe(0.004f, time))
    }

    @Test
    fun `ongoing noticeable sound never becomes the noise floor`() {
        val detector = QuietSaveDetector()
        for (time in 0L..600_000L step 100) assertFalse(detector.observe(0.04f, time))
    }
}
