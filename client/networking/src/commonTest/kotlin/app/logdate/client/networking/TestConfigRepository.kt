package app.logdate.client.networking

import app.logdate.shared.config.LogDateConfigRepository
import app.logdate.shared.model.ServerDescriptor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

internal class TestConfigRepository : LogDateConfigRepository {
    private val backendUrlState = MutableStateFlow("http://localhost")
    private val apiVersionState = MutableStateFlow("v1")
    private val apiBaseUrlState = MutableStateFlow("http://localhost/api/v1")
    private val localServerAddressState = MutableStateFlow("localhost:8765")
    private val serverDescriptorState = MutableStateFlow<ServerDescriptor?>(null)

    override val backendUrl: StateFlow<String> = backendUrlState
    override val apiVersion: StateFlow<String> = apiVersionState
    override val apiBaseUrl: Flow<String> = apiBaseUrlState
    override val localServerAddress: StateFlow<String> = localServerAddressState
    override val serverDescriptor: StateFlow<ServerDescriptor?> = serverDescriptorState

    override suspend fun updateBackendUrl(url: String) {
        backendUrlState.value = url
        apiBaseUrlState.value = "${url.trimEnd('/')}/api/${apiVersionState.value}"
    }

    override suspend fun updateApiVersion(version: String) {
        apiVersionState.value = version
        apiBaseUrlState.value = "${backendUrlState.value.trimEnd('/')}/api/$version"
    }

    override suspend fun updateLocalServerAddress(address: String) {
        localServerAddressState.value = address
    }

    override suspend fun updateServerDescriptor(descriptor: ServerDescriptor?) {
        serverDescriptorState.value = descriptor
    }

    override suspend fun resetToDefaults() {
        backendUrlState.value = "http://localhost"
        apiVersionState.value = "v1"
        apiBaseUrlState.value = "http://localhost/api/v1"
        localServerAddressState.value = "localhost:8765"
        serverDescriptorState.value = null
    }

    override fun getCurrentBackendUrl(): String = backendUrlState.value

    override fun getCurrentApiBaseUrl(): String = apiBaseUrlState.value

    override fun getCurrentServerDescriptor(): ServerDescriptor? = serverDescriptorState.value
}
