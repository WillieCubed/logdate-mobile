package app.logdate.client.device.crypto

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for [AndroidCryptoManager], verifying the Android-specific implementation of
 * cryptographic primitives.
 *
 * This suite confirms that recovery phrases are generated correctly, master keys
 * are derived deterministically from those phrases, and random byte generation
 * meets the required length specifications.
 */
class AndroidCryptoManagerTest {
    private val cryptoManager = AndroidCryptoManager()

    @Test
    fun `generate recovery phrase`() =
        runTest {
            val phrase = cryptoManager.generateRecoveryPhrase()

            assertEquals(12, phrase.size)
            assertTrue(phrase.all { it.isNotBlank() })
        }

    @Test
    fun `derive master key`() =
        runTest {
            val phrase = cryptoManager.generateRecoveryPhrase()
            val masterKey = cryptoManager.deriveMasterKey(phrase)

            assertEquals(32, masterKey.size)
        }

    @Test
    fun `deterministic key derivation`() =
        runTest {
            val phrase = cryptoManager.generateRecoveryPhrase()

            val key1 = cryptoManager.deriveMasterKey(phrase)
            val key2 = cryptoManager.deriveMasterKey(phrase)

            assertTrue(key1.contentEquals(key2), "Same phrase should derive same key")
        }

    @Test
    fun `validate recovery phrase`() =
        runTest {
            val phrase = cryptoManager.generateRecoveryPhrase()

            assertTrue(cryptoManager.validateRecoveryPhrase(phrase))
        }

    @Test
    fun `generate random bytes`() {
        val bytes = cryptoManager.generateRandomBytes(32)

        assertEquals(32, bytes.size)
    }
}
