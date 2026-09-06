package app.logdate.server.identity

import app.logdate.server.config.RuntimeProfile
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The PDS service endpoint is what the server advertises through `/api/v1/server/info`, and the
 * client follows that descriptor. Requiring https everywhere meant a server on a developer's
 * machine could only ever tell a device to go somewhere else — in practice, production.
 */
class AtprotoServiceEndpointTest {
    @Test
    fun `https is always acceptable`() {
        assertTrue(isAcceptableServiceEndpoint("https://logdate.app", RuntimeProfile.PRODUCTION))
        assertTrue(isAcceptableServiceEndpoint("https://logdate.app", RuntimeProfile.DEVELOPMENT))
    }

    @Test
    fun `loopback http is acceptable outside production`() {
        // 10.0.2.2 is the developer's machine as seen from inside an Android emulator.
        assertTrue(isAcceptableServiceEndpoint("http://10.0.2.2:8765", RuntimeProfile.DEVELOPMENT))
        assertTrue(isAcceptableServiceEndpoint("http://localhost:8765", RuntimeProfile.TEST))
        assertTrue(isAcceptableServiceEndpoint("http://127.0.0.1:8765", RuntimeProfile.DEVELOPMENT))
    }

    @Test
    fun `loopback http is never acceptable in production`() {
        assertFalse(isAcceptableServiceEndpoint("http://10.0.2.2:8765", RuntimeProfile.PRODUCTION))
        assertFalse(isAcceptableServiceEndpoint("http://localhost:8765", RuntimeProfile.PRODUCTION))
    }

    @Test
    fun `non-loopback http is never acceptable`() {
        assertFalse(isAcceptableServiceEndpoint("http://logdate.app", RuntimeProfile.DEVELOPMENT))
        assertFalse(isAcceptableServiceEndpoint("http://example.com", RuntimeProfile.TEST))
    }
}
