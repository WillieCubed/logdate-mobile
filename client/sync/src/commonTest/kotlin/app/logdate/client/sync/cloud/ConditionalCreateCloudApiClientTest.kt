package app.logdate.client.sync.cloud

import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.sync.recovery.DownloadInbox
import app.logdate.client.sync.recovery.DownloadInboxTest
import app.logdate.client.sync.recovery.DownloadScope
import app.logdate.client.sync.recovery.DurableCloudApiClient
import app.logdate.client.sync.test.FakeCloudApiClient
import app.logdate.shared.config.DefaultLogDateConfigRepository
import app.logdate.shared.model.DeploymentKind
import app.logdate.shared.model.Journal
import app.logdate.shared.model.ServerDescriptor
import app.logdate.shared.model.ServerProtocolFeature
import app.logdate.shared.model.diagnostics.DiagnosticPhase
import app.logdate.shared.model.diagnostics.SyncDiagnosticEvent
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Tests the create-if-absent requests a linked watch uses, against the answers the server sends:
 * `201` with the new version, `412 <NOUN>_EXISTS` with `details.serverVersion`, and `410 <NOUN>_DELETED`
 * with `details.deletedAt`, so a repeated create never overwrites or brings back a record, and
 * nothing of the kind is sent to a server that would ignore it.
 */
class ConditionalCreateCloudApiClientTest {
    private val requests = mutableListOf<HttpRequestData>()

    @Test
    fun `a content create asks for a strict create and reads the created version`() =
        runTest {
            val client = client { respondJson("""{"id":"$ID","serverVersion":7,"uploadedAt":1000}""", HttpStatusCode.Created) }

            val result = client.createContentIfAbsent("token", content())

            assertEquals(ConditionalCreateResult.Created(7, Instant.fromEpochMilliseconds(1_000)), result.getOrThrow())
            val request = requests.single()
            assertEquals(HttpMethod.Put, request.method)
            assertEquals("$API/contents/$ID?ifAbsent=true", request.url.toString())
            assertEquals("Bearer token", request.headers[HttpHeaders.Authorization])
        }

