package app.logdate.shared.config

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine

/**
 * Repository for managing LogDate configuration that can be updated at runtime
 */
interface LogDateConfigRepository {
    val backendUrl: StateFlow<String>
    val apiVersion: StateFlow<String>
    val apiBaseUrl: Flow<String>
    
    suspend fun updateBackendUrl(url: String)
    suspend fun updateApiVersion(version: String)
    suspend fun resetToDefaults()
    
    fun getCurrentBackendUrl(): String
    fun getCurrentApiBaseUrl(): String
}

/**
 * Default implementation of LogDateConfigRepository
 */
class DefaultLogDateConfigRepository(
    initialBackendUrl: String = DEFAULT_BACKEND_URL,
    initialApiVersion: String = DEFAULT_API_VERSION
) : LogDateConfigRepository {
    
    companion object {
        const val DEFAULT_BACKEND_URL = "https://cloud.logdate.app"
        const val DEFAULT_API_VERSION = "v1"
    }
    
    private val _backendUrl = MutableStateFlow(initialBackendUrl)
    private val _apiVersion = MutableStateFlow(initialApiVersion)
    
    override val backendUrl: StateFlow<String> = _backendUrl.asStateFlow()
    override val apiVersion: StateFlow<String> = _apiVersion.asStateFlow()
    
    override val apiBaseUrl: Flow<String> = combine(
        backendUrl,
        apiVersion
    ) { url, version ->
        "${url.trimEnd('/')}/api/$version"
    }
    
    override suspend fun updateBackendUrl(url: String) {
        val cleanUrl = url.trimEnd('/')
        _backendUrl.value = if (cleanUrl.startsWith("http")) {
            cleanUrl
        } else {
            "https://$cleanUrl"
        }
    }
    
    override suspend fun updateApiVersion(version: String) {
        _apiVersion.value = version
    }
    
    override suspend fun resetToDefaults() {
        _backendUrl.value = DEFAULT_BACKEND_URL
        _apiVersion.value = DEFAULT_API_VERSION
    }
    
    override fun getCurrentBackendUrl(): String = _backendUrl.value
    
    override fun getCurrentApiBaseUrl(): String = "${getCurrentBackendUrl()}/api/${_apiVersion.value}"
}