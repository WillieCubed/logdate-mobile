package app.logdate.server.passkeys

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Tests the configuration logic for WebAuthn and Relying Party (RP) parameters.
 *
 * Ensures that WebAuthn properties such as RP ID, RP Name, and Origin are correctly
 * derived from environment variables or server endpoints, and that explicit configuration
 * values take precedence over derived defaults.
 */
class WebAuthnConfigTest {
    @Test
    fun `derives origin and RP ID from server endpoint when webauthn env is absent`() {
        val config =
            WebAuthnConfig.fromEnvironment(
                relyingPartyId = null,
                relyingPartyName = null,
                origin = null,
                serverOrigin = "https://logdate-server-abc-uc.a.run.app/",
            )

        assertEquals("logdate-server-abc-uc.a.run.app", config.relyingPartyId)
        assertEquals("LogDate", config.relyingPartyName)
        assertEquals("https://logdate-server-abc-uc.a.run.app", config.origin)
    }

    @Test
    fun `explicit webauthn values override derived defaults`() {
        val config =
            WebAuthnConfig.fromEnvironment(
                relyingPartyId = "cloud.logdate.app",
                relyingPartyName = "LogDate Cloud",
                origin = "https://cloud.logdate.app",
                serverOrigin = "https://ignored.example.com",
            )

        assertEquals("cloud.logdate.app", config.relyingPartyId)
        assertEquals("LogDate Cloud", config.relyingPartyName)
        assertEquals("https://cloud.logdate.app", config.origin)
    }
}
