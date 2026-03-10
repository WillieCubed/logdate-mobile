package app.logdate.server.routes.sync

import app.logdate.server.auth.JwtTokenService
import app.logdate.server.logdate.asLogDateBackupRepository
import app.logdate.server.logdate.asLogDateCollectionsRepository
import app.logdate.server.logdate.asLogDateMediaRepository
import app.logdate.server.routes.support.authHeader
import app.logdate.server.routes.syncRoutes
import app.logdate.server.sync.InMemorySyncRepository
import app.logdate.server.sync.SyncMetricsRegistry
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SyncAuthorizationAndOpsPolicyTest {
    @Test
    fun `sync operations reject invalid token payload and require explicit token service wiring`() =
        testApplication {
            val tokenService = JwtTokenService("sync-policy-secret")
            val malformedAccountIdToken = tokenService.generateAccessToken("not-a-uuid")
            val syncRepository = InMemorySyncRepository()
            val defaultSyncRepository = InMemorySyncRepository()

            application {
                install(ContentNegotiation) { json() }
                routing {
                    route("/api/v1") {
                        syncRoutes(
                            tokenService = tokenService,
                            mediaStorage = null,
                            metrics = SyncMetricsRegistry(),
                            collectionsRepository = syncRepository.asLogDateCollectionsRepository(),
                            mediaRepository = syncRepository.asLogDateMediaRepository(),
                            backupRepository = syncRepository.asLogDateBackupRepository(),
                        )
                    }
                    route("/api/v1/defaults") {
                        syncRoutes(
                            metrics = SyncMetricsRegistry(),
                            collectionsRepository = defaultSyncRepository.asLogDateCollectionsRepository(),
                            mediaRepository = defaultSyncRepository.asLogDateMediaRepository(),
                            backupRepository = defaultSyncRepository.asLogDateBackupRepository(),
                        )
                    }
                }
            }

            val malformedPayload =
                client.get("/api/v1/ops/sync/status") {
                    header(HttpHeaders.Authorization, "Bearer $malformedAccountIdToken")
                }
            assertEquals(HttpStatusCode.Unauthorized, malformedPayload.status)
            assertTrue(malformedPayload.bodyAsText().contains("Invalid token payload"))

            val noAuthPrometheus = client.get("/api/v1/ops/sync/metrics/prometheus")
            assertEquals(HttpStatusCode.Unauthorized, noAuthPrometheus.status)

            val defaultRouteMissingTokenService = client.get("/api/v1/defaults/ops/sync/status")
            assertEquals(HttpStatusCode.InternalServerError, defaultRouteMissingTokenService.status)
            assertTrue(defaultRouteMissingTokenService.bodyAsText().contains("SERVER_MISCONFIGURED"))
        }

    @Test
    fun `sync tombstone purge endpoint clamps excessive retention values`() =
        testApplication {
            val tokenService = JwtTokenService("sync-policy-secret")
            val repository = InMemorySyncRepository()
            application {
                install(ContentNegotiation) { json() }
                routing {
                    route("/api/v1") {
                        syncRoutes(
                            tokenService = tokenService,
                            mediaStorage = null,
                            metrics = SyncMetricsRegistry(),
                            collectionsRepository = repository.asLogDateCollectionsRepository(),
                            mediaRepository = repository.asLogDateMediaRepository(),
                            backupRepository = repository.asLogDateBackupRepository(),
                        )
                    }
                }
            }

            val response =
                client.post("/api/v1/ops/sync/tombstones:purge?retentionDays=999999") {
                    header(HttpHeaders.Authorization, authHeader(tokenService))
                }
            assertEquals(HttpStatusCode.OK, response.status)
        }
}
