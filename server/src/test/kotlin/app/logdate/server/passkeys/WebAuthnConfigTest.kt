package app.logdate.server.passkeys

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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

    @Test
    fun `allowed origins add android apk-key-hash entries alongside the web origin`() {
        val androidOrigin = "android:apk-key-hash:pNiP8Z6X1xH6vQX0r1Tq8m9Hb3kq9b0c0d1e2f3g4h5"
        val config =
            WebAuthnConfig.fromEnvironment(
                relyingPartyId = "cloud.logdate.app",
                relyingPartyName = "LogDate Cloud",
                origin = "https://cloud.logdate.app",
                allowedOrigins = "$androidOrigin, https://extra.logdate.app",
                serverOrigin = null,
            )

        // The canonical web origin still drives [origin] and rpId derivation.
        assertEquals("https://cloud.logdate.app", config.origin)
        assertTrue(config.origins.contains(androidOrigin))
        assertTrue(config.origins.contains("https://cloud.logdate.app"))
        assertTrue(config.origins.contains("https://extra.logdate.app"))
    }

    @Test
    fun `allowed origins drop malformed entries instead of failing`() {
        val androidOrigin = "android:apk-key-hash:pNiP8Z6X1xH6vQX0r1Tq8m9Hb3kq9b0c0d1e2f3g4h5"
        val config =
            WebAuthnConfig.fromEnvironment(
                relyingPartyId = "cloud.logdate.app",
                relyingPartyName = "LogDate Cloud",
                origin = "https://cloud.logdate.app",
                allowedOrigins = "ftp://nope.example, , $androidOrigin",
                serverOrigin = null,
            )

        assertTrue(config.origins.contains(androidOrigin))
        assertFalse(config.origins.any { it.startsWith("ftp://") })
    }

    @Test
    fun `web origin is always present even when allowed origins is blank`() {
        val config =
            WebAuthnConfig.fromEnvironment(
                relyingPartyId = "cloud.logdate.app",
                relyingPartyName = "LogDate Cloud",
                origin = "https://cloud.logdate.app",
                allowedOrigins = null,
                serverOrigin = null,
            )

        assertEquals(setOf("https://cloud.logdate.app"), config.origins)
    }
}
