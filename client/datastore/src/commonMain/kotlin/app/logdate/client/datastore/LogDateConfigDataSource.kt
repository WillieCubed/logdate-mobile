package app.logdate.client.datastore

import app.logdate.shared.config.DefaultLogDateConfigRepository
import app.logdate.shared.config.LogDateConfigRepository
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Data source for persisting LogDate configuration settings
 */
class LogDateConfigDataSource(
    private val dataStore: DataStore<Preferences>,
    private val configRepository: LogDateConfigRepository,
    private val scope: CoroutineScope
) {
    
    companion object {
        private val BACKEND_URL_KEY = stringPreferencesKey("backend_url")
        private val API_VERSION_KEY = stringPreferencesKey("api_version")
    }
    
    init {
        // Load configuration on initialization
        scope.launch {
            loadConfiguration()
        }
        
        // Save configuration when it changes
        scope.launch {
            configRepository.backendUrl.collect { url ->
                saveBackendUrl(url)
            }
        }
        
        scope.launch {
            configRepository.apiVersion.collect { version ->
                saveApiVersion(version)
            }
        }
    }
    
    /**
     * Load configuration from persistent storage
     */
    private suspend fun loadConfiguration() {
        val preferences = dataStore.data.first()
        
        val backendUrl = preferences[BACKEND_URL_KEY] ?: DefaultLogDateConfigRepository.DEFAULT_BACKEND_URL
        val apiVersion = preferences[API_VERSION_KEY] ?: DefaultLogDateConfigRepository.DEFAULT_API_VERSION
        
        configRepository.updateBackendUrl(backendUrl)
        configRepository.updateApiVersion(apiVersion)
    }
    
    /**
     * Save backend URL to persistent storage
     */
    private suspend fun saveBackendUrl(url: String) {
        dataStore.edit { preferences ->
            preferences[BACKEND_URL_KEY] = url
        }
    }
    
    /**
     * Save API version to persistent storage
     */
    private suspend fun saveApiVersion(version: String) {
        dataStore.edit { preferences ->
            preferences[API_VERSION_KEY] = version
        }
    }
    
    /**
     * Get backend URL flow
     */
    fun getBackendUrl() = dataStore.data.map { preferences ->
        preferences[BACKEND_URL_KEY] ?: DefaultLogDateConfigRepository.DEFAULT_BACKEND_URL
    }
    
    /**
     * Get API version flow
     */
    fun getApiVersion() = dataStore.data.map { preferences ->
        preferences[API_VERSION_KEY] ?: DefaultLogDateConfigRepository.DEFAULT_API_VERSION
    }
}