package app.logdate.server.sync

import kotlin.test.Test
import kotlin.test.assertEquals

class ChangePageReadSizeTest {
    @Test
    fun `a page reads one row past its limit to find the next page`() {
        assertEquals(26, changePageReadSize(25))
    }

    @Test
    fun `a whole-feed read stays at the largest limit instead of wrapping negative`() {
        assertEquals(Int.MAX_VALUE, changePageReadSize(Int.MAX_VALUE))
    }
}
