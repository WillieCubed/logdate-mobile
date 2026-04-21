package app.logdate.server.routes

import app.logdate.server.auth.JwtTokenService
import app.logdate.server.auth.TokenService
import app.logdate.server.logdate.asLogDateBackupRepository
import app.logdate.server.logdate.asLogDateCollectionsRepository
import app.logdate.server.logdate.asLogDateMediaBlobRepository
import app.logdate.server.logdate.asLogDateMediaRepository
import app.logdate.server.routes.support.backupMultipartWithFields
import app.logdate.server.routes.support.mediaMultipartWithFields
import app.logdate.server.sync.BackupRecord
import app.logdate.server.sync.GcsMediaStorage
import app.logdate.server.sync.InMemorySyncRepository
import app.logdate.server.sync.MediaAccessPolicy
import app.logdate.server.sync.MediaRecord
import app.logdate.server.sync.SyncMetricsRegistry
import app.logdate.shared.model.sync.DeviceId
import io.ktor.client.request.get
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
import io.ktor.server.testing.testApplication
import io.mockk.every
import io.mockk.mockk
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val SYNC_HMAC_KEY = "sync-route-secret"
private const val SYNC_VALIDATION_HMAC_KEY = "sync-validation-secret"
private const val SYNC_ROUTE_VALIDATION_HMAC_KEY = "sync-route-validation-secret"

/**
 * Validation and error-handling tests for the synchronization API endpoints.
 *
 * This class focuses on the robustness of the sync routes, ensuring that they correctly
 * enforce authentication requirements, validate multipart form-data structure, and handle
 * edge cases such as missing configuration or storage failures. It also verifies that the
 * system gracefully falls back to direct binary downloads if signed URL generation fails.
 */
class SyncRoutesValidationTest {
    @Test
    fun `sync status returns unauthorized when auth header is missing`() =
        testApplication {
            val env = configureSyncRoutes()
            val response = client.get("/api/v1/ops/sync/status")
            assertEquals(HttpStatusCode.Unauthorized, response.status)
            assertTrue(response.bodyAsText().contains("UNAUTHORIZED"))
            // Ensure metrics endpoint still authenticates with same helper.
            val metrics = client.get("/api/v1/ops/sync/metrics")
            assertEquals(HttpStatusCode.Unauthorized, metrics.status)
            assertTrue(env.repository.status(UUID.randomUUID()).lastTimestamp > 0)
        }

    @Test
    fun `sync status returns unauthorized when token is invalid or malformed`() =
        testApplication {
            configureSyncRoutes()

            val invalid =
                client.get("/api/v1/ops/sync/status") {
                    header(HttpHeaders.Authorization, "Bearer not-a-valid-token")
                }
            assertEquals(HttpStatusCode.Unauthorized, invalid.status)

            val jwt = JwtTokenService(secret = SYNC_VALIDATION_HMAC_KEY)
            val nonUuidToken = jwt.generateAccessToken("not-a-uuid")
            val malformedPayload =
                client.get("/api/v1/ops/sync/status") {
                    header(HttpHeaders.Authorization, "Bearer $nonUuidToken")
                }
            assertEquals(HttpStatusCode.Unauthorized, malformedPayload.status)
            assertTrue(malformedPayload.bodyAsText().contains("UNAUTHORIZED"))
        }

    @Test
    fun `sync routes return server misconfigured when token service is missing`() =
        testApplication {
            configureSyncRoutes(tokenService = null)

            val response = client.get("/api/v1/ops/sync/status")
            assertEquals(HttpStatusCode.InternalServerError, response.status)
            assertTrue(response.bodyAsText().contains("SERVER_MISCONFIGURED"))
        }

    @Test
    fun `media upload validates multipart and field constraints`() =
        testApplication {
            val env = configureSyncRoutes()
            val token = env.tokenService.generateAccessToken(UUID.randomUUID().toString())
            val auth = "Bearer $token"

            val wrongBody =
                client.post("/api/v1/media") {
                    header(HttpHeaders.Authorization, auth)
                    contentType(ContentType.Application.Json)
                    setBody("{\"unexpected\":true}")
                }
            assertEquals(HttpStatusCode.BadRequest, wrongBody.status)
            assertTrue(wrongBody.bodyAsText().contains("Expected multipart/form-data body"))

            val missingContentId =
                client.post("/api/v1/media") {
                    header(HttpHeaders.Authorization, auth)
                    setBody(
                        mediaMultipartWithFields(
                            includeContentId = false,
                            includeFileName = true,
                            includeMimeType = true,
                            includeSizeBytes = true,
                            includeDeviceId = true,
                            includeData = true,
                            sizeBytes = 4,
                            payload = byteArrayOf(1, 2, 3, 4),
                        ),
                    )
                }
            assertEquals(HttpStatusCode.BadRequest, missingContentId.status)
            assertTrue(missingContentId.bodyAsText().contains("contentId"))

            val nonPositiveSize =
                client.post("/api/v1/media") {
                    header(HttpHeaders.Authorization, auth)
                    setBody(
                        mediaMultipartWithFields(
                            includeContentId = true,
                            includeFileName = true,
                            includeMimeType = true,
                            includeSizeBytes = true,
                            includeDeviceId = true,
                            includeData = true,
                            sizeBytes = 0,
                            payload = byteArrayOf(1, 2, 3, 4),
                        ),
                    )
                }
            assertEquals(HttpStatusCode.BadRequest, nonPositiveSize.status)
            assertTrue(nonPositiveSize.bodyAsText().contains("sizeBytes must be greater than 0"))
        }

