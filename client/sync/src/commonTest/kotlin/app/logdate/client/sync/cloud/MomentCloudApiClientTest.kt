package app.logdate.client.sync.cloud

import app.logdate.client.datastore.SessionStorage
import app.logdate.client.datastore.UserSession
import app.logdate.client.sync.test.FakeSessionStorage
import app.logdate.shared.config.DefaultLogDateConfigRepository
import app.logdate.shared.model.DeploymentKind
import app.logdate.shared.model.ServerDescriptor
import app.logdate.shared.model.ServerProtocolFeature
import app.logdate.shared.model.sync.MomentUpdateRequest
import app.logdate.shared.model.sync.MomentUploadRequest
import app.logdate.shared.model.sync.VersionConstraint
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class MomentCloudApiClientTest {
    private val requests = mutableListOf<HttpRequestData>()
    private val id = "10000000-0000-0000-0000-000000000001"
    private val upload = MomentUploadRequest(id, 1, 2, payload = "LDSE2:encrypted")

    @Test
    fun `strict create asks for tombstone-safe insertion`() =
        runTest {
            val api = client(HttpStatusCode.Created, """{"id":"$id","serverVersion":7,"uploadedAt":20}""")
            assertIs<ConditionalCreateResult.Created>(api.createMomentIfAbsent("token", upload).getOrThrow())
            assertEquals(HttpMethod.Put, requests.single().method)
            assertEquals("https://example.test/api/v1/moments/$id?ifAbsent=true", requests.single().url.toString())
            assertEquals("Bearer token", requests.single().headers[HttpHeaders.Authorization])
        }

    @Test
    fun `strict create reads existing and deleted outcomes without overwriting`() =
        runTest {
            val existing = client(HttpStatusCode.PreconditionFailed, """{"code":"MOMENT_EXISTS","details":{"serverVersion":"8"}}""")
            assertEquals(ConditionalCreateResult.AlreadyExists(8), existing.createMomentIfAbsent("token", upload).getOrThrow())
            val deleted = client(HttpStatusCode.Gone, """{"code":"MOMENT_DELETED","details":{"deletedAt":"40"}}""")
            assertIs<ConditionalCreateResult.Deleted>(deleted.createMomentIfAbsent("token", upload).getOrThrow())
        }

    @Test
    fun `an ignored create condition and malformed conflict never acknowledge creation`() =
        runTest {
            val ignored = client(HttpStatusCode.OK, """{"serverVersion":7,"uploadedAt":20}""")
            assertTrue(ignored.createMomentIfAbsent("token", upload).isFailure)
            val broken = client(HttpStatusCode.PreconditionFailed, """{"code":"MOMENT_EXISTS"}""")
            assertTrue(broken.createMomentIfAbsent("token", upload).isFailure)
        }

    @Test
    fun `missing capabilities prevent every moment request`() =
        runTest {
            val api = client(HttpStatusCode.Created, "{}", features = emptyList())
            assertTrue(api.createMomentIfAbsent("token", upload).isFailure)
            assertTrue(api.updateMoment("token", id, update()).isFailure)
            assertTrue(api.getMoment("token", id).isFailure)
            assertTrue(api.getMomentChanges("token", 0).isFailure)
            assertTrue(api.deleteMoment("token", id).isFailure)
            assertTrue(requests.isEmpty())
        }

    @Test
    fun `moment support alone does not authorize an unsafe create`() =
        runTest {
            val api = client(HttpStatusCode.Created, "{}", features = listOf(ServerProtocolFeature.MOMENTS_V1))
            assertTrue(api.createMomentIfAbsent("token", upload).isFailure)
            assertTrue(requests.isEmpty())
        }

    @Test
    fun `updates retain the server conflict code`() =
        runTest {
            val api = client(HttpStatusCode.Conflict, """{"code":"CONFLICT","message":"Server has a newer version"}""")
            val failure = assertIs<CloudApiException>(api.updateMoment("token", id, update()).exceptionOrNull())
            assertEquals("CONFLICT", failure.errorCode)
            assertEquals(409, failure.statusCode)
            assertEquals(HttpMethod.Patch, requests.single().method)
            assertEquals(
                update(),
                Json.decodeFromString<MomentUpdateRequest>(
                    requests
                        .single()
                        .body
                        .toByteArray()
                        .decodeToString(),
                ),
            )
        }

    @Test
    fun `change pages retain cursor deletions and pagination`() =
        runTest {
            val api =
                client(
                    HttpStatusCode.OK,
                    """{"changes":[],"deletions":[{"id":"$id","deletedAt":30,"serverVersion":4}],"lastTimestamp":50,"hasMore":true}""",
                )
            val page = api.getMomentChanges("token", 12, 3).getOrThrow()
            assertEquals(50, page.lastTimestamp)
            assertTrue(page.hasMore)
            assertEquals(id, page.deletions.single().id)
            assertEquals("12", requests.single().url.parameters["since"])
            assertEquals("3", requests.single().url.parameters["limit"])
        }

    @Test
    fun `single read and delete use their dedicated routes`() =
        runTest {
            val api =
                client(
                    HttpStatusCode.OK,
                    """{"id":"$id","capturedAt":1,"lastUpdated":2,"deviceId":"watch","payload":"LDSE2:encrypted","schema":1,"serverVersion":3}""",
                )
            assertEquals(id, api.getMoment("token", id).getOrThrow().id)
            assertEquals(HttpMethod.Get, requests.single().method)
            val deleting = client(HttpStatusCode.NoContent, "")
            deleting.deleteMoment("token", id).getOrThrow()
            assertEquals(HttpMethod.Delete, requests.single().method)
        }

    @Test
    fun `cancellation propagates rather than becoming a retry failure`() =
        runTest {
            val api = client(HttpStatusCode.OK, "{}", cancel = true)
            assertFailsWith<CancellationException> { api.getMomentChanges("token", 0) }
        }

    @Test
    fun `rotated credentials reject the old moment token before dispatch`() =
        runTest {
            val sessions =
                FakeSessionStorage().apply {
                    currentOrigin = "https://example.test"
                    saveSession(UserSession("old", "refresh", "account"))
                }
            val api = client(HttpStatusCode.OK, "{}", sessions = sessions)
            sessions.saveSession(UserSession("new", "rotated", "account"))
            val failure = assertIs<CloudApiException>(api.getMomentChanges("old", 0).exceptionOrNull())
            assertEquals("CLOUD_SCOPE_CHANGED", failure.errorCode)
            assertTrue(requests.isEmpty())
        }

    @Test
    fun `capabilities advertised for another API destination authorize no requests`() =
        runTest {
            val api = client(HttpStatusCode.OK, "{}", advertisedApi = "https://other.test/api/v1")
            assertTrue(api.getMomentChanges("token", 0).isFailure)
            assertTrue(requests.isEmpty())
        }

    private fun update() = MomentUpdateRequest(payload = "LDSE2:encrypted", lastUpdated = 2, versionConstraint = VersionConstraint.Known(7))

    private suspend fun client(
        status: HttpStatusCode,
        body: String,
        features: List<String> = listOf(ServerProtocolFeature.MOMENTS_V1, ServerProtocolFeature.CONDITIONAL_CREATE_V1),
        cancel: Boolean = false,
        sessions: SessionStorage? = null,
        advertisedApi: String = "https://example.test/api/v1",
    ): LogDateCloudApiClient {
        requests.clear()
        val config = DefaultLogDateConfigRepository(initialBackendUrl = "https://example.test")
        config.updateServerDescriptor(
            ServerDescriptor(
                serverOrigin = "https://example.test",
                apiBaseUrl = advertisedApi,
                deploymentKind = DeploymentKind.SELF_HOSTED,
                displayName = "Fixture",
                protocolFeatures = features,
            ),
        )
        return LogDateCloudApiClient(
            config,
            HttpClient(
                MockEngine {
                    requests += it
                    if (cancel) throw CancellationException("cancelled")
                    respond(body, status, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
                },
            ) { install(ContentNegotiation) { json() } },
            sessionStorage = sessions,
        )
    }
}
