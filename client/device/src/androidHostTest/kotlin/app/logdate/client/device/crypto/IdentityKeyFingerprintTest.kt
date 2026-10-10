package app.logdate.client.device.crypto

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * Pins [IdentityKeyFingerprint] with a real HMAC-SHA256: every synced value already includes this
 * fingerprint, so its output can never change.
 */
class IdentityKeyFingerprintTest {
    private val cryptoManager = AndroidCryptoManager()

    @Test
    fun `the fingerprint is the first 8 bytes of the HMAC of its context in Base64`() {
        assertEquals("9+eXSPQZXdg=", IdentityKeyFingerprint.of(ByteArray(32) { 0x31 }, cryptoManager))
    }

    @Test
    fun `different keys have different fingerprints`() {
        assertNotEquals(
            IdentityKeyFingerprint.of(ByteArray(32) { 0x31 }, cryptoManager),
            IdentityKeyFingerprint.of(ByteArray(32) { 0x32 }, cryptoManager),
        )
    }
}
