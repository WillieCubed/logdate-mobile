package app.logdate.ui.tokens

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pins the springs both apps move with. A change here changes how every LogDate surface feels, so
 * it should be a deliberate one.
 */
class LogDateMotionTest {
    @Test
    fun `standard barely overshoots at a medium-low stiffness`() {
        val spec = LogDateMotion.standard<Float>()

        assertEquals(0.9f, spec.dampingRatio)
        assertEquals(400f, spec.stiffness)
    }

    @Test
    fun `quick never overshoots and is stiffer than standard`() {
        val quick = LogDateMotion.quick<Float>()

        assertEquals(1f, quick.dampingRatio)
        assertTrue(quick.stiffness > LogDateMotion.standard<Float>().stiffness)
    }

    @Test
    fun `expressive is the only spring with a visible bounce`() {
        val expressive = LogDateMotion.expressive<Float>()

        assertEquals(0.75f, expressive.dampingRatio)
        assertTrue(expressive.dampingRatio < LogDateMotion.standard<Float>().dampingRatio)
    }
}
