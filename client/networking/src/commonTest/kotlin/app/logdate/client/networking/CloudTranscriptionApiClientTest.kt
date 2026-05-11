package app.logdate.client.networking

import app.logdate.shared.config.LogDateConfigRepository
import app.logdate.shared.model.ServerDescriptor
import app.logdate.shared.model.transcription.CloudAudioInputFormat
import app.logdate.shared.model.transcription.CloudTranscriptionMode
import app.logdate.shared.model.transcription.CloudTranscriptionSessionRequest
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CloudTranscriptionApiClientTest {
    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    @Test
    fun `createSession posts authenticated request and decodes session lease`() =
        runTest {
            lateinit var capturedRequest: HttpRequestData
            val client =
                CloudTranscriptionApiClient(
                    httpClient =
                        HttpClient(
                            MockEngine { request ->
                                capturedRequest = request
                                respond(
                                    content =
                                        """
                                        {
                                          "sessionId": "session-1",
                                          "noteId": "note-1",
                                          "language": "en-US",
                                          "mode": "REALTIME",
                                          "streamPath": "/api/v1/transcription/sessions/session-1/stream",
                                          "inputFormat": "PCM16_MONO_24KHZ",
                                          "provider": "logdate-cloud",
                                          "realtimeUrl": "wss://api.openai.com/v1/realtime?intent=transcription",
                                          "clientSecret": {
                                            "value": "ephemeral-token",
                                            "expiresAtEpochSeconds": 1850000000
                                          },
                                          "modelId": "gpt-4o-transcribe"
                                        }
                                        """.trimIndent(),
                                    status = HttpStatusCode.Created,
                                    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                                )
                            },
                        ),
                    configRepository = StaticConfigRepository("https://cloud.logdate.app/api/v1"),
                    json = json,
                )

            val result =
                client.createSession(
                    accessToken = "access-token",
                    request =
                        CloudTranscriptionSessionRequest(
                            noteId = "note-1",
                            language = "en-US",
                            mode = CloudTranscriptionMode.REALTIME,
                        ),
                )

            val session = result.getOrThrow()
            assertEquals("https://cloud.logdate.app/api/v1/transcription/sessions", capturedRequest.url.toString())
            assertEquals("Bearer access-token", capturedRequest.headers[HttpHeaders.Authorization])
            assertTrue((capturedRequest.body as TextContent).text.contains("\"noteId\":\"note-1\""))
            assertEquals("session-1", session.sessionId)
            assertEquals(CloudAudioInputFormat.PCM16_MONO_24KHZ, session.inputFormat)
            assertEquals("ephemeral-token", session.clientSecret?.value)
            assertEquals("gpt-4o-transcribe", session.modelId)
        }

    @Test
    fun `createSession returns failure for subscription gated response`() =
        runTest {
            val client =
                CloudTranscriptionApiClient(
                    httpClient =
                        HttpClient(
                            MockEngine {
                                respond(
                                    content = """{"error":"cloud transcription requires an active LogDate Cloud subscription"}""",
                                    status = HttpStatusCode.PaymentRequired,
                                    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                                )
                            },
                        ),
                    configRepository = StaticConfigRepository("https://cloud.logdate.app/api/v1"),
                    json = json,
                )

            val result =
                client.createSession(
                    accessToken = "access-token",
                    request = CloudTranscriptionSessionRequest(noteId = "note-1"),
                )

            assertTrue(result.isFailure)
        }

    private class StaticConfigRepository(
        private val baseUrl: String,
    ) : LogDateConfigRepository {
        override val backendUrl: StateFlow<String> = MutableStateFlow("https://cloud.logdate.app")
        override val apiVersion: StateFlow<String> = MutableStateFlow("v1")
        override val apiBaseUrl: Flow<String> = flowOf(baseUrl)
        override val localServerAddress: StateFlow<String> = MutableStateFlow("localhost:8765")
        override val serverDescriptor: StateFlow<ServerDescriptor?> = MutableStateFlow(null)

        override suspend fun updateBackendUrl(url: String) = Unit

        override suspend fun updateApiVersion(version: String) = Unit

        override suspend fun updateLocalServerAddress(address: String) = Unit

        override suspend fun updateServerDescriptor(descriptor: ServerDescriptor?) = Unit

        override suspend fun resetToDefaults() = Unit

        override fun getCurrentBackendUrl(): String = backendUrl.value

        override fun getCurrentApiBaseUrl(): String = baseUrl

        override fun getCurrentServerDescriptor(): ServerDescriptor? = serverDescriptor.value
    }
}
