package app.logdate.client.device.crypto

import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals

/** Fixed bytes consumed by the native Swift client interoperability test. */
class SwiftInteropFixtureTest {
    @Test
    fun `sync field encryption matches Swift fixture`() {
        val rootKey = ByteArray(32) { it.toByte() }
        val iv = ByteArray(12) { (it + 32).toByte() }
        val fieldId = "entry-123"
        val aad = "type=CONTENT|v=1|id=$fieldId".encodeToByteArray()
        val crypto = DesktopCryptoManager()
        val derivedKey = KeyDerivation(crypto).deriveKey(rootKey, "journal_content", fieldId)
        val ciphertext = crypto.aesGcmEncrypt(derivedKey, iv, aad, "A Mac note.".encodeToByteArray())
        val fingerprint = crypto.hmacSha256(rootKey, "sync-payload-fingerprint".encodeToByteArray())

        assertEquals(
            "0a06627234738316ac09ae99b0836dc1a67eaf65ddd658b958ff55c5ebf8adcc",
            derivedKey.joinToString("") { "%02x".format(it) },
        )
        assertEquals("DZaJV+kJOVY35EfCrfwXkDUQOwduvyq+MIer", Base64.getEncoder().encodeToString(ciphertext))
        assertEquals("gaEKxl1FgoE=", Base64.getEncoder().encodeToString(fingerprint.copyOfRange(0, 8)))
    }
}