    @Test
    fun `an existing record answers with its server version`() =
        runTest {
            val client = client { respondJson(existsBody("CONTENT_EXISTS", """"serverVersion":"9""""), HttpStatusCode.PreconditionFailed) }

            assertEquals(ConditionalCreateResult.AlreadyExists(9), client.createContentIfAbsent("token", content()).getOrThrow())
        }

    @Test
    fun `an existing record that reports no version is a failure to retry`() =
        runTest {
            val client = client { respondJson(existsBody("CONTENT_EXISTS"), HttpStatusCode.PreconditionFailed) }

            val error = assertIs<CloudApiException>(client.createContentIfAbsent("token", content()).exceptionOrNull())

            assertEquals("CONTENT_EXISTS", error.errorCode)
            assertEquals(412, error.statusCode)
        }

    @Test
    fun `a deleted record answers with when it was deleted`() =
        runTest {
            val client =
                client { respondJson(existsBody("CONTENT_DELETED", """"deletedAt":"1234","serverVersion":"5""""), HttpStatusCode.Gone) }

            assertEquals(
                ConditionalCreateResult.Deleted(Instant.fromEpochMilliseconds(1_234)),
                client.createContentIfAbsent("token", content()).getOrThrow(),
            )
        }

    @Test
    fun `a deleted record that reports no deletion time is a parse error`() =
        runTest {
            val client = client { respondJson(existsBody("CONTENT_DELETED"), HttpStatusCode.Gone) }

            val error = assertIs<CloudApiException>(client.createContentIfAbsent("token", content()).exceptionOrNull())

            assertEquals("PARSE_ERROR", error.errorCode)
            assertEquals(410, error.statusCode)
        }

    @Test
    fun `a server without conditional create is never sent one`() =
        runTest {
            val client = client(conditionalCreate = false) { respondJson("{}", HttpStatusCode.Created) }

            assertIs<ConditionalCreateUnsupportedException>(client.createContentIfAbsent("token", content()).exceptionOrNull())
            assertIs<ConditionalCreateUnsupportedException>(client.createJournalIfAbsent("token", journal()).exceptionOrNull())
            assertIs<ConditionalCreateUnsupportedException>(client.contentState("token", ID).exceptionOrNull())
            assertIs<ConditionalCreateUnsupportedException>(client.uploadAssociationsIfAbsent("token", links()).exceptionOrNull())
            assertTrue(requests.isEmpty())
        }

    @Test
    fun `an update that answers ok means the server ignored the condition`() =
        runTest {
            val client = client { respondJson("""{"id":"$ID","serverVersion":7,"uploadedAt":1000}""", HttpStatusCode.OK) }

            val error = assertIs<CloudApiException>(client.createContentIfAbsent("token", content()).exceptionOrNull())

            assertEquals(200, error.statusCode)
        }

    @Test
    fun `an unreadable conflict answer is a parse error`() =
        runTest {
            val client = client { respondJson("nope", HttpStatusCode.PreconditionFailed) }

            val error = assertIs<CloudApiException>(client.createContentIfAbsent("token", content()).exceptionOrNull())
            assertEquals("PARSE_ERROR", error.errorCode)
            assertEquals(412, error.statusCode)
        }

    @Test
    fun `a conflict with another code keeps that code`() =
        runTest {
            val client =
                client { respondJson(existsBody("VERSION_CONFLICT", """"serverVersion":"9""""), HttpStatusCode.PreconditionFailed) }

            val error = assertIs<CloudApiException>(client.createContentIfAbsent("token", content()).exceptionOrNull())

            assertEquals("VERSION_CONFLICT", error.errorCode)
            assertEquals(412, error.statusCode)
        }

    @Test
    fun `a journal create uses the journals path and its own codes`() =
        runTest {
            val created = client { respondJson("""{"id":"$ID","serverVersion":3,"uploadedAt":5}""", HttpStatusCode.Created) }

            assertEquals(
                ConditionalCreateResult.Created(3, Instant.fromEpochMilliseconds(5)),
                created.createJournalIfAbsent("token", journal()).getOrThrow(),
            )
            assertEquals("$API/journals/$ID?ifAbsent=true", requests.single().url.toString())

            val existing =
                client { respondJson(existsBody("JOURNAL_EXISTS", """"serverVersion":"4""""), HttpStatusCode.PreconditionFailed) }
            assertEquals(ConditionalCreateResult.AlreadyExists(4), existing.createJournalIfAbsent("token", journal()).getOrThrow())

            val deleted =
                client { respondJson(existsBody("JOURNAL_DELETED", """"deletedAt":"8","serverVersion":"2""""), HttpStatusCode.Gone) }
            assertEquals(
                ConditionalCreateResult.Deleted(Instant.fromEpochMilliseconds(8)),
                deleted.createJournalIfAbsent("token", journal()).getOrThrow(),
            )
        }

    @Test
    fun `content state reads the version from the server version header`() =
        runTest {
            val client = client { respond("", HttpStatusCode.OK, headersOf(SERVER_VERSION_HEADER, "12")) }

            assertEquals(ContentState.Present(12), client.contentState("token", ID).getOrThrow())
            val request = requests.single()
            assertEquals(HttpMethod.Head, request.method)
            assertEquals("$API/contents/$ID", request.url.toString())
            assertEquals("Bearer token", request.headers[HttpHeaders.Authorization])
        }

    @Test
    fun `content state reports absent and deleted records`() =
        runTest {
            assertEquals(ContentState.Absent, client { respond("", HttpStatusCode.NotFound) }.contentState("token", ID).getOrThrow())

            val gone =
                client {
                    respond(
                        "",
                        HttpStatusCode.Gone,
                        headersOf(SERVER_VERSION_HEADER to listOf("5"), DELETED_AT_HEADER to listOf("1234")),
                    )
                }
            assertEquals(ContentState.Deleted(Instant.fromEpochMilliseconds(1_234)), gone.contentState("token", ID).getOrThrow())
        }

    @Test
    fun `content state without a version is a parse error`() =
        runTest {
            val error = assertIs<CloudApiException>(client { respond("", HttpStatusCode.OK) }.contentState("token", ID).exceptionOrNull())

            assertEquals("PARSE_ERROR", error.errorCode)
            assertEquals(200, error.statusCode)
        }

    @Test
    fun `a content state request is a read in the diagnostics`() =
        runTest {
            val events = mutableListOf<SyncDiagnosticEvent>()
            val client = client(diagnostics = { events += it }) { respond("", HttpStatusCode.NotFound) }

            client.contentState("token", ID).getOrThrow()

            assertEquals(DiagnosticPhase.FETCH, events.first().phase)
        }

    @Test
    fun `associations created if absent ask the server to skip existing ones`() =
        runTest {
            val existing = AssociationDeleteItem(journalId = ID, contentId = "existing")
            val deleted = AssociationDeletion(journalId = ID, contentId = "removed", deletedAt = 9, serverVersion = 9)
            val client =
                client {
                    respondJson(
                        """{"uploadedCount":1,"uploadedAt":5,"existing":[{"journalId":"$ID","contentId":"existing"}],""" +
                            """"deleted":[{"journalId":"$ID","contentId":"removed","deletedAt":9,"serverVersion":9}]}""",
                        HttpStatusCode.OK,
                    )
                }

            val response = client.uploadAssociationsIfAbsent("token", links()).getOrThrow()

            assertEquals(AssociationUploadResponse(1, 5, listOf(existing), listOf(deleted)), response)
            val request = requests.single()
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("$API/associations?ifAbsent=true", request.url.toString())
        }

    @Test
    fun `the data sources create through the client`() =
        runTest {
            val api = FakeCloudApiClient()
            api.createContentIfAbsentResponse = Result.success(ConditionalCreateResult.AlreadyExists(4))
            val note =
                JournalNote.Text(
                    uid = Uuid.parse(ID),
                    creationTimestamp = Instant.fromEpochMilliseconds(1),
                    lastUpdated = Instant.fromEpochMilliseconds(1),
                    content = "hi",
                )
            val link = JournalContentAssociation(Uuid.parse(ID), Uuid.parse(ID), Instant.fromEpochMilliseconds(1))

            assertEquals(
                ConditionalCreateResult.AlreadyExists(4),
                DefaultCloudContentDataSource(api).createNoteIfAbsent("token", note).getOrThrow(),
            )
            assertEquals(ID, api.createContentIfAbsentCalls.single().id)
            assertTrue(DefaultCloudJournalDataSource(api).createJournalIfAbsent("token", Journal(id = Uuid.parse(ID))).isSuccess)
            assertTrue(DefaultCloudAssociationDataSource(api).uploadAssociationsIfAbsent("token", listOf(link)).isSuccess)
            assertEquals(listOf("createContentIfAbsent", "createJournalIfAbsent", "uploadAssociationsIfAbsent"), api.methodCalls)
        }

    @Test
    fun `a data source without create if absent says so`() =
        runTest {
            val unsupported =
                object : CloudContentDataSource {
                    override suspend fun uploadNote(
                        accessToken: String,
                        note: JournalNote,
                    ) = Result.failure<SyncUploadResult>(IllegalStateException())

                    override suspend fun updateNote(
                        accessToken: String,
                        note: JournalNote,
                    ) = Result.failure<SyncUploadResult>(IllegalStateException())

                    override suspend fun deleteNote(
                        accessToken: String,
                        noteId: Uuid,
                    ) = Result.failure<Unit>(IllegalStateException())

                    override suspend fun getContentChanges(
                        accessToken: String,
                        since: Instant,
                        limit: Int?,
                    ) = Result.failure<ContentSyncResult>(IllegalStateException())
                }
            val note =
                JournalNote.Text(
                    uid = Uuid.parse(ID),
                    creationTimestamp = Instant.fromEpochMilliseconds(1),
                    lastUpdated = Instant.fromEpochMilliseconds(1),
                    content = "hi",
                )

            assertIs<UnsupportedOperationException>(unsupported.createNoteIfAbsent("token", note).exceptionOrNull())
        }

    @Test
    fun `the durable client forwards create if absent`() =
        runTest {
            val api = FakeCloudApiClient()
            api.createContentIfAbsentResponse = Result.success(ConditionalCreateResult.AlreadyExists(3))
            val database = DownloadInboxTest.Database()
            val inbox = DownloadInbox(database, database, { DownloadScope("a", "server") }, { 1L })
            val durable = DurableCloudApiClient(api, inbox)

            assertEquals(ConditionalCreateResult.AlreadyExists(3), durable.createContentIfAbsent("token", content()).getOrThrow())
        }

    private suspend fun client(
        conditionalCreate: Boolean = true,
        diagnostics: (SyncDiagnosticEvent) -> Unit = {},
        handler: MockRequestHandler,
    ): LogDateCloudApiClient {
        val config = DefaultLogDateConfigRepository(initialBackendUrl = ORIGIN)
        if (conditionalCreate) {
            config.updateServerDescriptor(
                ServerDescriptor(
                    serverOrigin = ORIGIN,
                    apiBaseUrl = API,
                    deploymentKind = DeploymentKind.SELF_HOSTED,
                    displayName = "Fixture",
                    protocolFeatures = listOf(ServerProtocolFeature.CONDITIONAL_CREATE_V1),
                ),
            )
        }
        requests.clear()
        val engine =
            MockEngine { request ->
                requests += request
                handler(request)
            }
        return LogDateCloudApiClient(
            config,
            HttpClient(engine) { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } },
            diagnostics = diagnostics,
        )
    }

    private fun MockRequestHandleScope.respondJson(
        body: String,
        status: HttpStatusCode,
    ) = respond(body, status, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))

    /** The server's `SimpleErrorResponse`: a top-level `code`, a `message` and string `details`. */
    private fun existsBody(
        code: String,
        details: String = "",
    ) = """{"code":"$code","message":"conflict","details":{$details},"timestamp":"2026-01-01T00:00:00Z"}"""

    private fun content() =
        ContentUploadRequest(id = ID, type = "TEXT", content = "LDSE2:ciphertext", mediaUri = null, createdAt = 1, lastUpdated = 1)

    private fun journal() = JournalUploadRequest(ID, "title", "", createdAt = 1, lastUpdated = 1)

    private fun links() = AssociationUploadRequest(listOf(Association(journalId = ID, contentId = ID, createdAt = 1)))

    private companion object {
        const val ID = "550e8400-e29b-41d4-a716-446655440000"
        const val ORIGIN = "https://cloud.example"
        const val API = "https://cloud.example/api/v1"
    }
}
