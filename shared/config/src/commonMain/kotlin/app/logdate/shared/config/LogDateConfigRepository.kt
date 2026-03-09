package app.logdate.shared.config

import app.logdate.shared.model.ServerDescriptor
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
    val localServerAddress: StateFlow<String>
    val serverDescriptor: StateFlow<ServerDescriptor?>

    suspend fun updateBackendUrl(url: String)

    suspend fun updateApiVersion(version: String)

    suspend fun updateLocalServerAddress(address: String)

    suspend fun updateServerDescriptor(descriptor: ServerDescriptor?)

    suspend fun resetToDefaults()

    fun getCurrentBackendUrl(): String

    fun getCurrentApiBaseUrl(): String

    fun getCurrentServerDescriptor(): ServerDescriptor?
}

/**
 * Default implementation of LogDateConfigRepository
 */
class DefaultLogDateConfigRepository(
    initialBackendUrl: String = DEFAULT_BACKEND_URL,
    initialApiVersion: String = DEFAULT_API_VERSION,
    initialLocalServerAddress: String = DEFAULT_LOCAL_SERVER_ADDRESS,
) : LogDateConfigRepository {
    companion object {
        const val DEFAULT_BACKEND_URL = "https://cloud.logdate.app"
        const val DEFAULT_API_VERSION = "v1"
        const val DEFAULT_LOCAL_SERVER_ADDRESS = "localhost:8765"
    }

    private val _backendUrl = MutableStateFlow(initialBackendUrl)
    private val _apiVersion = MutableStateFlow(initialApiVersion)
    private val _localServerAddress = MutableStateFlow(initialLocalServerAddress)
    private val _serverDescriptor = MutableStateFlow<ServerDescriptor?>(null)

    override val backendUrl: StateFlow<String> = _backendUrl.asStateFlow()
    override val apiVersion: StateFlow<String> = _apiVersion.asStateFlow()
    override val localServerAddress: StateFlow<String> = _localServerAddress.asStateFlow()
    override val serverDescriptor: StateFlow<ServerDescriptor?> = _serverDescriptor.asStateFlow()

    override val apiBaseUrl: Flow<String> =
        combine(
            backendUrl,
            apiVersion,
        ) { url, version ->
            "${url.trimEnd('/')}/api/$version"
        }

    override suspend fun updateBackendUrl(url: String) {
        val cleanUrl = url.trimEnd('/')
        val normalizedUrl =
            if (cleanUrl.startsWith("http")) {
                cleanUrl
            } else {
                "https://$cleanUrl"
            }
        _backendUrl.value = normalizedUrl
        if (_serverDescriptor.value?.serverOrigin != normalizedUrl) {
            _serverDescriptor.value = null
        }
    }

    override suspend fun updateApiVersion(version: String) {
        _apiVersion.value = version
    }

    override suspend fun updateLocalServerAddress(address: String) {
        _localServerAddress.value = address
    }

    override suspend fun updateServerDescriptor(descriptor: ServerDescriptor?) {
        _serverDescriptor.value = descriptor
    }

    override suspend fun resetToDefaults() {
        _backendUrl.value = DEFAULT_BACKEND_URL
        _apiVersion.value = DEFAULT_API_VERSION
        _localServerAddress.value = DEFAULT_LOCAL_SERVER_ADDRESS
        _serverDescriptor.value = null
    }

    override fun getCurrentBackendUrl(): String = _backendUrl.value

    override fun getCurrentApiBaseUrl(): String = "${getCurrentBackendUrl()}/api/${_apiVersion.value}"

    override fun getCurrentServerDescriptor(): ServerDescriptor? = _serverDescriptor.value
}
