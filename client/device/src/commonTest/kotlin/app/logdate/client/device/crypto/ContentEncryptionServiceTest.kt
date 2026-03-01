package app.logdate.client.device.crypto

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ContentEncryptionServiceTest {
    
    private val mockSecureStorage = InMemorySecureStorage()
    private val cryptoManager = TestCryptoManager()
    private val identityKeyManager = IdentityKeyManager(mockSecureStorage, cryptoManager)
    private val keyDerivation = KeyDerivation(cryptoManager)
    private val service = ContentEncryptionService(identityKeyManager, keyDerivation, cryptoManager)
    
    @Test
    fun testEncryptDecryptRoundtrip() = runTest {
        identityKeyManager.setupNewIdentity()
        
        val plaintext = "Hello, World! This is sensitive journal content."
        val contentId = "entry-123"
        
        val envelope = service.encryptContent(contentId, plaintext)
        val decrypted = service.decryptContent(contentId, envelope)
        
        assertEquals(plaintext, decrypted)
    }
    
    @Test
    fun testEncryptedContentIsNotPlaintext() = runTest {
        identityKeyManager.setupNewIdentity()
        
        val plaintext = "Secret data"
        val contentId = "entry-456"
        
        val envelope = service.encryptContent(contentId, plaintext)
        
        assertFalse(envelope.ciphertext.contains(plaintext))
    }
    
    @Test
    fun testWrongContentIdFails() = runTest {
        identityKeyManager.setupNewIdentity()
        
        val plaintext = "Secret"
        val envelope = service.encryptContent("entry-1", plaintext)
        
        assertFailsWith<Exception> {
            service.decryptContent("entry-2", envelope)
        }
    }
    
    @Test
    fun testTamperedCiphertextFails() = runTest {
        identityKeyManager.setupNewIdentity()
        
        val plaintext = "Secret"
        val contentId = "entry-3"
        val envelope = service.encryptContent(contentId, plaintext)
        
        // Flip a bit in ciphertext
        val tamperedBytes = envelope.ciphertext.toByteArray()
        if (tamperedBytes.isNotEmpty()) {
            tamperedBytes[0] = (tamperedBytes[0].toInt() xor 1).toByte()
        }
        val tampered = envelope.copy(ciphertext = tamperedBytes.decodeToString())
        
        assertFailsWith<Exception> {
            service.decryptContent(contentId, tampered)
        }
    }
    
    @Test
    fun testMultipleEntriesDifferentKeys() = runTest {
        identityKeyManager.setupNewIdentity()
        
        val envelope1 = service.encryptContent("entry-1", "Content 1")
        val envelope2 = service.encryptContent("entry-2", "Content 1")
        
        // Same plaintext, different IDs should produce different ciphertexts
        assertFalse(envelope1.ciphertext == envelope2.ciphertext)
    }
    
    @Test
    fun testIdentityNotSetUpThrows() = runTest {
        assertFailsWith<IdentityKeyNotFoundException> {
            service.encryptContent("entry", "data")
        }
    }
}

/**
 * Test implementation of CryptoManager with real crypto for testing.
 */
class TestCryptoManager : CryptoManager {
    override suspend fun generateRecoveryPhrase(): List<String> {
        return (1..12).map { "test$it" }
    }
    
    override suspend fun deriveMasterKey(phrase: List<String>): ByteArray {
        val combined = phrase.joinToString("")
        val hash = java.security.MessageDigest.getInstance("SHA-256").digest(combined.toByteArray())
        return hash.copyOfRange(0, 32)
    }
    
    override fun validateRecoveryPhrase(phrase: List<String>): Boolean {
        return phrase.size == 12
    }
    
    override fun encryptSink(sink: okio.Sink, key: ByteArray, iv: ByteArray): okio.Sink {
        TODO("Not needed for tests")
    }
    
    override fun decryptSource(source: okio.Source, key: ByteArray, iv: ByteArray): okio.Source {
        TODO("Not needed for tests")
    }
    
    override fun generateRandomBytes(size: Int): ByteArray {
        return ByteArray(size) { Math.random().toInt().toByte() }
    }
    
    override fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        val mac = javax.crypto.Mac.getInstance("HmacSHA256")
        mac.init(javax.crypto.spec.SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }
    
    override fun aesGcmEncrypt(
        key: ByteArray,
        iv: ByteArray,
        aad: ByteArray,
        plaintext: ByteArray
    ): ByteArray {
        require(key.size == 32) { "Key must be 32 bytes" }
        require(iv.size == 12) { "IV must be 12 bytes" }
        
        val secretKey = javax.crypto.spec.SecretKeySpec(key, "AES")
        val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
        val gcmSpec = javax.crypto.spec.GCMParameterSpec(128, iv)
        
        cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, secretKey, gcmSpec)
        cipher.updateAAD(aad)
        
        return cipher.doFinal(plaintext)
    }
    
    override fun aesGcmDecrypt(
        key: ByteArray,
        iv: ByteArray,
        aad: ByteArray,
        ciphertext: ByteArray
    ): ByteArray {
        require(key.size == 32) { "Key must be 32 bytes" }
        require(iv.size == 12) { "IV must be 12 bytes" }
        
        val secretKey = javax.crypto.spec.SecretKeySpec(key, "AES")
        val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
        val gcmSpec = javax.crypto.spec.GCMParameterSpec(128, iv)
        
        cipher.init(javax.crypto.Cipher.DECRYPT_MODE, secretKey, gcmSpec)
        cipher.updateAAD(aad)
        
        return cipher.doFinal(ciphertext)
    }
}
