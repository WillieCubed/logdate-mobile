package app.logdate.feature.core.settings.ui

import app.logdate.client.networking.ServerDiscoveryClient
import app.logdate.client.networking.ServerHealthChecker
import app.logdate.shared.config.DefaultLogDateConfigRepository
import app.logdate.shared.config.LogDateConfigRepository
import app.logdate.shared.model.ServerCapability
import app.logdate.shared.model.ServerDescriptor
import app.logdate.shared.model.ServerProtocolFeature
import io.github.aakira.napier.Napier

/** Why a server can't hold a LogDate account for this device. */
enum class ServerProblem {
    /** What was typed isn't a web address. */
    INVALID_ADDRESS,

    /** Nothing answered at the address. */
    UNREACHABLE,

    /** Something answered, but it isn't a LogDate server. */
    NOT_LOGDATE,

    /** A LogDate server, but without passkey sign-in, sync, or single-identity accounts. */
    OUT_OF_DATE,

    /** The server's passkeys are for a domain this device's platform won't create passkeys for. */
    PASSKEYS_UNAVAILABLE_HERE,
}

/** A server that answered and can hold this device's account. */
data class CheckedServer(
    val origin: String,
    val descriptor: ServerDescriptor,
    val version: String?,
)

sealed interface ServerCheck {
    data class Usable(
        val server: CheckedServer,
    ) : ServerCheck

    data class Unusable(
        val problem: ServerProblem,
    ) : ServerCheck
}

/**
 * @param passkeysWorkWith whether this device's platform can create passkeys for a relying party
 *   ID. iOS only creates them for domains the app is associated with.
 */
class ServerConfigurationCoordinator(
    private val serverHealthChecker: ServerHealthChecker,
    private val serverDiscoveryClient: ServerDiscoveryClient,
    private val configRepository: LogDateConfigRepository,
    private val passkeysWorkWith: (rpId: String) -> Boolean = { true },
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

    /**
     * Points the app at LogDate Cloud, failing if the server cannot be reached.
     *
     * Discovery failure used to be swallowed, so an unreachable server still reported success and
     * the flow carried on with no descriptor — leaving someone to fill in a name and a handle
     * before anything told them the server was down. Surfacing it here means they find out while
     * they can still choose a different server.
     */
    suspend fun saveLogDateCloudSelection(): Result<SaveResult> {
        val serverOrigin = DefaultLogDateConfigRepository.DEFAULT_BACKEND_URL
        val descriptor =
            serverDiscoveryClient.discoverServer(serverOrigin).getOrElse { error ->
                return Result.failure(error)
            }
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
        if (!descriptor.hasProtocolFeature(ServerProtocolFeature.CANONICAL_OWNER_BINDING_V1)) {
            return Result.failure(IllegalArgumentException("Server does not support LogDate's single-identity protocol"))
        }
        saveServerConfiguration(normalizedOrigin, descriptor)
        return Result.success(
            SaveResult(
                serverOrigin = normalizedOrigin,
                descriptor = descriptor,
                serverVersion = healthInfo.version,
            ),
        )
    }

    /**
     * Asks the server at [address] whether it can hold this device's account. Changes nothing; see
     * [validateAndSaveCustomServer] to switch to it.
     */
    suspend fun check(address: String): ServerCheck {
        val origin = normalizeOrigin(address).takeIf(::isWebAddress) ?: return ServerCheck.Unusable(ServerProblem.INVALID_ADDRESS)
        val health =
            serverHealthChecker.checkServerHealth(origin).getOrElse { error ->
                Napier.i("$origin did not answer: ${error.message}")
                return ServerCheck.Unusable(ServerProblem.UNREACHABLE)
            }
        val descriptor =
            serverDiscoveryClient.discoverServer(origin).getOrElse { error ->
                Napier.i("$origin answered but did not describe itself as a LogDate server: ${error.message}")
                return ServerCheck.Unusable(ServerProblem.NOT_LOGDATE)
            }
        val supportsAccounts =
            descriptor.hasProtocolFeature(ServerProtocolFeature.CANONICAL_OWNER_BINDING_V1) &&
                REQUIRED_CAPABILITIES.all(descriptor::hasCapability)
        if (!supportsAccounts) return ServerCheck.Unusable(ServerProblem.OUT_OF_DATE)
        val rpId = descriptor.passkey?.rpId
        if (rpId == null || !passkeysWorkWith(rpId)) return ServerCheck.Unusable(ServerProblem.PASSKEYS_UNAVAILABLE_HERE)
        return ServerCheck.Usable(CheckedServer(origin = origin, descriptor = descriptor, version = health.version))
    }

    private fun isWebAddress(origin: String): Boolean {
        val host = origin.substringAfter("://").substringBefore('/').substringBefore(':')
        return host.isNotBlank() && host.none { it.isWhitespace() } && (host.contains('.') || host == "localhost")
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

    private companion object {
        val REQUIRED_CAPABILITIES =
            listOf(ServerCapability.AUTH_PASSKEY, ServerCapability.SYNC_CONTENT, ServerCapability.SYNC_MEDIA)
    }
}
