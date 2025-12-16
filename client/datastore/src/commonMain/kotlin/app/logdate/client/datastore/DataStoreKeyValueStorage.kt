package app.logdate.client.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Implementation of KeyValueStorage using DataStore Preferences.
 * This works on all platforms supported by DataStore (Android, iOS, Desktop).
 */
class DataStoreKeyValueStorage(
    private val dataStore: DataStore<Preferences>
) : KeyValueStorage {
    
    override suspend fun getString(key: String): String? {
        val prefKey = stringPreferencesKey(key)
        return try {
            val preferences = dataStore.data.first()
            preferences[prefKey]
        } catch (e: Exception) {
            Napier.e("Failed to get string for key: $key", e)
            null
        }
    }
    
    override fun getStringSync(key: String): String? {
        // In the common implementation, we can't actually provide a truly sync implementation
        // So we return a default value. Platform-specific implementations should override this.
        Napier.w("getStringSync is not properly implemented for this platform. Returning null.")
        return null
    }
    
    override suspend fun putString(key: String, value: String) {
        val prefKey = stringPreferencesKey(key)
        try {
            dataStore.edit { preferences ->
                preferences[prefKey] = value
            }
        } catch (e: Exception) {
            Napier.e("Failed to put string for key: $key", e)
        }
    }
    
    override suspend fun getBoolean(key: String, defaultValue: Boolean): Boolean {
        val prefKey = booleanPreferencesKey(key)
        return try {
            val preferences = dataStore.data.first()
            preferences[prefKey] ?: defaultValue
        } catch (e: Exception) {
            Napier.e("Failed to get boolean for key: $key", e)
            defaultValue
        }
    }
    
    override suspend fun putBoolean(key: String, value: Boolean) {
        val prefKey = booleanPreferencesKey(key)
        try {
            dataStore.edit { preferences ->
                preferences[prefKey] = value
            }
        } catch (e: Exception) {
            Napier.e("Failed to put boolean for key: $key", e)
        }
    }
    
    override suspend fun getInt(key: String, defaultValue: Int): Int {
        val prefKey = intPreferencesKey(key)
        return try {
            val preferences = dataStore.data.first()
            preferences[prefKey] ?: defaultValue
        } catch (e: Exception) {
            Napier.e("Failed to get int for key: $key", e)
            defaultValue
        }
    }
    
    override suspend fun putInt(key: String, value: Int) {
        val prefKey = intPreferencesKey(key)
        try {
            dataStore.edit { preferences ->
                preferences[prefKey] = value
            }
        } catch (e: Exception) {
            Napier.e("Failed to put int for key: $key", e)
        }
    }
    
    override suspend fun getLong(key: String, defaultValue: Long): Long {
        val prefKey = longPreferencesKey(key)
        return try {
            val preferences = dataStore.data.first()
            preferences[prefKey] ?: defaultValue
        } catch (e: Exception) {
            Napier.e("Failed to get long for key: $key", e)
            defaultValue
        }
    }
    
    override suspend fun putLong(key: String, value: Long) {
        val prefKey = longPreferencesKey(key)
        try {
            dataStore.edit { preferences ->
                preferences[prefKey] = value
            }
        } catch (e: Exception) {
            Napier.e("Failed to put long for key: $key", e)
        }
    }
    
    override suspend fun getFloat(key: String, defaultValue: Float): Float {
        val prefKey = floatPreferencesKey(key)
        return try {
            val preferences = dataStore.data.first()
            preferences[prefKey] ?: defaultValue
        } catch (e: Exception) {
            Napier.e("Failed to get float for key: $key", e)
            defaultValue
        }
    }
    
    override suspend fun putFloat(key: String, value: Float) {
        val prefKey = floatPreferencesKey(key)
        try {
            dataStore.edit { preferences ->
                preferences[prefKey] = value
            }
        } catch (e: Exception) {
            Napier.e("Failed to put float for key: $key", e)
        }
    }
    
    override suspend fun remove(key: String) {
        try {
            dataStore.edit { preferences ->
                // Find and remove the key regardless of its type
                val keysToRemove = preferences.asMap().keys
                    .filter { it.name == key }
                
                keysToRemove.forEach { preferences.remove(it) }
            }
        } catch (e: Exception) {
            Napier.e("Failed to remove key: $key", e)
        }
    }
    
    override suspend fun contains(key: String): Boolean {
        return try {
            val preferences = dataStore.data.first()
            preferences.asMap().keys.any { it.name == key }
        } catch (e: Exception) {
            Napier.e("Failed to check if contains key: $key", e)
            false
        }
    }
    
    override suspend fun clear() {
        try {
            dataStore.edit { preferences ->
                preferences.clear()
            }
        } catch (e: Exception) {
            Napier.e("Failed to clear preferences", e)
        }
    }
    
    override fun observeString(key: String): Flow<String?> {
        val prefKey = stringPreferencesKey(key)
        return dataStore.data
            .catch { e -> 
                Napier.e("Error reading string for key: $key", e)
                emit(emptyPreferences())
            }
            .map { preferences ->
                preferences[prefKey]
            }
    }
    
    override fun observeBoolean(key: String, defaultValue: Boolean): Flow<Boolean> {
        val prefKey = booleanPreferencesKey(key)
        return dataStore.data
            .catch { e -> 
                Napier.e("Error reading boolean for key: $key", e)
                emit(emptyPreferences())
            }
            .map { preferences ->
                preferences[prefKey] ?: defaultValue
            }
    }
    
    override fun observeInt(key: String, defaultValue: Int): Flow<Int> {
        val prefKey = intPreferencesKey(key)
        return dataStore.data
            .catch { e -> 
                Napier.e("Error reading int for key: $key", e)
                emit(emptyPreferences())
            }
            .map { preferences ->
                preferences[prefKey] ?: defaultValue
            }
    }
    
    override fun observeLong(key: String, defaultValue: Long): Flow<Long> {
        val prefKey = longPreferencesKey(key)
        return dataStore.data
            .catch { e -> 
                Napier.e("Error reading long for key: $key", e)
                emit(emptyPreferences())
            }
            .map { preferences ->
                preferences[prefKey] ?: defaultValue
            }
    }
    
    override fun observeFloat(key: String, defaultValue: Float): Flow<Float> {
        val prefKey = floatPreferencesKey(key)
        return dataStore.data
            .catch { e -> 
                Napier.e("Error reading float for key: $key", e)
                emit(emptyPreferences())
            }
            .map { preferences ->
                preferences[prefKey] ?: defaultValue
            }
    }
}