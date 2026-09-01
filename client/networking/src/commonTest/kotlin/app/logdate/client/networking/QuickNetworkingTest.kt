package app.logdate.client.networking

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.time.Clock

/**
 * Quick validation test to ensure networking test infrastructure is working.
 */
class QuickNetworkingTest {
    @Test
    fun `http client exists`() {
        assertNotNull(httpClient)
    }

    @Test
    fun `network state connected can be created`() {
        val timestamp = Clock.System.now()
        val state = NetworkState.Connected(timestamp)
        assertNotNull(state)
    }
}
