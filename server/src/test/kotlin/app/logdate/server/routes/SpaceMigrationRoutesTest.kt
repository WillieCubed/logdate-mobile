package app.logdate.server.routes

import app.logdate.server.auth.JwtTokenService
import app.logdate.server.migration.MigrationFixture
import app.logdate.server.migration.legacyMedia
import app.logdate.server.routes.sync.spaceMigrationRoutes
import app.logdate.server.transfer.TransferSlots
import app.logdate.shared.model.AccountStorageMode
import app.logdate.shared.model.sync.LegacyInventory
import app.logdate.shared.model.sync.LegacyRecordKinds
import app.logdate.shared.model.sync.RegisteredLegacyMedia
import app.logdate.shared.model.sync.SpaceMigrationErrorCodes
import app.logdate.shared.model.sync.SpaceMigrationPhase
import app.logdate.shared.model.sync.SpaceMigrationRequest
import app.logdate.shared.model.sync.SpaceMigrationStatus
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
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
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

/** `/api/v1/space-migration` over HTTP: who may call it, and what each step answers. */
class SpaceMigrationRoutesTest {
    private val tokens = JwtTokenService("space-migration-routes-secret")
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `a device moves its journal one step at a time over HTTP`() =
        testApplication {
            val fixture = MigrationFixture()
            serve(fixture)
            val account = fixture.legacyAccount()
            fixture.addLegacyEntry(account, "Entry-One")
            fixture.putSpaceRecord(account, "sealed-one")

            assertEquals(
                SpaceMigrationPhase.NONE,
                client.get("/api/v1/space-migration") { bearer(account) }.decode<SpaceMigrationStatus>().phase,
            )
            assertEquals(
                SpaceMigrationPhase.UPLOADING,
                post(account, "begin", SpaceMigrationRequest("move-1")).decode<SpaceMigrationStatus>().phase,
            )
            val media = fixture.legacyMedia(account)
            val registered = post(account, "media/${media.mediaId}", SpaceMigrationRequest("move-1")).decode<RegisteredLegacyMedia>()
            assertEquals(media.mediaId, registered.mediaId)

            val inventory =
                client
                    .get(
                        "/api/v1/space-migration/legacy-inventory?ids=${LegacyRecordKinds.ENTRIES}",
                    ) { bearer(account) }
                    .decode<LegacyInventory>()
            assertEquals(listOf("entry-one"), inventory.kinds.single { it.kind == LegacyRecordKinds.ENTRIES }.ids)
            assertEquals(null, inventory.kinds.single { it.kind == LegacyRecordKinds.JOURNALS }.ids)

            val early = post(account, "switch", fixture.proof(account, "move-1"))
            assertEquals(HttpStatusCode.Conflict, early.status)
            assertEquals(SpaceMigrationErrorCodes.HOLD_NOT_READY, early.code())
            post(account, "hold", SpaceMigrationRequest("move-1")).decode<SpaceMigrationStatus>()
            fixture.clock.advance(fixture.config.holdGrace)

            val switched = post(account, "switch", fixture.proof(account, "move-1")).decode<SpaceMigrationStatus>()
            assertEquals(SpaceMigrationPhase.SWITCHED, switched.phase)
            assertEquals(AccountStorageMode.PERSONAL_SPACE_V1, fixture.mode(account))
        }

    @Test
    fun `media registration needs a move that is uploading`() =
        testApplication {
            val fixture = MigrationFixture()
            serve(fixture)
            val account = fixture.legacyAccount()
            val media = fixture.legacyMedia(account)

            val refused = post(account, "media/${media.mediaId}", SpaceMigrationRequest("move-1"))
            assertEquals(HttpStatusCode.Conflict, refused.status)
            assertEquals(SpaceMigrationErrorCodes.NOT_ACTIVE, refused.code())

            post(account, "begin", SpaceMigrationRequest("move-1"))
            val missing = post(account, "media/not-a-file", SpaceMigrationRequest("move-1"))
            assertEquals(HttpStatusCode.NotFound, missing.status)
            assertEquals("MEDIA_NOT_FOUND", missing.code())
        }

    @Test
    fun `a linked watch or an anonymous caller is refused`() =
        testApplication {
            val fixture = MigrationFixture()
            serve(fixture)
            val account = fixture.legacyAccount()

            assertEquals(HttpStatusCode.Unauthorized, client.get("/api/v1/space-migration").status)
            val watch =
                client.post("/api/v1/space-migration/begin") {
                    header(
                        HttpHeaders.Authorization,
                        "Bearer ${tokens.generateDeviceAccessToken(account.toString(), null, UUID.randomUUID().toString())}",
                    )
                    contentType(ContentType.Application.Json)
                    setBody("""{"migrationId":"move-1"}""")
                }
            assertEquals(HttpStatusCode.Forbidden, watch.status)
            assertEquals(SpaceMigrationPhase.NONE, fixture.service.status(account).phase)
        }

    @Test
    fun `every call answers feature disabled while the migration is off`() =
        testApplication {
            val fixture = MigrationFixture()
            serve(fixture, enabled = false)
            val account = fixture.legacyAccount()

            listOf(
                client.get("/api/v1/space-migration") { bearer(account) },
                post(account, "begin", SpaceMigrationRequest("move-1")),
                post(account, "media/m1", SpaceMigrationRequest("move-1")),
                client.get("/api/v1/space-migration/legacy-inventory") { bearer(account) },
            ).forEach { response ->
                assertEquals(HttpStatusCode.NotFound, response.status)
                assertEquals("FEATURE_DISABLED", response.code())
            }
        }

    @Test
    fun `a malformed body is refused without changing anything`() =
        testApplication {
            val fixture = MigrationFixture()
            serve(fixture)
            val account = fixture.legacyAccount()

            val response =
                client.post("/api/v1/space-migration/begin") {
                    bearer(account)
                    contentType(ContentType.Application.Json)
                    setBody("""{"migration":1}""")
                }
            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertEquals(SpaceMigrationPhase.NONE, fixture.service.status(account).phase)
        }

    private fun ApplicationTestBuilder.serve(
        fixture: MigrationFixture,
        enabled: Boolean = true,
    ) {
        application {
            install(ContentNegotiation) { json() }
            routing {
                route("/api/v1") {
                    spaceMigrationRoutes(tokens, fixture.service.takeIf { enabled }, fixture.copier, fixture.accounts, TransferSlots())
                }
            }
        }
    }

    private suspend inline fun <reified T> ApplicationTestBuilder.post(
        account: UUID,
        path: String,
        body: T,
    ): HttpResponse =
        client.post("/api/v1/space-migration/$path") {
            bearer(account)
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(body))
        }

    private fun HttpRequestBuilder.bearer(account: UUID) {
        header(HttpHeaders.Authorization, "Bearer ${tokens.generateAccessToken(account.toString())}")
    }

    private suspend inline fun <reified T> HttpResponse.decode(): T {
        assertEquals(HttpStatusCode.OK, status, bodyAsText())
        return json.decodeFromString(bodyAsText())
    }

    private suspend fun HttpResponse.code(): String? =
        Json
            .parseToJsonElement(bodyAsText())
            .jsonObject["code"]
            ?.jsonPrimitive
            ?.content
}
