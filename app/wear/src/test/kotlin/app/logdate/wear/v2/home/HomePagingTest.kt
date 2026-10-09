package app.logdate.wear.v2.home

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HomePagingTest {
    @Test
    fun `turning up at the top of the moment list returns to Today`() {
        assertEquals(
            HomePage.TODAY,
            pageForCrown(HomePage.FLOW, scrollPixels = -40f, canScrollBackward = false, canScrollForward = true, locked = false),
        )
    }

    @Test
    fun `turning up inside the moment list scrolls the list`() {
        assertNull(pageForCrown(HomePage.FLOW, scrollPixels = -40f, canScrollBackward = true, canScrollForward = true, locked = false))
    }

    @Test
    fun `turning down at the end of Today opens the moment list`() {
        assertEquals(
            HomePage.FLOW,
            pageForCrown(HomePage.TODAY, scrollPixels = 40f, canScrollBackward = true, canScrollForward = false, locked = false),
        )
    }

    @Test
    fun `turning down inside Today scrolls Today`() {
        assertNull(pageForCrown(HomePage.TODAY, scrollPixels = 40f, canScrollBackward = false, canScrollForward = true, locked = false))
    }

    @Test
    fun `turning up at the top of Today stays on Today`() {
        assertNull(pageForCrown(HomePage.TODAY, scrollPixels = -40f, canScrollBackward = false, canScrollForward = false, locked = false))
    }

    @Test
    fun `turning down at the end of the moment list stays on the list`() {
        assertNull(pageForCrown(HomePage.FLOW, scrollPixels = 40f, canScrollBackward = true, canScrollForward = false, locked = false))
    }

    @Test
    fun `nothing pages while recording`() {
        assertNull(pageForCrown(HomePage.FLOW, scrollPixels = -40f, canScrollBackward = false, canScrollForward = true, locked = true))
        assertNull(pageForCrown(HomePage.TODAY, scrollPixels = 40f, canScrollBackward = true, canScrollForward = false, locked = true))
    }
}
