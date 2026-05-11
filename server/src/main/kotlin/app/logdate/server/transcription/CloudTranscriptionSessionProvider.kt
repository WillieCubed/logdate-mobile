package app.logdate.server.transcription

import app.logdate.shared.model.transcription.CloudAudioInputFormat
import app.logdate.shared.model.transcription.CloudTranscriptionSessionRequest
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
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

/**
 * Server-side boundary for minting provider-backed realtime transcription sessions.
 *
 * Implementations must keep provider API keys on the server. Returned credentials are expected to
 * be short-lived and scoped to a single realtime transcription session.
 */
interface CloudTranscriptionSessionProvider {
    /**
     * Reserves a provider session for [request] and returns the client-safe connection metadata.
     *
     * @throws CloudTranscriptionSessionUnavailableException when no provider can currently mint a
     * session. Routes translate this to a fail-closed 503.
     */
    suspend fun reserveSession(
        accountId: UUID,
        request: CloudTranscriptionSessionRequest,
        sessionId: String,
    ): CloudTranscriptionSessionLease
}

/**
 * Client-safe lease returned by a realtime transcription provider.
 */
data class CloudTranscriptionSessionLease(
    val sessionId: String,
    val streamPath: String,
    val inputFormat: CloudAudioInputFormat = CloudAudioInputFormat.PCM16_MONO_24KHZ,
    val realtimeUrl: String? = null,
    val clientSecretValue: String? = null,
    val clientSecretExpiresAtEpochSeconds: Long? = null,
    val modelId: String? = null,
)

/**
 * Raised when LogDate Cloud transcription is configured off or the upstream provider rejects setup.
 */
class CloudTranscriptionSessionUnavailableException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

/**
 * Disabled provider used when production secrets are absent. This keeps unpaid or misconfigured
 * deployments from returning dead stream paths.
 */
class DisabledCloudTranscriptionSessionProvider(
    private val reason: String = "Cloud transcription provider is not configured",
) : CloudTranscriptionSessionProvider {
    override suspend fun reserveSession(
        accountId: UUID,
        request: CloudTranscriptionSessionRequest,
        sessionId: String,
    ): CloudTranscriptionSessionLease = throw CloudTranscriptionSessionUnavailableException(reason)
}

/**
 * Provider that mints OpenAI Realtime transcription ephemeral credentials.
 *
 * The app keeps its own LogDate session id while the upstream ephemeral token lets the client open
 * a realtime transcription socket without ever receiving the server's OpenAI API key.
 */
class OpenAiRealtimeTranscriptionSessionProvider(
    private val apiKey: String,
    private val httpClient: HttpClient,
    private val json: Json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        },
    private val modelId: String = DEFAULT_MODEL,
    private val realtimeUrl: String = OPENAI_REALTIME_TRANSCRIPTION_URL,
) : CloudTranscriptionSessionProvider {
    override suspend fun reserveSession(
        accountId: UUID,
        request: CloudTranscriptionSessionRequest,
        sessionId: String,
    ): CloudTranscriptionSessionLease {
        val response =
            runCatching {
                httpClient.post(OPENAI_TRANSCRIPTION_SESSIONS_URL) {
                    header(HttpHeaders.Authorization, "Bearer $apiKey")
                    contentType(ContentType.Application.Json)
                    setBody(json.encodeToString(request.toOpenAiRequest()))
                }
            }.getOrElse { error ->
                Napier.w("Failed to create OpenAI realtime transcription session", error)
                throw CloudTranscriptionSessionUnavailableException("Realtime transcription provider unavailable", error)
            }

        val responseBody = response.bodyAsText()
        if (!response.status.isSuccess()) {
            Napier.w("OpenAI realtime transcription session setup failed: ${response.status}")
            throw CloudTranscriptionSessionUnavailableException(
                "Realtime transcription provider rejected session setup",
            )
        }

        val created =
            runCatching { json.decodeFromString<OpenAiTranscriptionSessionCreated>(responseBody) }
                .getOrElse { error ->
                    Napier.w("OpenAI realtime transcription session response could not be decoded", error)
                    throw CloudTranscriptionSessionUnavailableException("Realtime transcription provider returned invalid setup")
                }
        val clientSecret =
            created.clientSecret
                ?: throw CloudTranscriptionSessionUnavailableException("Realtime transcription provider returned no client secret")

        return CloudTranscriptionSessionLease(
            sessionId = sessionId,
            streamPath = "/api/v1/transcription/sessions/$sessionId/stream",
            inputFormat = CloudAudioInputFormat.PCM16_MONO_24KHZ,
            realtimeUrl = realtimeUrl,
            clientSecretValue = clientSecret.value,
            clientSecretExpiresAtEpochSeconds = clientSecret.expiresAt,
            modelId = modelId,
        )
    }

    private fun CloudTranscriptionSessionRequest.toOpenAiRequest(): OpenAiTranscriptionSessionRequest =
        OpenAiTranscriptionSessionRequest(
            audio =
                OpenAiAudioConfig(
                    input =
                        OpenAiAudioInputConfig(
                            transcription =
                                OpenAiInputTranscriptionConfig(
                                    model = modelId,
                                    language = language.substringBefore('-').ifBlank { null },
                                ),
                        ),
                ),
        )

    private companion object {
        const val DEFAULT_MODEL = "gpt-4o-transcribe"
        const val OPENAI_TRANSCRIPTION_SESSIONS_URL = "https://api.openai.com/v1/realtime/transcription_sessions"
        const val OPENAI_REALTIME_TRANSCRIPTION_URL = "wss://api.openai.com/v1/realtime?intent=transcription"
    }
}

