package app.logdate.client.datastore

import kotlinx.coroutines.flow.Flow

/**
 * A general-purpose key-value storage interface that works across all platforms.
 * 
 * This provides a simple, consistent API for storing and retrieving persistent
 * data as key-value pairs. Implementations should handle the platform-specific
 * storage mechanisms transparently.
 * 
 * Use this storage for:
 * - Application settings and preferences
 * - Device-specific configuration
 * - User preferences
 * - Cached data that should persist across app restarts
 * 
 * The storage is:
 * - Persistent: Data survives app restarts and updates
 * - Type-safe: Basic types are handled correctly
 * - Reactive: Changes can be observed using Flow
 */
interface KeyValueStorage {
    /**
     * Retrieves a string value for the given key.
     *
     * @param key The key to retrieve the value for
     * @return The stored string value, or null if not found
     */
    suspend fun getString(key: String): String?
    
    /**
     * Retrieves a string value for the given key synchronously.
     * This is a convenience method for platforms that don't support coroutines well.
     *
     * @param key The key to retrieve the value for
     * @return The stored string value, or null if not found
     */
    fun getStringSync(key: String): String?
    
    /**
     * Stores a string value for the given key.
     *
     * @param key The key to store the value under
     * @param value The string value to store
     */
    suspend fun putString(key: String, value: String)
    
    /**
     * Retrieves a boolean value for the given key.
     *
     * @param key The key to retrieve the value for
     * @param defaultValue The value to return if the key is not found
     * @return The stored boolean value, or defaultValue if not found
     */
    suspend fun getBoolean(key: String, defaultValue: Boolean = false): Boolean
    
    /**
     * Stores a boolean value for the given key.
     *
     * @param key The key to store the value under
     * @param value The boolean value to store
     */
    suspend fun putBoolean(key: String, value: Boolean)
    
    /**
     * Retrieves an integer value for the given key.
     *
     * @param key The key to retrieve the value for
     * @param defaultValue The value to return if the key is not found
     * @return The stored integer value, or defaultValue if not found
     */
    suspend fun getInt(key: String, defaultValue: Int = 0): Int
    
    /**
     * Stores an integer value for the given key.
     *
     * @param key The key to store the value under
     * @param value The integer value to store
     */
    suspend fun putInt(key: String, value: Int)
    
    /**
     * Retrieves a long value for the given key.
     *
     * @param key The key to retrieve the value for
     * @param defaultValue The value to return if the key is not found
     * @return The stored long value, or defaultValue if not found
     */
    suspend fun getLong(key: String, defaultValue: Long = 0L): Long
    
    /**
     * Stores a long value for the given key.
     *
     * @param key The key to store the value under
     * @param value The long value to store
     */
    suspend fun putLong(key: String, value: Long)
    
    /**
     * Retrieves a float value for the given key.
     *
     * @param key The key to retrieve the value for
     * @param defaultValue The value to return if the key is not found
     * @return The stored float value, or defaultValue if not found
     */
    suspend fun getFloat(key: String, defaultValue: Float = 0f): Float
    
    /**
     * Stores a float value for the given key.
     *
     * @param key The key to store the value under
     * @param value The float value to store
     */
    suspend fun putFloat(key: String, value: Float)
    
    /**
     * Removes the value for the given key.
     *
     * @param key The key to remove
     */
    suspend fun remove(key: String)
    
    /**
     * Checks if the storage contains a value for the given key.
     *
     * @param key The key to check
     * @return True if the key exists, false otherwise
     */
    suspend fun contains(key: String): Boolean
    
    /**
     * Clears all values from this storage.
     */
    suspend fun clear()
    
    /**
     * Gets a flow of changes to the string value for the given key.
     * 
     * @param key The key to observe changes for
     * @return A flow of string values, or null when the value is removed
     */
    fun observeString(key: String): Flow<String?>
    
    /**
     * Gets a flow of changes to the boolean value for the given key.
     * 
     * @param key The key to observe changes for
     * @param defaultValue The value to emit if the key is not found
     * @return A flow of boolean values
     */
    fun observeBoolean(key: String, defaultValue: Boolean = false): Flow<Boolean>
    
    /**
     * Gets a flow of changes to the integer value for the given key.
     * 
     * @param key The key to observe changes for
     * @param defaultValue The value to emit if the key is not found
     * @return A flow of integer values
     */
    fun observeInt(key: String, defaultValue: Int = 0): Flow<Int>
    
    /**
     * Gets a flow of changes to the long value for the given key.
     * 
     * @param key The key to observe changes for
     * @param defaultValue The value to emit if the key is not found
     * @return A flow of long values
     */
    fun observeLong(key: String, defaultValue: Long = 0L): Flow<Long>
    
    /**
     * Gets a flow of changes to the float value for the given key.
     * 
     * @param key The key to observe changes for
     * @param defaultValue The value to emit if the key is not found
     * @return A flow of float values
     */
    fun observeFloat(key: String, defaultValue: Float = 0f): Flow<Float>
}