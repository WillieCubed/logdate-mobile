package app.logdate.server.routes.sync

import app.logdate.server.auth.AccessTokenVerifier
import app.logdate.server.auth.InMemoryAccountRepository
import app.logdate.server.auth.JwtTokenService
import app.logdate.server.database.toJavaUUID
import app.logdate.server.devices.DeviceSessionGuard
import app.logdate.server.logdate.asLogDateBackupRepository
import app.logdate.server.logdate.asLogDateCollectionsRepository
import app.logdate.server.logdate.asLogDateMediaBlobRepository
import app.logdate.server.logdate.asLogDateMediaRepository
import app.logdate.server.routes.support.DeviceLinkTestKit
import app.logdate.server.routes.support.contentUploadBody
import app.logdate.server.routes.syncRoutes
import app.logdate.server.sync.InMemorySyncRepository
import app.logdate.server.sync.SyncMetricsRegistry
import app.logdate.shared.model.devices.DeviceLinkErrorCodes
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.put
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
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class)
class SyncLinkedDeviceAccessTest {
    private val tokenService = JwtTokenService("linked-device-sync-secret")

    @Test
    fun `a linked device writes and reads entries until its session is removed`() =
        testApplication {
            val kit = DeviceLinkTestKit(tokenService, InMemoryAccountRepository())
            syncApp(kit.verifier)
            val owner = kit.newAccount("sync_owner")
            val watch = kit.linkWatch(owner)
            val watchBearer = "Bearer ${watch.tokens.accessToken}"
            val phoneBearer = "Bearer ${tokenService.generateAccessToken(owner.toString())}"

            assertEquals(HttpStatusCode.Created, client.putNote(watchBearer, "watch-note-1").status)
            assertEquals(HttpStatusCode.OK, client.getAs(watchBearer, "/api/v1/contents/watch-note-1").status)
            assertEquals(HttpStatusCode.OK, client.getAs(watchBearer, "/api/v1/ops/sync/status").status)
            assertEquals(HttpStatusCode.OK, client.getAs(phoneBearer, "/api/v1/contents/watch-note-1").status)

            kit.linkService.revoke(owner.toJavaUUID(), UUID.fromString(watch.sessionId))

            assertSyncError(
                client.putNote(watchBearer, "watch-note-2"),
                HttpStatusCode.Unauthorized,
                DeviceLinkErrorCodes.DEVICE_SESSION_REVOKED,
            )
            assertEquals(HttpStatusCode.Created, client.putNote(phoneBearer, "phone-note-1").status)
        }

    @Test
    fun `sync routes without a session store refuse linked device tokens`() =
        testApplication {
            val kit = DeviceLinkTestKit(tokenService, InMemoryAccountRepository())
            syncApp(AccessTokenVerifier(tokenService, DeviceSessionGuard.Disabled))
            val watch = kit.linkWatch(kit.newAccount("no_store_owner"))

            assertSyncError(
                client.putNote("Bearer ${watch.tokens.accessToken}", "watch-note-1"),
                HttpStatusCode.Unauthorized,
                DeviceLinkErrorCodes.DEVICE_SESSION_REVOKED,
            )
        }

    @Test
    fun `account wide sync routes refuse linked device tokens`() =
        testApplication {
            val kit = DeviceLinkTestKit(tokenService, InMemoryAccountRepository())
            syncApp(kit.verifier)
            val owner = kit.newAccount("account_wide_owner")
            val watchBearer = "Bearer ${kit.linkWatch(owner).tokens.accessToken}"

            val phoneBearer = "Bearer ${tokenService.generateAccessToken(owner.toString())}"

            val denied = DeviceLinkErrorCodes.DEVICE_SCOPE_DENIED
            assertSyncError(client.getAs(watchBearer, "/api/v1/backups"), HttpStatusCode.Forbidden, denied)
            assertSyncError(client.getAs(watchBearer, "/api/v1/ops/sync/metrics"), HttpStatusCode.Forbidden, denied)
            assertEquals(HttpStatusCode.OK, client.getAs(phoneBearer, "/api/v1/backups").status)
        }

    private fun ApplicationTestBuilder.syncApp(verifier: AccessTokenVerifier) {
        val repository = InMemorySyncRepository()
        application {
            install(ContentNegotiation) { json() }
            routing {
                route("/api/v1") {
                    syncRoutes(
                        tokenService = tokenService,
                        metrics = SyncMetricsRegistry(),
                        collectionsRepository = repository.asLogDateCollectionsRepository(),
                        mediaBlobRepository = repository.asLogDateMediaRepository().asLogDateMediaBlobRepository(),
                        backupRepository = repository.asLogDateBackupRepository(),
                        accessTokenVerifier = verifier,
                    )
                }
            }
        }
    }

    private suspend fun HttpClient.putNote(
        bearer: String,
        id: String,
    ): HttpResponse =
        put("/api/v1/contents/$id") {
            header(HttpHeaders.Authorization, bearer)
            contentType(ContentType.Application.Json)
            setBody(contentUploadBody(id = id))
        }

    private suspend fun HttpClient.getAs(
        bearer: String,
        path: String,
    ): HttpResponse = get(path) { header(HttpHeaders.Authorization, bearer) }

    private suspend fun assertSyncError(
        response: HttpResponse,
        status: HttpStatusCode,
        code: String,
    ) {
        assertEquals(status, response.status)
        assertEquals(
            code,
            Json
                .parseToJsonElement(response.bodyAsText())
                .jsonObject
                .getValue("code")
                .jsonPrimitive.content,
        )
    }
}
