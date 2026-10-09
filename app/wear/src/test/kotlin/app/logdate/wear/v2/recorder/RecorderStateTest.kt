package app.logdate.wear.v2.recorder

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RecorderStateTest {
    @Test
    fun `the recorder starts ready`() {
        assertEquals(RecorderState.Ready, RecorderViewModel().state.value)
    }

    @Test
    fun `only a recording locks Home on Today`() {
        assertFalse(RecorderState.Ready.locksPaging)
        assertTrue(RecorderState.Recording.locksPaging)
    }
}
