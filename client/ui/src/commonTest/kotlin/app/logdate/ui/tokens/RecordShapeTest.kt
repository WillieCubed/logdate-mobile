package app.logdate.ui.tokens

import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Checks the record button's morph in shape units: a circle of radius 1 at progress 0, a rounded
 * square [RecordShape.STOP_SIDE] wide at progress 1.
 */
class RecordShapeTest {
    @Test
    fun `the record shape is a circle of radius 1`() {
        anchors(progress = 0f).forEach { (x, y) ->
            assertEquals(1f, sqrt(x * x + y * y), TOLERANCE)
        }
    }

    @Test
    fun `the stop shape is a square STOP_SIDE wide`() {
        val anchors = anchors(progress = 1f)

        assertEquals(RecordShape.STOP_SIDE / 2, anchors.maxOf { abs(it.first) }, TOLERANCE)
        assertEquals(RecordShape.STOP_SIDE / 2, anchors.maxOf { abs(it.second) }, TOLERANCE)
    }

    @Test
    fun `halfway the button is between the two shapes`() {
        val extent = anchors(progress = 0.5f).maxOf { abs(it.first) }

        assertTrue(extent < 1f && extent > RecordShape.STOP_SIDE / 2, "extent was $extent")
    }

    private fun anchors(progress: Float): List<Pair<Float, Float>> =
        RecordShape.morph.asCubics(progress).map { cubic -> cubic.anchor0X to cubic.anchor0Y }

    private companion object {
        const val TOLERANCE = 0.01f
    }
}
