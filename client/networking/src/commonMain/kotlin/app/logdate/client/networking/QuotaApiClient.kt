package app.logdate.client.networking

import app.logdate.shared.config.LogDateConfigRepository
import app.logdate.shared.model.QuotaUsage
import io.github.aakira.napier.Napier
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json

/**
 * Reads the calling user's storage-quota state from the server. Mirrors the shape of the
 * other commonMain HTTP clients: the contract returns [Result] so platform implementations
 * can react to network errors without an exception escape hatch.
 */
interface QuotaApiClientContract {
    suspend fun getQuotaUsage(accessToken: String): Result<QuotaUsage>
}

class QuotaApiClient(
    private val httpClient: HttpClient,
    private val configRepository: LogDateConfigRepository,
    private val json: Json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = false
        },
) : QuotaApiClientContract {
    override suspend fun getQuotaUsage(accessToken: String): Result<QuotaUsage> =
        runCatching {
            val baseUrl = configRepository.apiBaseUrl.first()
            val response =
                httpClient.get("$baseUrl/quota") {
                    header("Authorization", "Bearer $accessToken")
                }
            val body = response.bodyAsText()
            if (!response.status.isSuccess()) {
                throw IllegalStateException("Quota request failed: ${response.status} $body")
            }
            json.decodeFromString<QuotaUsage>(body)
        }.onFailure { error ->
            Napier.w("Failed to fetch quota usage", error)
        }
}
