package app.logdate.feature.core.settings.account

import app.logdate.client.networking.ServerDiscoveryClient
import app.logdate.client.networking.ServerHealthChecker
import app.logdate.shared.config.DefaultLogDateConfigRepository
import app.logdate.shared.config.LogDateConfigRepository
import app.logdate.shared.model.ServerProtocolFeature
import io.github.aakira.napier.Napier
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * The server this device's account lives on.
 *
 * @property displayName the name the server gives itself, or `null` before it has been asked
 * @property publishesIdentityChanges whether AT Protocol identity changes made here reach the PLC
 *   directory, so identity tools that depend on that can be offered
 */
data class ConnectedServerInfo(
    val origin: String,
    val displayName: String?,
    val isLogDateCloud: Boolean,
    val publishesIdentityChanges: Boolean,
) {
    /** The origin without its scheme, as a person would type it. */
    val host: String get() = origin.substringAfter("://").trimEnd('/')
}

/** Whether the connected server answered the last time it was asked. */
sealed interface ServerHealth {
    data class Reachable(
        val version: String?,
    ) : ServerHealth

    data object Unreachable : ServerHealth
}

/** Reads the connected server and asks it for an up-to-date description of itself. */
interface ConnectedServer {
    val info: Flow<ConnectedServerInfo>

    /**
     * Checks that the server answers and refreshes what it says it supports. The saved description
     * is otherwise only fetched when a server is chosen, so capabilities added later would never
     * reach this device.
     */
    suspend fun refresh(): ServerHealth
}

class DefaultConnectedServer(
    private val configRepository: LogDateConfigRepository,
    private val healthChecker: ServerHealthChecker,
    private val discoveryClient: ServerDiscoveryClient,
) : ConnectedServer {
    override val info: Flow<ConnectedServerInfo> =
        combine(configRepository.backendUrl, configRepository.serverDescriptor) { backendUrl, descriptor ->
            ConnectedServerInfo(
                origin = backendUrl,
                displayName = descriptor?.displayName,
                isLogDateCloud = backendUrl == DefaultLogDateConfigRepository.DEFAULT_BACKEND_URL,
                publishesIdentityChanges =
                    descriptor?.hasProtocolFeature(ServerProtocolFeature.ATPROTO_PLC_PUBLISHING_V1) == true,
            )
        }

    override suspend fun refresh(): ServerHealth =
        coroutineScope {
            val origin = configRepository.getCurrentBackendUrl()
            launch {
                discoveryClient
                    .discoverServer(origin)
                    .onSuccess { descriptor -> configRepository.updateServerDescriptor(descriptor) }
                    .onFailure { error -> Napier.w("Could not refresh the description of $origin", error) }
            }
            healthChecker
                .checkServerHealth(origin)
                .fold(
                    onSuccess = { ServerHealth.Reachable(it.version) },
                    onFailure = { error ->
                        Napier.w("$origin did not answer a health check", error)
                        ServerHealth.Unreachable
                    },
                )
        }
}
