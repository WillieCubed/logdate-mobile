package app.logdate.server.identity

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Tests for [AtprotoIdentityConfig], ensuring the server's identity management
 * is correctly configured through defaults and environment overrides.
 *
 * This suite verifies:
 * - Proper initialization of default values for the LogDate ecosystem.
 * - Robust environment variable parsing, including normalization of hostnames,
 *   casing, and trailing slashes for PDS and PLC endpoints.
 * - Correct handling of configuration flags for PLC operation publishing and
 *   DID method selection.
 * - Strict validation to prevent unsupported or invalid identity configurations
 *   from being used at runtime.
 */
class AtprotoIdentityConfigTest {
    @Test
    fun `default config uses logdate host defaults`() {
        val config = AtprotoIdentityConfig()

        assertEquals("logdate.app", config.handleDomain)
        assertEquals("logdate.app", config.normalizedHandleDomain)
        assertEquals(HostedAccountDidMethod.PLC, config.hostedAccountDidMethod)
        assertEquals("did:web:logdate.app", config.serverDid)
        assertEquals("https://logdate.app", config.pdsServiceEndpoint)
        assertEquals("https://plc.directory", config.plcDirectoryUrl)
        assertEquals("https://plc.directory", config.normalizedPlcDirectoryUrl)
    }

    @Test
    fun `environment factory normalizes host casing and trailing slashes`() {
        val config = AtprotoIdentityConfig.fromEnvironment(handleDomain = " LogDate.App. ", pdsServiceEndpoint = "https://pds.logdate.app/")

        assertEquals("logdate.app", config.handleDomain)
        assertEquals("https://pds.logdate.app", config.pdsServiceEndpoint)
        assertEquals(HostedAccountDidMethod.PLC, config.hostedAccountDidMethod)
        assertEquals("did:web:logdate.app", config.serverDid)
    }

    @Test
    fun `environment factory supports hosted did method and plc publish flags`() {
        val config =
            AtprotoIdentityConfig.fromEnvironment(
                handleDomain = "users.logdate.app",
                pdsServiceEndpoint = "https://pds.logdate.app/",
                hostedAccountDidMethod = "web",
                publishHostedPlcOperations = "TrUe",
                plcDirectoryUrl = "https://plc.example.com/",
            )

        assertEquals(HostedAccountDidMethod.WEB, config.hostedAccountDidMethod)
        assertTrue(config.publishHostedPlcOperations)
        assertEquals("https://plc.example.com", config.normalizedPlcDirectoryUrl)
    }

    @Test
    fun `environment factory accepts explicit plc hosted did method and disabled publish flag`() {
        val config =
            AtprotoIdentityConfig.fromEnvironment(
                hostedAccountDidMethod = "plc",
                publishHostedPlcOperations = "false",
            )

        assertEquals(HostedAccountDidMethod.PLC, config.hostedAccountDidMethod)
        assertEquals(false, config.publishHostedPlcOperations)
    }

    @Test
    fun `environment factory rejects unsupported hosted did methods`() {
        assertFailsWith<IllegalArgumentException> {
            AtprotoIdentityConfig.fromEnvironment(hostedAccountDidMethod = "unsupported")
        }
    }
}
