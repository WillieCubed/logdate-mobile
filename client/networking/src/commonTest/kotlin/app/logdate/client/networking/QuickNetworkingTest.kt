package app.logdate.client.networking

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.time.Clock

/**
 * Quick validation test to ensure networking test infrastructure is working.
 */
class QuickNetworkingTest {
    @Test
    fun httpClient_exists() {
        assertNotNull(httpClient)
    }

    @Test
    fun networkState_connected_canBeCreated() {
        val timestamp = Clock.System.now()
        val state = NetworkState.Connected(timestamp)
        assertNotNull(state)
    }
}
