package app.logdate.ui.tokens

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Draws the morph at a real size. Path bounds include curve control points, so the circle is only
 * checked for being centered; the stop square has straight edges and is checked exactly.
 */
class RecordShapeOutlineTest {
    @Test
    fun `the circle is centered in the size it is drawn at`() {
        val bounds = RecordShape.outline(progress = 0f, size = 100f).getBounds()

        assertEquals(50f, bounds.center.x, 0.5f)
        assertEquals(50f, bounds.center.y, 0.5f)
    }

    @Test
    fun `the stop square fills the middle 60 percent`() {
        val bounds = RecordShape.outline(progress = 1f, size = 100f).getBounds()

        assertEquals(20f, bounds.left, 0.5f)
        assertEquals(20f, bounds.top, 0.5f)
        assertEquals(80f, bounds.right, 0.5f)
        assertEquals(80f, bounds.bottom, 0.5f)
    }
}
