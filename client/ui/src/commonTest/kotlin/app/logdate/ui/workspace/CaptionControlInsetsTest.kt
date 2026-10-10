package app.logdate.ui.workspace

import androidx.compose.ui.unit.IntRect
import kotlin.test.Test
import kotlin.test.assertEquals

class CaptionControlInsetsTest {
    @Test
    fun appMenuAndWindowButtonsTakeTheirEdges() {
        val controls = listOf(IntRect(0, 0, 140, 96), IntRect(1600, 0, 1840, 96))

        assertEquals(140 to 240, captionControlInsets(controls, windowWidth = 1840))
    }

    @Test
    fun rightToLeftControlsSwapEdges() {
        val controls = listOf(IntRect(0, 0, 240, 96), IntRect(1700, 0, 1840, 96))

        assertEquals(240 to 140, captionControlInsets(controls, windowWidth = 1840))
    }

    @Test
    fun noCaptionControlsLeaveTheEdgesFree() {
        assertEquals(0 to 0, captionControlInsets(emptyList(), windowWidth = 1840))
    }

    @Test
    fun controlsReportedForAWiderWindowNeverGiveNegativeWidths() {
        // While a window shrinks, its control rectangles can still describe the old, wider window.
        val controls = listOf(IntRect(0, 0, 140, 96), IntRect(2300, 0, 2540, 96))

        val (left, right) = captionControlInsets(controls, windowWidth = 1200)

        assertEquals(140, left)
        assertEquals(0, right)
    }

    @Test
    fun anEdgeNeverTakesMoreThanHalfTheWindow() {
        val controls = listOf(IntRect(0, 0, 900, 96))

        assertEquals(600 to 0, captionControlInsets(controls, windowWidth = 1200))
    }
}
