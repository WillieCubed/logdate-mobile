package app.logdate.server.oauth

import java.math.BigInteger
import java.nio.charset.StandardCharsets
import java.security.Signature
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for [OAuthKeyService], ensuring the server's internal OAuth signing infrastructure
 * is robust and standard-compliant.
 *
 * This suite validates:
 * - The stable exposure of the server's public key material via JSON Web Key Sets (JWKS).
 * - Correct implementation of ES256 (ECDSA with P-256) for token signing.
 * - Compatibility of generated signatures with standard Java security providers.
 * - Internal cryptographic helpers, such as fixed-width byte array conversion for
 *   elliptic curve coordinates.
 */
class OAuthKeyServiceTest {
    @Test
    fun `jwks exposes stable es256 public key`() {
        val service = OAuthKeyService()

        val first = service.currentJwk()
        val second = service.currentJwk()
        val jwks = service.jwks()

        assertEquals(first, second)
        assertEquals(listOf(first), jwks.keys)
        assertEquals("EC", first.kty)
        assertEquals("sig", first.use)
        assertEquals(listOf("verify"), first.key_ops)
        assertEquals("ES256", first.alg)
        assertEquals("P-256", first.crv)
        assertTrue(first.kid.isNotBlank())
        assertTrue(first.x.isNotBlank())
        assertTrue(first.y.isNotBlank())
    }

    @Test
    fun `sign exposes a signature that verifies with the public key`() {
        val service = OAuthKeyService()
        val payload = "header.payload".toByteArray(StandardCharsets.UTF_8)
        val signature = service.sign(payload)

        val verified =
            Signature.getInstance("SHA256withECDSAinP1363Format").run {
                initVerify(service.publicKey())
                update(payload)
                verify(signature)
            }

        assertTrue(verified)
    }

    @Test
    fun `fixed width helper covers equal padded and truncated branches`() {
        val helperClass = Class.forName("app.logdate.server.oauth.OAuthKeyServiceKt")
        val toFixedWidth =
            helperClass
                .getDeclaredMethod("toFixedWidth", BigInteger::class.java, Int::class.javaPrimitiveType)
                .apply { isAccessible = true }

        val equalWidth = toFixedWidth.invoke(null, BigInteger("0102", 16), 2) as ByteArray
        val padded = toFixedWidth.invoke(null, BigInteger("01", 16), 4) as ByteArray
        val truncated = toFixedWidth.invoke(null, BigInteger("0102030405", 16), 4) as ByteArray

        assertContentEquals(byteArrayOf(0x01, 0x02), equalWidth)
        assertContentEquals(byteArrayOf(0x00, 0x00, 0x00, 0x01), padded)
        assertContentEquals(byteArrayOf(0x02, 0x03, 0x04, 0x05), truncated)
    }
}
