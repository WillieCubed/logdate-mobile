package app.logdate.client.device.storage

import kotlinx.coroutines.flow.Flow

/**
 * Storage interface specifically for device identity and device-specific configuration data.
 * 
 * This storage is:
 * - Device-specific: Data stored here is tied to the physical device
 * - Persistent: Data survives app restarts and updates
 * - Not user-specific: This storage is independent of user accounts
 * - Not backed up: Data here is not intended to be transferred between devices
 * 
 * Use this storage only for:
 * - Device identification data
 * - Device-specific settings and configuration
 * - Local device security information
 * 
 * Do NOT use for:
 * - User preferences (use app.logdate.client.datastore.LogdatePreferencesDataSource)
 * - App-wide settings (use app.logdate.client.datastore)
 * - User content or data that should sync between devices
 */
interface DeviceIdentityStorage {
    /**
     * Retrieves a string value for the given key.
     *
     * @param key The key to retrieve the value for
     * @return The stored string value, or null if not found
     */
    suspend fun getString(key: String): String?
    
    /**
     * Stores a string value for the given key.
     *
     * @param key The key to store the value under
     * @param value The string value to store
     */
    suspend fun putString(key: String, value: String)
    
    /**
     * Removes the value for the given key.
     *
     * @param key The key to remove
     */
    suspend fun remove(key: String)
    
    /**
     * Clears all device identity values from this storage.
     * Use with caution as this will reset device identity.
     */
    suspend fun clear()
    
    /**
     * Gets a flow of changes to the string value for the given key.
     * 
     * @param key The key to observe changes for
     * @return A flow of string values, or null when the value is removed
     */
    fun observeString(key: String): Flow<String?>
}