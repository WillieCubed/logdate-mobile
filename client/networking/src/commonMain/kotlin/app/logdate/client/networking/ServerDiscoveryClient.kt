package app.logdate.client.networking

import app.logdate.shared.model.ServerDescriptor
import app.logdate.shared.model.ServerInfoResponse
import io.github.aakira.napier.Napier
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json

interface ServerDiscoveryClient {
    suspend fun discoverServer(serverOrigin: String): Result<ServerDescriptor>
}

class DefaultServerDiscoveryClient(
    private val httpClient: HttpClient,
) : ServerDiscoveryClient {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun discoverServer(serverOrigin: String): Result<ServerDescriptor> =
        try {
            val normalizedOrigin = normalizeOrigin(serverOrigin)
            val response = httpClient.get("$normalizedOrigin/api/v1/server/info")
            val payload = json.decodeFromString<ServerInfoResponse>(response.bodyAsText())
            Result.success(payload.data)
        } catch (e: Exception) {
            Napier.e("Server discovery failed", e)
            Result.failure(ServerDiscoveryException("Failed to discover server: ${e.message}", e))
        }

    private fun normalizeOrigin(serverOrigin: String): String {
        val trimmed = serverOrigin.trim().trimEnd('/')
        return if (trimmed.startsWith("http")) trimmed else "https://$trimmed"
    }
}

class ServerDiscoveryException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
