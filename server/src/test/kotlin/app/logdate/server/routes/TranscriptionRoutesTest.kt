package app.logdate.server.routes

import app.logdate.server.auth.JwtTokenService
import app.logdate.server.entitlements.Entitlement
import app.logdate.server.entitlements.EntitlementFeature
import app.logdate.server.entitlements.EntitlementLimits
import app.logdate.server.entitlements.EntitlementService
import app.logdate.server.entitlements.EntitlementStatus
import app.logdate.server.entitlements.EntitlementTier
import app.logdate.server.transcription.CloudTranscriptionSessionLease
import app.logdate.server.transcription.CloudTranscriptionSessionProvider
import app.logdate.server.transcription.CloudTranscriptionSessionUnavailableException
import app.logdate.shared.model.transcription.CloudTranscriptionMode
import app.logdate.shared.model.transcription.CloudTranscriptionSessionResponse
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.testing.TestApplicationBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

private const val TRANSCRIPTION_ROUTE_HMAC_KEY = "transcription-route-test-secret"

class TranscriptionRoutesTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val accountId = UUID.fromString("11111111-1111-1111-1111-111111111111")
    private val tokenService = JwtTokenService(TRANSCRIPTION_ROUTE_HMAC_KEY)

    @Test
    fun `creating transcription session requires authorization`() =
        testApplication {
            configureTranscriptionRoutes(entitlement = realtimeEntitlement())

            val response =
                client.post("/api/v1/transcription/sessions") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"noteId":"note-1"}""")
                }

            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }

    @Test
    fun `creating realtime transcription session requires cloud feature`() =
        testApplication {
            configureTranscriptionRoutes(entitlement = freeEntitlement())

            val response =
                client.post("/api/v1/transcription/sessions") {
                    header(HttpHeaders.Authorization, authHeader())
                    contentType(ContentType.Application.Json)
                    setBody("""{"noteId":"note-1"}""")
                }

            assertEquals(HttpStatusCode.PaymentRequired, response.status)
        }

    @Test
    fun `creating refinement session requires refinement feature`() =
        testApplication {
            configureTranscriptionRoutes(entitlement = realtimeEntitlement())

            val response =
                client.post("/api/v1/transcription/sessions") {
                    header(HttpHeaders.Authorization, authHeader())
                    contentType(ContentType.Application.Json)
                    setBody("""{"noteId":"note-1","mode":"REFINEMENT"}""")
                }

            assertEquals(HttpStatusCode.PaymentRequired, response.status)
        }

    @Test
    fun `cancelled transcription entitlement cannot create cloud sessions`() =
        testApplication {
            configureTranscriptionRoutes(
                entitlement =
                    realtimeEntitlement().copy(
                        status = EntitlementStatus.CANCELLED,
                    ),
            )

            val response =
                client.post("/api/v1/transcription/sessions") {
                    header(HttpHeaders.Authorization, authHeader())
                    contentType(ContentType.Application.Json)
                    setBody("""{"noteId":"note-1"}""")
                }

            assertEquals(HttpStatusCode.PaymentRequired, response.status)
        }

    @Test
    fun `creating realtime transcription session returns provider hidden stream path`() =
        testApplication {
            configureTranscriptionRoutes(
                entitlement = realtimeEntitlement(),
                provider =
                    StaticCloudTranscriptionSessionProvider(
                        lease =
                            CloudTranscriptionSessionLease(
                                sessionId = "session-fixed",
                                streamPath = "/api/v1/transcription/sessions/session-fixed/stream",
                                realtimeUrl = "wss://transcribe.example.test/session-fixed",
                                clientSecretValue = "ephemeral-secret",
                                clientSecretExpiresAtEpochSeconds = 1_850_000_000,
                                modelId = "gpt-4o-transcribe",
                            ),
                    ),
            )

            val response =
                client.post("/api/v1/transcription/sessions") {
                    header(HttpHeaders.Authorization, authHeader())
                    contentType(ContentType.Application.Json)
                    setBody("""{"noteId":"note-1","language":"en-US","mode":"REALTIME"}""")
                }
            val payload = json.decodeFromString<CloudTranscriptionSessionResponse>(response.bodyAsText())

            assertEquals(HttpStatusCode.Created, response.status)
            assertEquals("session-fixed", payload.sessionId)
            assertEquals("note-1", payload.noteId)
            assertEquals("en-US", payload.language)
            assertEquals(CloudTranscriptionMode.REALTIME, payload.mode)
            assertEquals("/api/v1/transcription/sessions/session-fixed/stream", payload.streamPath)
            assertEquals("logdate-cloud", payload.provider)
            assertEquals("wss://transcribe.example.test/session-fixed", payload.realtimeUrl)
            assertEquals("ephemeral-secret", payload.clientSecret?.value)
            assertEquals(1_850_000_000, payload.clientSecret?.expiresAtEpochSeconds)
            assertEquals("gpt-4o-transcribe", payload.modelId)
        }

    @Test
    fun `creating transcription session fails closed when cloud provider is unavailable`() =
        testApplication {
            configureTranscriptionRoutes(
                entitlement = realtimeEntitlement(),
                provider =
                    StaticCloudTranscriptionSessionProvider(
                        failure = CloudTranscriptionSessionUnavailableException("OpenAI realtime transcription is disabled"),
                    ),
            )

            val response =
                client.post("/api/v1/transcription/sessions") {
                    header(HttpHeaders.Authorization, authHeader())
                    contentType(ContentType.Application.Json)
                    setBody("""{"noteId":"note-1","language":"en-US","mode":"REALTIME"}""")
                }

            assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
        }

    private fun TestApplicationBuilder.configureTranscriptionRoutes(
        entitlement: Entitlement,
        provider: CloudTranscriptionSessionProvider = StaticCloudTranscriptionSessionProvider(),
    ) {
        application {
            install(ContentNegotiation) {
                json(json)
            }
            routing {
                route("/api/v1") {
                    transcriptionRoutes(
                        tokenService = tokenService,
                        entitlementService = StaticEntitlementService(entitlement),
                        sessionIdFactory = { "session-fixed" },
                        sessionProvider = provider,
                    )
                }
            }
        }
    }

    private fun authHeader(): String = "Bearer ${tokenService.generateAccessToken(accountId.toString())}"

    private fun freeEntitlement(): Entitlement =
        Entitlement(
            planId = "free",
            tier = EntitlementTier.FREE,
            status = EntitlementStatus.ACTIVE,
            limits =
                EntitlementLimits(
                    storageBytes = 512L * 1024L * 1024L,
                    backupCount = 1,
                    transcriptionSecondsPerMonth = 0,
                ),
        )

    private fun realtimeEntitlement(): Entitlement =
        Entitlement(
            planId = "cloud-standard",
            tier = EntitlementTier.STANDARD,
            status = EntitlementStatus.ACTIVE,
            limits =
                EntitlementLimits(
                    storageBytes = 10L * 1024L * 1024L * 1024L,
                    backupCount = 30,
                    transcriptionSecondsPerMonth = 3_600,
                ),
            features = mapOf(EntitlementFeature.CLOUD_TRANSCRIPTION_REALTIME.key to true),
        )

    private class StaticEntitlementService(
        private val entitlement: Entitlement,
    ) : EntitlementService {
        override suspend fun resolve(accountId: UUID): Entitlement = entitlement
    }

    private class StaticCloudTranscriptionSessionProvider(
        private val lease: CloudTranscriptionSessionLease =
            CloudTranscriptionSessionLease(
                sessionId = "session-fixed",
                streamPath = "/api/v1/transcription/sessions/session-fixed/stream",
            ),
        private val failure: Exception? = null,
    ) : CloudTranscriptionSessionProvider {
        override suspend fun reserveSession(
            accountId: UUID,
            request: app.logdate.shared.model.transcription.CloudTranscriptionSessionRequest,
            sessionId: String,
        ): CloudTranscriptionSessionLease {
            failure?.let { throw it }
            return lease
        }
    }
}
