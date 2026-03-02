package app.logdate.client.device.crypto

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AndroidCryptoManagerTest {
    private val cryptoManager = AndroidCryptoManager()

    @Test
    fun testGenerateRecoveryPhrase() =
        runTest {
            val phrase = cryptoManager.generateRecoveryPhrase()

            assertEquals(12, phrase.size)
            assertTrue(phrase.all { it.isNotBlank() })
        }

    @Test
    fun testDeriveMasterKey() =
        runTest {
            val phrase = cryptoManager.generateRecoveryPhrase()
            val masterKey = cryptoManager.deriveMasterKey(phrase)

            assertEquals(32, masterKey.size)
        }

    @Test
    fun testDeterministicKeyDerivation() =
        runTest {
            val phrase = cryptoManager.generateRecoveryPhrase()

            val key1 = cryptoManager.deriveMasterKey(phrase)
            val key2 = cryptoManager.deriveMasterKey(phrase)

            assertTrue(key1.contentEquals(key2), "Same phrase should derive same key")
        }

    @Test
    fun testValidateRecoveryPhrase() =
        runTest {
            val phrase = cryptoManager.generateRecoveryPhrase()

            assertTrue(cryptoManager.validateRecoveryPhrase(phrase))
        }

    @Test
    fun testGenerateRandomBytes() {
        val bytes = cryptoManager.generateRandomBytes(32)

        assertEquals(32, bytes.size)
    }
}
