package app.logdate.server.routes

import app.logdate.server.auth.JwtTokenService
import app.logdate.server.entitlements.Entitlement
import app.logdate.server.entitlements.EntitlementLimits
import app.logdate.server.entitlements.EntitlementService
import app.logdate.server.entitlements.EntitlementStatus
import app.logdate.server.entitlements.EntitlementTier
import app.logdate.server.entitlements.UsageCalculator
import app.logdate.server.logdate.asLogDateBackupRepository
import app.logdate.server.logdate.asLogDateCollectionsRepository
import app.logdate.server.logdate.asLogDateMediaBlobRepository
import app.logdate.server.logdate.asLogDateMediaRepository
import app.logdate.server.routes.support.backupUploadMultipartContent
import app.logdate.server.routes.support.createPermissiveBlobStorage
import app.logdate.server.routes.support.mediaUploadMultipartContent
import app.logdate.server.sync.InMemorySyncRepository
import app.logdate.server.sync.SyncMetricsRegistry
import app.logdate.shared.model.QuotaUsage
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation as ServerContentNegotiation

class QuotaRoutesTest {
    private val accountId = UUID.fromString("44444444-4444-4444-4444-444444444444")
    private val tokenService = JwtTokenService("quota-route-test-secret-key-32-chars-min")
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `quota endpoint reflects media and backup writes immediately`() =
        testApplication {
            configureQuotaBackedSync()
            val authHeader = "Bearer ${tokenService.generateAccessToken(accountId.toString())}"

            val initialUsage = fetchUsage(authHeader)
            assertEquals(0L, initialUsage.usedBytes)
            assertEquals(0, initialUsage.categories.size)

            val mediaUpload =
                client.post("/api/v1/media") {
                    header(HttpHeaders.Authorization, authHeader)
                    setBody(
                        mediaUploadMultipartContent(
                            contentId = "content-1",
                            fileName = "photo.jpg",
                            mimeType = "image/jpeg",
                            data = byteArrayOf(1, 2, 3, 4),
                        ),
                    )
                }
            assertEquals(HttpStatusCode.Created, mediaUpload.status)
            assertEquals(4L, fetchUsage(authHeader).usedBytes)

            val backupUpload =
                client.post("/api/v1/backups") {
                    header(HttpHeaders.Authorization, authHeader)
                    setBody(
                        backupUploadMultipartContent(
                            deviceId = "device-1",
                            manifest = """{"version":1}""",
                            data = byteArrayOf(5, 6, 7, 8, 9, 10),
                        ),
                    )
                }
            assertEquals(HttpStatusCode.Created, backupUpload.status)

            val usage = fetchUsage(authHeader)
            assertEquals(10_000L, usage.totalBytes)
            assertEquals(10L, usage.usedBytes)
            assertEquals(0, usage.categories.size)
        }

    private suspend fun io.ktor.server.testing.ApplicationTestBuilder.fetchUsage(authHeader: String): QuotaUsage {
        val response =
            client.get("/api/v1/quota") {
                header(HttpHeaders.Authorization, authHeader)
            }
        assertEquals(HttpStatusCode.OK, response.status)
        return json.decodeFromString<QuotaUsage>(response.bodyAsText())
    }

    private fun io.ktor.server.testing.ApplicationTestBuilder.configureQuotaBackedSync() {
        val repository = InMemorySyncRepository()
        val entitlementService =
            object : EntitlementService {
                override suspend fun resolve(accountId: UUID): Entitlement =
                    Entitlement(
                        planId = "standard",
                        tier = EntitlementTier.STANDARD,
                        status = EntitlementStatus.ACTIVE,
                        limits = EntitlementLimits(storageBytes = 10_000L, backupCount = 10),
                    )
            }
        val usageCalculator =
            object : UsageCalculator {
                override suspend fun storageBytes(accountId: UUID): Long =
                    repository.listAllMediaForUser(accountId).sumOf { it.sizeBytes } +
                        repository.listBackups(accountId).sumOf { it.sizeBytes }

                override suspend fun backupCount(accountId: UUID): Int = repository.listBackups(accountId).size
            }
        application {
            install(ServerContentNegotiation) {
                json(json)
            }
            routing {
                route("/api/v1") {
                    syncRoutes(
                        tokenService = tokenService,
                        mediaStorage = createPermissiveBlobStorage(),
                        metrics = SyncMetricsRegistry(),
                        collectionsRepository = repository.asLogDateCollectionsRepository(),
                        mediaBlobRepository = repository.asLogDateMediaRepository().asLogDateMediaBlobRepository(),
                        backupRepository = repository.asLogDateBackupRepository(),
                    )
                    quotaRoutes(
                        tokenService = tokenService,
                        entitlementService = entitlementService,
                        usageCalculator = usageCalculator,
                    )
                }
            }
        }
    }
}
