package app.logdate.wear.v2.playback

import org.junit.Test
import kotlin.test.assertEquals

class CrownScrubberTest {
    private val scrubber = CrownScrubber(pixelsPerStep = 60f, stepMs = 5_000L)

    @Test
    fun `less than one detent waits for more rotation`() {
        assertEquals(0, scrubber.onRotate(30f))
        assertEquals(1, scrubber.onRotate(30f))
    }

    @Test
    fun `a fast turn crosses several detents and counts the remainder later`() {
        assertEquals(2, scrubber.onRotate(150f))
        assertEquals(1, scrubber.onRotate(30f))
    }

    @Test
    fun `turning back moves back`() {
        assertEquals(-1, scrubber.onRotate(-60f))
    }

    @Test
    fun `turning back part way cancels part of a forward turn`() {
        assertEquals(0, scrubber.onRotate(50f))
        assertEquals(0, scrubber.onRotate(-20f))
        assertEquals(1, scrubber.onRotate(30f))
    }

    @Test
    fun `reset forgets a part turn`() {
        scrubber.onRotate(50f)

        scrubber.reset()

        assertEquals(0, scrubber.onRotate(30f))
    }
}
