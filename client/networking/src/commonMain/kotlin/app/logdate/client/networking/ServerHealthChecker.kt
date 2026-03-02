package app.logdate.client.networking

import io.github.aakira.napier.Napier
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Checks the health status of a LogDate server.
 */
interface ServerHealthChecker {
    /**
     * Performs a health check against the specified server URL.
     *
     * @param baseUrl The base URL of the server to check (e.g., "https://cloud.logdate.app" or "http://localhost:8765")
     * @return Result containing [ServerHealthInfo] on success, or an error on failure
     */
    suspend fun checkServerHealth(baseUrl: String): Result<ServerHealthInfo>
}

/**
 * Information returned from a server health check.
 *
 * @property status The health status (e.g., "healthy")
 * @property version The server version, if available
 */
data class ServerHealthInfo(
    val status: String,
    val version: String?,
)

/**
 * Default implementation of [ServerHealthChecker] using Ktor HTTP client.
 */
class DefaultServerHealthChecker(
    private val httpClient: HttpClient,
) : ServerHealthChecker {
    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

    override suspend fun checkServerHealth(baseUrl: String): Result<ServerHealthInfo> =
        try {
            val cleanUrl = baseUrl.trimEnd('/')
            val healthUrl = "$cleanUrl/health"

            Napier.d("Checking server health at: $healthUrl")

            val response = httpClient.get(healthUrl)
            val body = response.bodyAsText()

            Napier.d("Health check response: $body")

            val healthResponse = json.decodeFromString<HealthCheckResponse>(body)

            if (healthResponse.status == "healthy") {
                Result.success(
                    ServerHealthInfo(
                        status = healthResponse.status,
                        version = healthResponse.version,
                    ),
                )
            } else {
                Result.failure(ServerHealthCheckException("Server reported unhealthy status: ${healthResponse.status}"))
            }
        } catch (e: Exception) {
            Napier.e("Health check failed", e)
            Result.failure(ServerHealthCheckException("Failed to connect to server: ${e.message}", e))
        }
}

@Serializable
private data class HealthCheckResponse(
    val status: String,
    val version: String? = null,
)

/**
 * Exception thrown when a server health check fails.
 */
class ServerHealthCheckException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
