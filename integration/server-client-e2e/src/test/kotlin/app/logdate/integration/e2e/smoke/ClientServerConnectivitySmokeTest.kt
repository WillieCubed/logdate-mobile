package app.logdate.integration.e2e.smoke

import app.logdate.integration.e2e.harness.withServerClientHarness
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Basic connectivity smoke tests to verify the integrity of the communication link
 * between the client and the backend server.
 *
 * These tests perform lightweight, non-mutating operations against the live server
 * environment to ensure that network configuration, base URLs, and core API
 * reachability are correctly established before more complex integration journeys
 * are executed.
 */
class ClientServerConnectivitySmokeTest {
    @Test
    fun `client can call server username availability endpoint`() =
        kotlinx.coroutines.test.runTest {
            withServerClientHarness {
                val username = "smoke_user"
                val result = apiClient.checkUsernameAvailability(username)
                assertTrue(result.isSuccess)
                assertTrue(result.getOrThrow().available)
            }
        }
}
