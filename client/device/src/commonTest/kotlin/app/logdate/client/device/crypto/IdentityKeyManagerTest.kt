package app.logdate.client.device.crypto

import app.logdate.client.device.storage.SecureStorage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IdentityKeyManagerTest {
    private val mockSecureStorage = InMemorySecureStorage()
    private val cryptoManager = FakeCryptoManager()
    private val manager = IdentityKeyManager(mockSecureStorage, cryptoManager)

    @Test
    fun testNoIdentityKeyInitially() =
        runTest {
            assertFalse(manager.hasIdentityKey())
        }

    @Test
    fun testSetupNewIdentity() =
        runTest {
            val phrase = manager.setupNewIdentity()

            assertEquals(12, phrase.words.size)
            assertTrue(manager.hasIdentityKey())
        }

    @Test
    fun testGetIdentityKeyAfterSetup() =
        runTest {
            manager.setupNewIdentity()
            val key = manager.getIdentityKey()

            assertEquals(32, key.size)
        }

    @Test
    fun testRecoverIdentity() =
        runTest {
            val phrase1 = manager.setupNewIdentity()
            val key1 = manager.getIdentityKey()

            manager.clearIdentityKey()
            assertFalse(manager.hasIdentityKey())

            manager.recoverIdentity(phrase1.words)
            assertTrue(manager.hasIdentityKey())

            val key2 = manager.getIdentityKey()
            assertTrue(key1.contentEquals(key2), "Same phrase should derive same key")
        }

    @Test
    fun testDeterministicKeyDerivation() =
        runTest {
            val phrase = manager.setupNewIdentity()
            val key1 = manager.getIdentityKey()

            manager.clearIdentityKey()
            manager.recoverIdentity(phrase.words)
            val key2 = manager.getIdentityKey()

            assertTrue(key1.contentEquals(key2))
        }

    @Test
    fun testGetIdentityKeyThrowsWhenNotSet() =
        runTest {
            assertFailsWith<IdentityKeyNotFoundException> {
                manager.getIdentityKey()
            }
        }

    @Test
    fun testClearIdentityKey() =
        runTest {
            manager.setupNewIdentity()
            assertTrue(manager.hasIdentityKey())

            manager.clearIdentityKey()
            assertFalse(manager.hasIdentityKey())
        }
}

class KeyDerivationTest {
    private val cryptoManager = FakeCryptoManager()
    private val keyDerivation = KeyDerivation(cryptoManager)

    @Test
    fun testDeriveKey() {
        val identityKey = ByteArray(32) { it.toByte() }
        val key = keyDerivation.deriveKey(identityKey, "journal_entry", "uuid-123")

        assertEquals(32, key.size)
    }

    @Test
    fun testDeterministicDerivation() {
        val identityKey = ByteArray(32) { it.toByte() }

        val key1 = keyDerivation.deriveKey(identityKey, "journal_entry", "uuid-123")
        val key2 = keyDerivation.deriveKey(identityKey, "journal_entry", "uuid-123")

        assertTrue(key1.contentEquals(key2))
    }

    @Test
    fun testDifferentContextProducesDifferentKey() {
        val identityKey = ByteArray(32) { it.toByte() }

        val key1 = keyDerivation.deriveKey(identityKey, "journal_entry", "uuid-123")
        val key2 = keyDerivation.deriveKey(identityKey, "media_file", "uuid-123")

        assertFalse(key1.contentEquals(key2))
    }

    @Test
    fun testDifferentContentIdProducesDifferentKey() {
        val identityKey = ByteArray(32) { it.toByte() }

        val key1 = keyDerivation.deriveKey(identityKey, "journal_entry", "uuid-123")
        val key2 = keyDerivation.deriveKey(identityKey, "journal_entry", "uuid-456")

        assertFalse(key1.contentEquals(key2))
    }
}

class PlcRecoveryKeyManagerTest {
    private val cryptoManager = FakeCryptoManager()
    private val keySupport = FakePlcRecoveryKeySupport()
    private val manager = DeterministicPlcRecoveryKeyManager(cryptoManager, keySupport)

    @Test
    fun `deriveDidKey is deterministic for the same recovery phrase`() =
        runTest {
            val phrase = List(12) { index -> "word-${index + 1}" }

            val first = manager.deriveDidKey(phrase)
            val second = manager.deriveDidKey(phrase)

            assertEquals(first, second)
            assertTrue(first.startsWith("did:key:z"))
        }

