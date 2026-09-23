package app.logdate.shared.config

import app.logdate.shared.model.ServerDescriptor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf

/**
 * Configuration fixed to one server, for clients that must talk to that server no matter which one
 * the app is connected to -- such as signing in to the server an account is moving to.
 *
 * It never changes; asking it to is a programming error.
 */
class PinnedLogDateConfigRepository(
    origin: String,
    descriptor: ServerDescriptor?,
    apiVersion: String = DefaultLogDateConfigRepository.DEFAULT_API_VERSION,
) : LogDateConfigRepository {
    private val origin = origin.trimEnd('/')
    private val baseUrl = descriptor?.apiBaseUrl?.trimEnd('/') ?: "${this.origin}/api/$apiVersion"

    override val backendUrl: StateFlow<String> = MutableStateFlow(this.origin).asStateFlow()
    override val apiVersion: StateFlow<String> = MutableStateFlow(apiVersion).asStateFlow()
    override val apiBaseUrl: Flow<String> = flowOf(baseUrl)
    override val localServerAddress: StateFlow<String> =
        MutableStateFlow(DefaultLogDateConfigRepository.DEFAULT_LOCAL_SERVER_ADDRESS).asStateFlow()
    override val serverDescriptor: StateFlow<ServerDescriptor?> = MutableStateFlow(descriptor).asStateFlow()

    override suspend fun updateBackendUrl(url: String) = pinned()

    override suspend fun updateApiVersion(version: String) = pinned()

    override suspend fun updateLocalServerAddress(address: String) = pinned()

    override suspend fun updateServerDescriptor(descriptor: ServerDescriptor?) = pinned()

    override suspend fun resetToDefaults() = pinned()

    override fun getCurrentBackendUrl(): String = origin

    override fun getCurrentApiBaseUrl(): String = baseUrl

    override fun getCurrentServerDescriptor(): ServerDescriptor? = serverDescriptor.value

    private fun pinned(): Nothing = throw UnsupportedOperationException("Configuration pinned to $origin cannot change")
}
