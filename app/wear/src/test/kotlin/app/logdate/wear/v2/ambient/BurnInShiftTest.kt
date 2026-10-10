package app.logdate.wear.v2.ambient

import org.junit.Test
import kotlin.test.assertEquals

class BurnInShiftTest {
    @Test
    fun `the face moves to a new place at each of four ticks and then starts over`() {
        val places = (0 until 8).map(::burnInShift)

        assertEquals(4, places.take(4).toSet().size)
        assertEquals(places.take(4), places.drop(4))
        assertEquals(BurnInShift.NONE, burnInShift(0))
    }
}
