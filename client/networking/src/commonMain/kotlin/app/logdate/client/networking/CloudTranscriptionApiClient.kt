package app.logdate.client.networking

import app.logdate.shared.config.LogDateConfigRepository
import app.logdate.shared.model.transcription.CloudTranscriptionSessionRequest
import app.logdate.shared.model.transcription.CloudTranscriptionSessionResponse
import io.github.aakira.napier.Napier
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Reserves LogDate Cloud realtime transcription sessions.
 *
 * The client talks only to LogDate Cloud. The server decides which ASR provider
 * to use and returns a short-lived session lease that can be used for the
 * realtime audio connection.
 */
interface CloudTranscriptionApiClientContract {
    suspend fun createSession(
        accessToken: String,
        request: CloudTranscriptionSessionRequest,
    ): Result<CloudTranscriptionSessionResponse>
}

/**
 * Ktor-backed implementation of [CloudTranscriptionApiClientContract].
 */
class CloudTranscriptionApiClient(
    private val httpClient: HttpClient,
    private val configRepository: LogDateConfigRepository,
    private val json: Json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = false
        },
) : CloudTranscriptionApiClientContract {
    override suspend fun createSession(
        accessToken: String,
        request: CloudTranscriptionSessionRequest,
    ): Result<CloudTranscriptionSessionResponse> =
        runCatching {
            val baseUrl = configRepository.apiBaseUrl.first()
            val response =
                httpClient.post("$baseUrl/transcription/sessions") {
                    header(HttpHeaders.Authorization, "Bearer $accessToken")
                    contentType(ContentType.Application.Json)
                    setBody(json.encodeToString(request))
                }
            val body = response.bodyAsText()
            if (!response.status.isSuccess()) {
                throw CloudTranscriptionApiException("Cloud transcription session request failed: ${response.status} $body")
            }
            json.decodeFromString<CloudTranscriptionSessionResponse>(body)
        }.onFailure { error ->
            Napier.w("Failed to create Cloud transcription session", error)
        }
}

/**
 * Failure raised when LogDate Cloud declines or cannot create a transcription session.
 */
class CloudTranscriptionApiException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
