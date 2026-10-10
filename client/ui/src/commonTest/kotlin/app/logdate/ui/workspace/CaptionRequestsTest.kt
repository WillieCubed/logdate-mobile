package app.logdate.ui.workspace

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CaptionRequestsTest {
    @Test
    fun `the caption returns to the system's once the only screen wanting it transparent leaves`() {
        val requests = CaptionRequests<String>()
        requests.acquire("window")

        assertTrue(requests.release("window"))
    }

    @Test
    fun `a screen leaving keeps the caption transparent while another screen in the window wants it`() {
        val requests = CaptionRequests<String>()
        requests.acquire("window")
        requests.acquire("window")

        assertFalse(requests.release("window"), "Home stays composed while the editor leaves")
        assertTrue(requests.release("window"))
    }

    @Test
    fun `each window counts its own screens`() {
        val requests = CaptionRequests<String>()
        requests.acquire("home")
        requests.acquire("editor")

        assertTrue(requests.release("editor"))
        assertTrue(requests.release("home"))
    }
}
