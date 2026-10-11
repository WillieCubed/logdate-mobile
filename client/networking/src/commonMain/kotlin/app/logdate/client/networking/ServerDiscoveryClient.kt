package app.logdate.client.networking

import app.logdate.shared.model.ServerDescriptor
import app.logdate.shared.model.ServerInfoResponse
import io.github.aakira.napier.Napier
import io.ktor.client.HttpClient
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json
import kotlin.coroutines.cancellation.CancellationException

interface ServerDiscoveryClient {
    suspend fun discoverServer(serverOrigin: String): Result<ServerDescriptor>
}

class DefaultServerDiscoveryClient(
    private val httpClient: HttpClient,
) : ServerDiscoveryClient {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun discoverServer(serverOrigin: String): Result<ServerDescriptor> {
        val normalizedOrigin = normalizeOrigin(serverOrigin)
        val response =
            try {
                httpClient.get("$normalizedOrigin/api/v1/server/info") {
                    timeout { requestTimeoutMillis = DISCOVERY_TIMEOUT_MS }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (e.isTimeout()) return failure(DiscoveryFailure.NOT_RESPONDING, "$normalizedOrigin timed out: ${e.message}", e)
                return failure(DiscoveryFailure.UNREACHABLE, "Could not reach $normalizedOrigin: ${e.message}", e)
            }
        if (response.status.value >= 500) {
            return failure(DiscoveryFailure.NOT_RESPONDING, "$normalizedOrigin answered HTTP ${response.status.value}")
        }
        return runCatching { json.decodeFromString<ServerInfoResponse>(response.bodyAsText()).data }
            .fold(
                onSuccess = { Result.success(it) },
                onFailure = { failure(DiscoveryFailure.UNREADABLE, unreadable(normalizedOrigin, response), it) },
            )
    }

    private fun unreadable(
        origin: String,
        response: HttpResponse,
    ) = "$origin answered HTTP ${response.status.value} without LogDate server information"

    private fun failure(
        kind: DiscoveryFailure,
        message: String,
        cause: Throwable? = null,
    ): Result<ServerDescriptor> {
        Napier.e("Server discovery failed: $message", cause)
        return Result.failure(ServerDiscoveryException(kind, message, cause))
    }

    private fun normalizeOrigin(serverOrigin: String): String {
        val trimmed = serverOrigin.trim().trimEnd('/')
        return if (trimmed.startsWith("http")) trimmed else "https://$trimmed"
    }

    private companion object {
        /** Long enough for a server waking from idle; short enough that a hung one gets a clear answer. */
        const val DISCOVERY_TIMEOUT_MS = 30_000L
    }
}

/** Whether [this] is one of Ktor's request or socket timeouts: the server was reached but stalled. */
internal fun Throwable.isTimeout(): Boolean = this is HttpRequestTimeoutException || this is SocketTimeoutException

/** Why a server could not be identified, so the person is told what to do about it. */
enum class DiscoveryFailure {
    /** The request never reached the server: no connection, or nothing at that address. */
    UNREACHABLE,

    /** The server was reached but did not answer in time, or answered with a server error. */
    NOT_RESPONDING,

    /** Something answered, but not with LogDate server information. */
    UNREADABLE,
}

class ServerDiscoveryException(
    val kind: DiscoveryFailure,
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