    @Test
    fun `signPayload uses the deterministic recovery key material`() =
        runTest {
            val phrase = List(12) { index -> "word-${index + 1}" }

            val signature = manager.signPayload(phrase, "payload".encodeToByteArray())

            assertEquals("sig-${keySupport.lastDerivedDidKey}-7061796c6f6164", signature)
        }
}

// Test implementations

class InMemorySecureStorage : SecureStorage {
    private val storage = mutableMapOf<String, String>()
    private val updates = MutableStateFlow<Map<String, String>>(emptyMap())

    override suspend fun getString(key: String): String? = storage[key]

    override suspend fun putString(
        key: String,
        value: String,
    ) {
        storage[key] = value
        updates.value = storage.toMap()
    }

    override suspend fun remove(key: String) {
        storage.remove(key)
        updates.value = storage.toMap()
    }

    override suspend fun clear() {
        storage.clear()
        updates.value = emptyMap()
    }

    override fun observeString(key: String): Flow<String?> = updates.map { it[key] }

    override fun observeAll(): Flow<Map<String, String>> = updates

    override suspend fun encrypt(data: ByteArray): ByteArray = data

    override suspend fun decrypt(data: ByteArray): ByteArray? = data
}

class FakeCryptoManager : CryptoManager {
    private var phraseCounter = 0

    override suspend fun generateRecoveryPhrase(): List<String> {
        phraseCounter++
        return (1..12).map { "word$phraseCounter-$it" }
    }

    override suspend fun deriveMasterKey(phrase: List<String>): ByteArray {
        val combined = phrase.joinToString(",").encodeToByteArray()
        return pseudoHash(combined, 32)
    }

    override fun validateRecoveryPhrase(phrase: List<String>): Boolean = phrase.size == 12

    override fun encryptSink(
        sink: okio.Sink,
        key: ByteArray,
        iv: ByteArray,
    ): okio.Sink {
        TODO("Not needed for tests")
    }

    override fun decryptSource(
        source: okio.Source,
        key: ByteArray,
        iv: ByteArray,
    ): okio.Source {
        TODO("Not needed for tests")
    }

    override fun generateRandomBytes(size: Int): ByteArray = ByteArray(size) { it.toByte() }

    override fun hmacSha256(
        key: ByteArray,
        data: ByteArray,
    ): ByteArray = pseudoHash(key + data, 32)

    override fun aesGcmEncrypt(
        key: ByteArray,
        iv: ByteArray,
        aad: ByteArray,
        plaintext: ByteArray,
    ): ByteArray = throw UnsupportedOperationException("AES-GCM encryption is not needed for this test.")

    override fun aesGcmDecrypt(
        key: ByteArray,
        iv: ByteArray,
        aad: ByteArray,
        ciphertext: ByteArray,
    ): ByteArray = throw UnsupportedOperationException("AES-GCM decryption is not needed for this test.")

    private fun pseudoHash(
        input: ByteArray,
        outputSize: Int,
    ): ByteArray {
        val out = ByteArray(outputSize)
        var state = 0x85EBCA6B.toInt()
        var index = 0
        while (index < outputSize) {
            for (byte in input) {
                state = state xor byte.toInt()
                state = state * 1103515245 + 12345
            }
            out[index] = (state ushr ((index % 4) * 8)).toByte()
            index++
        }
        return out
    }
}

private class FakePlcRecoveryKeySupport : PlcRecoveryKeySupport {
    var lastDerivedDidKey: String? = null

    override fun isValidPrivateKey(privateKeyMaterial: ByteArray): Boolean = privateKeyMaterial.any { it != 0.toByte() }

    override fun didKey(privateKeyMaterial: ByteArray): String =
        "did:key:z${privateKeyMaterial.take(4).joinToString("") { "%02x".format(it.toInt() and 0xff) }}".also {
            lastDerivedDidKey = it
        }

    override fun signPayload(
        privateKeyMaterial: ByteArray,
        payload: ByteArray,
    ): String {
        val didKey = didKey(privateKeyMaterial)
        return "sig-$didKey-${payload.joinToString("") { "%02x".format(it.toInt() and 0xff) }}"
    }
}
