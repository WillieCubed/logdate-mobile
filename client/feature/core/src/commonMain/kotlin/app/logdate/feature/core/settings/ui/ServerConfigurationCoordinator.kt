package app.logdate.feature.core.settings.ui

import app.logdate.client.networking.ServerDiscoveryClient
import app.logdate.client.networking.ServerHealthChecker
import app.logdate.shared.config.DefaultLogDateConfigRepository
import app.logdate.shared.config.LogDateConfigRepository
import app.logdate.shared.model.ServerDescriptor

class ServerConfigurationCoordinator(
    private val serverHealthChecker: ServerHealthChecker,
    private val serverDiscoveryClient: ServerDiscoveryClient,
    private val configRepository: LogDateConfigRepository,
) {
    data class SaveResult(
        val serverOrigin: String,
        val descriptor: ServerDescriptor?,
        val serverVersion: String? = null,
    )

    fun initialSelectionState(): ServerSelectionState {
        val backendUrl = configRepository.getCurrentBackendUrl()
        val descriptor = configRepository.getCurrentServerDescriptor()
        val isLogDateCloud = backendUrl == DefaultLogDateConfigRepository.DEFAULT_BACKEND_URL
        return ServerSelectionState(
            selectedPreset = if (isLogDateCloud) ServerPreset.PRODUCTION else ServerPreset.CUSTOM,
            customServerUrl = if (isLogDateCloud) "" else backendUrl,
            activeServerDescriptor = descriptor,
        )
    }

    suspend fun saveLogDateCloudSelection(): Result<SaveResult> {
        val serverOrigin = DefaultLogDateConfigRepository.DEFAULT_BACKEND_URL
        val descriptor = serverDiscoveryClient.discoverServer(serverOrigin).getOrNull()
        saveServerConfiguration(serverOrigin, descriptor)
        return Result.success(
            SaveResult(
                serverOrigin = serverOrigin,
                descriptor = descriptor,
            ),
        )
    }

    suspend fun validateAndSaveCustomServer(serverOrigin: String): Result<SaveResult> {
        val normalizedOrigin = normalizeOrigin(serverOrigin)
        val healthInfo = serverHealthChecker.checkServerHealth(normalizedOrigin).getOrElse { error -> return Result.failure(error) }
        val descriptor = serverDiscoveryClient.discoverServer(normalizedOrigin).getOrElse { error -> return Result.failure(error) }
        saveServerConfiguration(normalizedOrigin, descriptor)
        return Result.success(
            SaveResult(
                serverOrigin = normalizedOrigin,
                descriptor = descriptor,
                serverVersion = healthInfo.version,
            ),
        )
    }

    fun normalizeOrigin(serverOrigin: String): String {
        val trimmed = serverOrigin.trim().trimEnd('/')
        return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed
        } else {
            "https://$trimmed"
        }
    }

    private suspend fun saveServerConfiguration(
        serverOrigin: String,
        descriptor: ServerDescriptor?,
    ) {
        configRepository.updateBackendUrl(serverOrigin)
        configRepository.updateServerDescriptor(descriptor)
    }
}
