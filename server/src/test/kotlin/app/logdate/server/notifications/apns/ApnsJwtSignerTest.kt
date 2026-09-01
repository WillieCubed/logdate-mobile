package app.logdate.server.notifications.apns

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ApnsJwtSignerTest {
    @Test
    fun `mints an ES256 token that verifies with the public key`() {
        val keyPair = generateKeyPair()
        val clock = MutableClock(Instant.ofEpochSecond(1_800_000_000L))
        val signer =
            ApnsJwtSigner(
                teamId = "TEAM123",
                keyId = "KEY456",
                privateKeyPem = keyPair.private.toPem(),
                clock = clock,
            )

        val token = signer.token()
        val parts = token.split('.')
        assertEquals(3, parts.size)

        val header = decodeJson(parts[0])
        val payload = decodeJson(parts[1])
        assertEquals("ES256", header["alg"]?.jsonPrimitive?.content)
        assertEquals("KEY456", header["kid"]?.jsonPrimitive?.content)
        assertEquals("JWT", header["typ"]?.jsonPrimitive?.content)
        assertEquals("TEAM123", payload["iss"]?.jsonPrimitive?.content)
        assertEquals("1800000000", payload["iat"]?.jsonPrimitive?.content)

        val signatureBytes = Base64.getUrlDecoder().decode(parts[2])
        assertEquals(64, signatureBytes.size)
        val verifier = Signature.getInstance("SHA256withECDSAinP1363Format")
        verifier.initVerify(keyPair.public)
        verifier.update("${parts[0]}.${parts[1]}".toByteArray())
        assertTrue(verifier.verify(signatureBytes))
    }

    @Test
    fun `reuses a token for forty nine minutes and rotates it at fifty`() {
        val clock = MutableClock(Instant.ofEpochSecond(1_800_000_000L))
        val signer =
            ApnsJwtSigner(
                teamId = "TEAM123",
                keyId = "KEY456",
                privateKeyPem = generateKeyPair().private.toPem(),
                clock = clock,
            )

        val original = signer.token()
        clock.advanceSeconds(49 * 60L)
        assertEquals(original, signer.token())

        clock.advanceSeconds(60L)
        assertNotEquals(original, signer.token())
    }

    private fun generateKeyPair(): KeyPair =
        KeyPairGenerator
            .getInstance("EC")
            .apply { initialize(256) }
            .generateKeyPair()

    private fun java.security.PrivateKey.toPem(): String {
        val encoded = Base64.getMimeEncoder(64, "\n".toByteArray()).encodeToString(encoded)
        return "-----BEGIN PRIVATE KEY-----\n$encoded\n-----END PRIVATE KEY-----"
    }

    private fun decodeJson(encoded: String) =
        Json
            .parseToJsonElement(String(Base64.getUrlDecoder().decode(encoded)))
            .jsonObject

    private class MutableClock(
        private var now: Instant,
    ) : Clock() {
        override fun instant(): Instant = now

        override fun getZone(): ZoneId = ZoneOffset.UTC

        override fun withZone(zone: ZoneId): Clock = this

        fun advanceSeconds(seconds: Long) {
            now = now.plusSeconds(seconds)
        }
    }
}
