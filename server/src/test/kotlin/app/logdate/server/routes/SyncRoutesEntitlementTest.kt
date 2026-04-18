package app.logdate.server.routes

import app.logdate.server.auth.JwtTokenService
import app.logdate.server.entitlements.Entitlement
import app.logdate.server.entitlements.EntitlementEnforcer
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
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation as ServerContentNegotiation

/**
 * Verifies the 402 Payment Required path: when an account's entitlement caps storage or backups,
 * the sync endpoints refuse the write with a structured QUOTA_EXCEEDED payload so clients can
 * render a tier-upgrade prompt instead of a generic failure.
 */
class SyncRoutesEntitlementTest {
    private val accountId = UUID.fromString("33333333-3333-3333-3333-333333333333")
    private val jwtService = JwtTokenService("entitlement-test-secret-key-32-chars-min-abc")
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `media upload returns 402 when storage quota is exhausted`() =
        testApplication {
            configureSync(
                entitlement = tiered(storage = 1_000L, backups = 10),
                currentBytes = 900L,
                currentBackups = 0,
            )
            val token = jwtService.generateAccessToken(accountId.toString())

            val response =
                client.post("/api/v1/media") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    setBody(
                        mediaUploadMultipartContent(
                            contentId = "content-over-quota",
                            fileName = "big.jpg",
                            mimeType = "image/jpeg",
                            data = ByteArray(500),
                        ),
                    )
                }

            assertEquals(HttpStatusCode.PaymentRequired, response.status)
            val body = response.bodyAsText()
            assertTrue(body.contains("QUOTA_EXCEEDED"))
            assertTrue(body.contains("STORAGE_BYTES"))
        }

    @Test
    fun `backup upload returns 402 when backup count limit is reached`() =
        testApplication {
            configureSync(
                entitlement = tiered(storage = 10_000_000L, backups = 2),
                currentBytes = 0L,
                currentBackups = 2,
            )
            val token = jwtService.generateAccessToken(accountId.toString())

            val response =
                client.post("/api/v1/backups") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    setBody(
                        backupUploadMultipartContent(
                            deviceId = "device-1",
                            manifest = """{"version":1}""",
                            data = "extra".toByteArray(),
                        ),
                    )
                }

            assertEquals(HttpStatusCode.PaymentRequired, response.status)
            assertTrue(response.bodyAsText().contains("BACKUP_COUNT"))
        }

    @Test
    fun `unlimited plan lets media upload succeed regardless of usage`() =
        testApplication {
            configureSync(
                entitlement =
                    Entitlement(
                        planId = "unlimited",
                        tier = EntitlementTier.UNLIMITED,
                        status = EntitlementStatus.SELF_HOST,
                        limits = EntitlementLimits(storageBytes = null, backupCount = null),
                    ),
                currentBytes = 999_999_999_999L,
                currentBackups = 9_999,
            )
            val token = jwtService.generateAccessToken(accountId.toString())

            val response =
                client.post("/api/v1/media") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    setBody(
                        mediaUploadMultipartContent(
                            contentId = "content-ok",
                            fileName = "a.jpg",
                            mimeType = "image/jpeg",
                            data = "payload".toByteArray(),
                        ),
                    )
                }

            assertEquals(HttpStatusCode.Created, response.status, response.bodyAsText())
        }

    private fun tiered(
        storage: Long?,
        backups: Int?,
    ): Entitlement =
        Entitlement(
            planId = "standard",
            tier = EntitlementTier.STANDARD,
            status = EntitlementStatus.ACTIVE,
            limits = EntitlementLimits(storageBytes = storage, backupCount = backups),
        )

    private fun ApplicationTestBuilder.configureSync(
        entitlement: Entitlement,
        currentBytes: Long,
        currentBackups: Int,
    ) {
        val repository = InMemorySyncRepository()
        val enforcer =
            EntitlementEnforcer(
                entitlementService =
                    object : EntitlementService {
                        override suspend fun resolve(accountId: UUID): Entitlement = entitlement
                    },
                usageCalculator =
                    object : UsageCalculator {
                        override suspend fun storageBytes(accountId: UUID): Long = currentBytes

                        override suspend fun backupCount(accountId: UUID): Int = currentBackups
                    },
            )
        val storage = createPermissiveBlobStorage()
        application {
            install(ServerContentNegotiation) { json(json) }
            routing {
                route("/api/v1") {
                    syncRoutes(
                        tokenService = jwtService,
                        mediaStorage = storage,
                        metrics = SyncMetricsRegistry(),
                        collectionsRepository = repository.asLogDateCollectionsRepository(),
                        mediaBlobRepository = repository.asLogDateMediaRepository().asLogDateMediaBlobRepository(),
                        backupRepository = repository.asLogDateBackupRepository(),
                        entitlementEnforcer = enforcer,
                    )
                }
            }
        }
    }
}
