package app.logdate.client.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import app.logdate.shared.config.DefaultLogDateConfigRepository
import app.logdate.shared.config.LogDateConfigRepository
import app.logdate.shared.model.ServerDescriptor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/**
 * Data source for persisting LogDate configuration settings
 */
class LogDateConfigDataSource(
    private val dataStore: DataStore<Preferences>,
    private val configRepository: LogDateConfigRepository,
    private val scope: CoroutineScope,
) {
    companion object {
        private val BACKEND_URL_KEY = stringPreferencesKey("backend_url")
        private val API_VERSION_KEY = stringPreferencesKey("api_version")
        private val SERVER_DESCRIPTOR_KEY = stringPreferencesKey("server_descriptor")
    }

    private val json = Json { ignoreUnknownKeys = true }

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

        scope.launch {
            configRepository.serverDescriptor.collect { descriptor ->
                saveServerDescriptor(descriptor)
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
        val serverDescriptor =
            preferences[SERVER_DESCRIPTOR_KEY]?.let { raw ->
                runCatching { json.decodeFromString<ServerDescriptor>(raw) }.getOrNull()
            }

        configRepository.updateBackendUrl(backendUrl)
        configRepository.updateApiVersion(apiVersion)
        configRepository.updateServerDescriptor(serverDescriptor)
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
     * Save server descriptor to persistent storage.
     */
    private suspend fun saveServerDescriptor(descriptor: ServerDescriptor?) {
        dataStore.edit { preferences ->
            if (descriptor == null) {
                preferences.remove(SERVER_DESCRIPTOR_KEY)
            } else {
                preferences[SERVER_DESCRIPTOR_KEY] = json.encodeToString(descriptor)
            }
        }
    }

    /**
     * Get backend URL flow
     */
    fun getBackendUrl() =
        dataStore.data.map { preferences ->
            preferences[BACKEND_URL_KEY] ?: DefaultLogDateConfigRepository.DEFAULT_BACKEND_URL
        }

    /**
     * Get API version flow
     */
    fun getApiVersion() =
        dataStore.data.map { preferences ->
            preferences[API_VERSION_KEY] ?: DefaultLogDateConfigRepository.DEFAULT_API_VERSION
        }

    /**
     * Get cached server descriptor flow.
     */
    fun getServerDescriptor() =
        dataStore.data.map { preferences ->
            preferences[SERVER_DESCRIPTOR_KEY]?.let { raw ->
                runCatching { json.decodeFromString<ServerDescriptor>(raw) }.getOrNull()
            }
        }
}
