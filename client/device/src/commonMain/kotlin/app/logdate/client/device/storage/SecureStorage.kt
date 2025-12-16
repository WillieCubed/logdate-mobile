package app.logdate.client.device.storage

import kotlinx.coroutines.flow.Flow

/**
 * Interface for secure storage operations.
 * Implementations should securely store and retrieve sensitive data.
 */
interface SecureStorage {
    /**
     * Gets a string value from secure storage.
     *
     * @param key The key to retrieve
     * @return The stored string, or null if not found
     */
    suspend fun getString(key: String): String?
    
    /**
     * Stores a string value in secure storage.
     *
     * @param key The key to store under
     * @param value The value to store
     */
    suspend fun putString(key: String, value: String)
    
    /**
     * Removes a value from secure storage.
     *
     * @param key The key to remove
     */
    suspend fun remove(key: String)
    
    /**
     * Clears all values from secure storage.
     */
    suspend fun clear()
    
    /**
     * Observes changes to a specific key in secure storage.
     *
     * @param key The key to observe
     * @return Flow emitting the current value and updates
     */
    fun observeString(key: String): Flow<String?>
    
    /**
     * Observes all values in secure storage.
     *
     * @return Flow emitting the current map of all values and updates
     */
    fun observeAll(): Flow<Map<String, String>>
    
    /**
     * Encrypts data using a secure algorithm.
     *
     * @param data The data to encrypt
     * @return The encrypted data
     */
    suspend fun encrypt(data: ByteArray): ByteArray
    
    /**
     * Decrypts previously encrypted data.
     *
     * @param data The data to decrypt
     * @return The decrypted data, or null if decryption fails
     */
    suspend fun decrypt(data: ByteArray): ByteArray?
}