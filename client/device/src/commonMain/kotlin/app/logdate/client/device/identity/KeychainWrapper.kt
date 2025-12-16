package app.logdate.client.device.identity

/**
 * Interface for platform-specific keychain operations.
 * This allows for secure storage of credentials and sensitive data.
 */
interface KeychainWrapper {
    /**
     * Gets a string value from the keychain.
     *
     * @param key The key to retrieve
     * @return The stored string, or null if not found
     */
    fun getString(key: String): String?
    
    /**
     * Stores a string value in the keychain.
     *
     * @param key The key to store under
     * @param value The value to store
     * @return True if storage was successful
     */
    suspend fun set(value: String, key: String): Boolean
    
    /**
     * Removes a value from the keychain.
     *
     * @param key The key to remove
     * @return True if removal was successful
     */
    suspend fun remove(key: String): Boolean
}