package app.logdate.server.transcription

import app.logdate.shared.model.transcription.CloudAudioInputFormat
import app.logdate.shared.model.transcription.CloudTranscriptionMode
import app.logdate.shared.model.transcription.CloudTranscriptionSessionRequest
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class OpenAiRealtimeTranscriptionSessionProviderTest {
    @Test
    fun `reserve session creates OpenAI realtime transcription session`() =
        runTest {
            lateinit var capturedRequest: HttpRequestData
            val provider =
                OpenAiRealtimeTranscriptionSessionProvider(
                    apiKey = "server-key",
                    httpClient =
                        HttpClient(
                            MockEngine { request ->
                                capturedRequest = request
                                openAiCreatedSessionResponse()
                            },
                        ),
                )

            val lease =
                provider.reserveSession(
                    accountId = UUID.fromString("11111111-1111-1111-1111-111111111111"),
                    request =
                        CloudTranscriptionSessionRequest(
                            noteId = "note-1",
                            language = "en-US",
                            mode = CloudTranscriptionMode.REALTIME,
                        ),
                    sessionId = "session-1",
                )

            assertEquals("https://api.openai.com/v1/realtime/transcription_sessions", capturedRequest.url.toString())
            assertEquals("Bearer server-key", capturedRequest.headers[HttpHeaders.Authorization])
            val requestBody = (capturedRequest.body as TextContent).text
            assertTrue(requestBody.contains("gpt-4o-transcribe"))
            assertTrue(requestBody.contains("\"rate\":24000"))
            assertEquals("session-1", lease.sessionId)
            assertEquals("/api/v1/transcription/sessions/session-1/stream", lease.streamPath)
            assertEquals("wss://api.openai.com/v1/realtime?intent=transcription", lease.realtimeUrl)
            assertEquals("ephemeral-openai-token", lease.clientSecretValue)
            assertEquals(1_850_000_000, lease.clientSecretExpiresAtEpochSeconds)
            assertEquals("gpt-4o-transcribe", lease.modelId)
            assertEquals(CloudAudioInputFormat.PCM16_MONO_24KHZ, lease.inputFormat)
        }

    @Test
    fun `reserve session reports unavailable when OpenAI rejects setup`() =
        runTest {
            val provider =
                OpenAiRealtimeTranscriptionSessionProvider(
                    apiKey = "server-key",
                    httpClient =
                        HttpClient(
                            MockEngine {
                                respond(
                                    content = """{"error":{"message":"bad key"}}""",
                                    status = HttpStatusCode.Unauthorized,
                                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                                )
                            },
                        ),
                )

            assertFailsWith<CloudTranscriptionSessionUnavailableException> {
                provider.reserveSession(
                    accountId = UUID.fromString("11111111-1111-1111-1111-111111111111"),
                    request = CloudTranscriptionSessionRequest(noteId = "note-1"),
                    sessionId = "session-1",
                )
            }
        }

    private fun io.ktor.client.engine.mock.MockRequestHandleScope.openAiCreatedSessionResponse(): HttpResponseData =
        respond(
            content =
                """
                {
                  "object": "realtime.transcription_session",
                  "id": "sess_openai",
                  "client_secret": {
                    "value": "ephemeral-openai-token",
                    "expires_at": 1850000000
                  }
                }
                """.trimIndent(),
            status = HttpStatusCode.OK,
            headers = headersOf(HttpHeaders.ContentType, "application/json"),
        )
}
