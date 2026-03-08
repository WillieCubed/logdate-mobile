package app.logdate.integration.e2e.smoke

import app.logdate.integration.e2e.harness.withServerClientHarness
import kotlin.test.Test
import kotlin.test.assertTrue

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
