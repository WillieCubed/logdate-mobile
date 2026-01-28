package app.logdate.client.device.crypto

import okio.Sink
import okio.Source

/**
 * Provides platform-specific cryptographic primitives for the Data Sovereignty architecture.
 *
 * This interface abstracts the low-level security hardware (e.g., Android Keystore, Secure Enclave)
 * to enable zero-knowledge encryption where only the user's recovery phrase can access the data.
 */
expect class CryptoManager() {
    /**
     * Generates a new 12-word recovery phrase according to the BIP-39 standard.
     */
    suspend fun generateRecoveryPhrase(): List<String>

    /**
     * Derives a cryptographic master key from a BIP-39 recovery phrase.
     * 
     * @param phrase The 12-word recovery phrase held by the user.
     * @return A derived secret key suitable for AES-256 operations.
     */
    suspend fun deriveMasterKey(phrase: List<String>): ByteArray

    /**
     * Validates whether a given list of words constitutes a valid BIP-39 mnemonic.
     */
    fun validateRecoveryPhrase(phrase: List<String>): Boolean

    /**
     * Creates an encrypting [Sink] that wraps an underlying sink.
     *
     * Implementation must use AES-GCM to provide both confidentiality and integrity verification.
     * 
     * @param sink The target sink for ciphertext.
     * @param key The AES-256 secret key.
     * @param iv The 12-byte initialization vector.
     */
    fun encryptSink(sink: Sink, key: ByteArray, iv: ByteArray): Sink

    /**
     * Creates a decrypting [Source] that wraps an underlying source.
     *
     * Decodes ciphertext using AES-GCM. 
     *
     * @param source The origin source of ciphertext.
     * @param key The AES-256 secret key.
     * @param iv The 12-byte initialization vector used during encryption.
     * @throws IOException If the data is tampered with or the key is incorrect (GCM tag mismatch).
     */
    fun decryptSource(source: Source, key: ByteArray, iv: ByteArray): Source
    
    /**
     * Generates a cryptographically secure random sequence of bytes for salts or IVs.
     */
    fun generateRandomBytes(size: Int): ByteArray
    
    /**
     * Computes HMAC-SHA256 of data using the provided key.
     * Used for HKDF and other operations.
     */
    internal fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray
}