    @Test
    fun `backup upload validates multipart and empty payload`() =
        testApplication {
            val env = configureSyncRoutes(mediaStorage = mockk<GcsMediaStorage>(relaxed = true))
            val token = env.tokenService.generateAccessToken(UUID.randomUUID().toString())
            val auth = "Bearer $token"

            val wrongBody =
                client.post("/api/v1/backups") {
                    header(HttpHeaders.Authorization, auth)
                    contentType(ContentType.Application.Json)
                    setBody("{}")
                }
            assertEquals(HttpStatusCode.BadRequest, wrongBody.status)
            assertTrue(wrongBody.bodyAsText().contains("Expected multipart/form-data body"))

            val emptyData =
                client.post("/api/v1/backups") {
                    header(HttpHeaders.Authorization, auth)
                    setBody(
                        backupMultipartWithFields(
                            includeDeviceId = true,
                            includeManifest = true,
                            includeData = true,
                            payload = byteArrayOf(),
                        ),
                    )
                }
            assertEquals(HttpStatusCode.BadRequest, emptyData.status)
            assertTrue(emptyData.bodyAsText().contains("Backup payload must not be empty"))
        }

    @Test
    fun `signed URL failures fall back to local binary endpoints`() =
        testApplication {
            val userId = UUID.randomUUID()
            val repository = InMemorySyncRepository()
            val mediaStorage = mockk<GcsMediaStorage>()
            every { mediaStorage.getSignedDownloadUrl(any(), any()) } throws IllegalStateException("sign failed")

            val mediaId = "media-1"
            repository.upsertMedia(
                userId,
                MediaRecord(
                    mediaId = mediaId,
                    contentId = "content-1",
                    userId = userId,
                    fileName = "f.jpg",
                    mimeType = "image/jpeg",
                    sizeBytes = 3,
                    data = byteArrayOf(1, 2, 3),
                    storagePath = "users/$userId/media/$mediaId/f.jpg",
                    createdAt = 1,
                    serverVersion = 1,
                    deviceId = DeviceId("dev"),
                ),
            )

            val backupId = UUID.randomUUID()
            repository.createBackupRecord(
                userId,
                BackupRecord(
                    id = backupId,
                    userId = userId,
                    deviceId = "dev",
                    manifest = "{}",
                    storagePath = "users/$userId/backups/$backupId.enc",
                    createdAt = 1,
                    sizeBytes = 3,
                ),
            )

            val tokenService = JwtTokenService(secret = SYNC_ROUTE_VALIDATION_HMAC_KEY)
            application {
                install(ContentNegotiation) { json() }
                routing {
                    route("/api/v1") {
                        syncRoutes(
                            tokenService = tokenService,
                            mediaStorage = mediaStorage,
                            metrics = SyncMetricsRegistry(),
                            mediaAccessPolicy = MediaAccessPolicy(useSignedUrls = true, signedUrlTtlHours = 1),
                            collectionsRepository = repository.asLogDateCollectionsRepository(),
                            mediaBlobRepository = repository.asLogDateMediaRepository().asLogDateMediaBlobRepository(),
                            backupRepository = repository.asLogDateBackupRepository(),
                        )
                    }
                }
            }

            val auth = "Bearer ${tokenService.generateAccessToken(userId.toString())}"
            val mediaResponse =
                client.get("/api/v1/media/$mediaId") {
                    header(HttpHeaders.Authorization, auth)
                }
            assertEquals(HttpStatusCode.OK, mediaResponse.status)
            assertTrue(mediaResponse.bodyAsText().contains("/api/v1/media/$mediaId/binary"))

            val backupResponse =
                client.get("/api/v1/backups/$backupId") {
                    header(HttpHeaders.Authorization, auth)
                }
            assertEquals(HttpStatusCode.OK, backupResponse.status)
            assertTrue(backupResponse.bodyAsText().contains("/api/v1/backups/$backupId/binary"))
        }

    /**
     * Encapsulates the test environment for synchronization routes.
     */
    private data class SyncEnv(
        val repository: InMemorySyncRepository,
        val tokenService: JwtTokenService,
    )

    private fun io.ktor.server.testing.ApplicationTestBuilder.configureSyncRoutes(
        tokenService: TokenService? = JwtTokenService(secret = SYNC_HMAC_KEY),
        mediaStorage: GcsMediaStorage? = null,
    ): SyncEnv {
        val repository = InMemorySyncRepository()
        val jwt = JwtTokenService(secret = SYNC_HMAC_KEY)
        application {
            install(ContentNegotiation) { json() }
            routing {
                route("/api/v1") {
                    syncRoutes(
                        tokenService = tokenService,
                        mediaStorage = mediaStorage,
                        metrics = SyncMetricsRegistry(),
                        collectionsRepository = repository.asLogDateCollectionsRepository(),
                        mediaBlobRepository = repository.asLogDateMediaRepository().asLogDateMediaBlobRepository(),
                        backupRepository = repository.asLogDateBackupRepository(),
                    )
                }
            }
        }
        return SyncEnv(repository = repository, tokenService = jwt)
    }
}