/**
 * Builds the production provider from environment variables.
 */
fun cloudTranscriptionSessionProviderFromEnvironment(httpClient: HttpClient): CloudTranscriptionSessionProvider {
    val apiKey = System.getenv("OPENAI_API_KEY").orEmpty()
    if (apiKey.isBlank()) {
        return DisabledCloudTranscriptionSessionProvider("OPENAI_API_KEY is not configured")
    }

    return OpenAiRealtimeTranscriptionSessionProvider(
        apiKey = apiKey,
        httpClient = httpClient,
        modelId = System.getenv("OPENAI_REALTIME_TRANSCRIPTION_MODEL").orEmpty().ifBlank { "gpt-4o-transcribe" },
    )
}

@Serializable
private data class OpenAiTranscriptionSessionRequest(
    val type: String = "transcription",
    val audio: OpenAiAudioConfig,
    val include: List<String> = listOf("item.input_audio_transcription.logprobs"),
)

@Serializable
private data class OpenAiAudioConfig(
    val input: OpenAiAudioInputConfig,
)

@Serializable
private data class OpenAiAudioInputConfig(
    val format: OpenAiAudioFormat = OpenAiAudioFormat(),
    @SerialName("noise_reduction")
    val noiseReduction: OpenAiNoiseReductionConfig = OpenAiNoiseReductionConfig(),
    val transcription: OpenAiInputTranscriptionConfig,
    @SerialName("turn_detection")
    val turnDetection: OpenAiTurnDetectionConfig = OpenAiTurnDetectionConfig(),
)

@Serializable
private data class OpenAiAudioFormat(
    val type: String = "audio/pcm",
    val rate: Int = 24_000,
)

@Serializable
private data class OpenAiNoiseReductionConfig(
    val type: String = "near_field",
)

@Serializable
private data class OpenAiInputTranscriptionConfig(
    val model: String,
    val language: String? = null,
)

@Serializable
private data class OpenAiTurnDetectionConfig(
    val type: String = "server_vad",
    val threshold: Float = 0.5f,
    @SerialName("prefix_padding_ms")
    val prefixPaddingMs: Int = 300,
    @SerialName("silence_duration_ms")
    val silenceDurationMs: Int = 500,
)

@Serializable
private data class OpenAiTranscriptionSessionCreated(
    val id: String,
    @SerialName("client_secret")
    val clientSecret: OpenAiClientSecret? = null,
)

@Serializable
private data class OpenAiClientSecret(
    val value: String,
    @SerialName("expires_at")
    val expiresAt: Long,
)
