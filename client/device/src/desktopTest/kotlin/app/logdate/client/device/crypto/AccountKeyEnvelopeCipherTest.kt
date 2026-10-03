package app.logdate.client.device.crypto

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse

class AccountKeyEnvelopeCipherTest {
    private val owner = "11111111-1111-1111-1111-111111111111"
    private val credential = "cGFzc2tleS1maXh0dXJl"
    private val secret = ByteArray(32) { (it + 128).toByte() }
    private val identity = ByteArray(32) { it.toByte() }
    private val media = ByteArray(32) { (it + 32).toByte() }

    @Test
    fun `only the credential secret can unlock the account keys`() {
        val cipher = AccountKeyEnvelopeCipher(DesktopCryptoManager())
        val envelope = cipher.seal(owner, credential, secret, identity, media)
        val opened = cipher.open(owner, credential, secret, envelope)
        assertContentEquals(identity, opened.identity)
        assertContentEquals(media, opened.media)
        assertFalse(envelope.asList().windowed(32).any { it == identity.asList() || it == secret.asList() })
        assertFails { cipher.open(owner, credential, ByteArray(32), envelope) }
        assertFails { cipher.open("22222222-2222-2222-2222-222222222222", credential, secret, envelope) }
        assertFails { cipher.open(owner, "YW5vdGhlci1wYXNza2V5", secret, envelope) }
    }

    @Test
    fun `modified truncated and plaintext key material is rejected`() {
        val cipher = AccountKeyEnvelopeCipher(DesktopCryptoManager())
        val envelope = cipher.seal(owner, credential, secret, identity, media)
        assertFails { cipher.open(owner, credential, secret, envelope.dropLast(1).toByteArray()) }
        assertFails { cipher.open(owner, credential, secret, identity + media) }
        envelope[envelope.lastIndex] = (envelope.last() + 1).toByte()
        assertFails { cipher.open(owner, credential, secret, envelope) }
    }
}
