package app.logdate.server.routes

import app.logdate.server.auth.JwtTokenService
import app.logdate.server.logdate.asLogDateBackupRepository
import app.logdate.server.logdate.asLogDateCollectionsRepository
import app.logdate.server.logdate.asLogDateMediaBlobRepository
import app.logdate.server.logdate.asLogDateMediaRepository
import app.logdate.server.routes.support.backupUploadMultipartContent
import app.logdate.server.routes.support.createBackupStorageMock
import app.logdate.server.sync.BackupRecord
import app.logdate.server.sync.InMemorySyncRepository
import app.logdate.server.sync.SyncMetricsRegistry
import app.logdate.shared.model.sync.BackupListResponse
import app.logdate.shared.model.sync.BackupUploadResponse
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
import kotlin.test.assertTrue
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation as ServerContentNegotiation

class SyncRoutesBackupTest {
    private val testUserId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000")
    private val jwtService = JwtTokenService("test-secret-for-jwt-signing-1234567890")
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `should upload backup successfully`() =
        testApplication {
            val repository = InMemorySyncRepository()
            val mockHarness = createBackupStorageMock("users/$testUserId/backups/test-backup.enc")
            val mockStorage = mockHarness.storage

            application {
                install(ServerContentNegotiation) {
                    json(json)
                }
                routing {
                    route("/api/v1") {
                        syncRoutes(
                            tokenService = jwtService,
                            mediaStorage = mockStorage,
                            metrics = SyncMetricsRegistry(),
                            collectionsRepository = repository.asLogDateCollectionsRepository(),
                            mediaBlobRepository = repository.asLogDateMediaRepository().asLogDateMediaBlobRepository(),
                            backupRepository = repository.asLogDateBackupRepository(),
                        )
                    }
                }
            }

            val token = jwtService.generateAccessToken(testUserId.toString())
            val manifest = """{"version":1,"timestamp":1234567890,"deviceId":"device-1","userId":"$testUserId","encryption":{}}"""
            val payload = "encrypted-data".toByteArray()

            // 1. Upload Backup
            val response =
                client.post("/api/v1/backups") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    setBody(
                        backupUploadMultipartContent(
                            deviceId = "device-1",
                            manifest = manifest,
                            data = payload,
                        ),
                    )
                }

            assertEquals(HttpStatusCode.Created, response.status)
            val uploadResp = json.decodeFromString<BackupUploadResponse>(response.bodyAsText())
            assertTrue(uploadResp.id.isNotBlank())
            assertEquals(payload.size.toLong(), uploadResp.sizeBytes)

            // Verify storage was called with encrypted data (LDBK1 prefix)
            assertTrue(
                mockHarness.uploadedRequest.captured.bytes.size > payload.size,
                "Encrypted data should be larger than plaintext",
            )
            assertTrue(
                String(
                    mockHarness.uploadedRequest.captured.bytes
                        .copyOfRange(0, 5),
                    Charsets.UTF_8,
                ) == "LDBK1",
                "Uploaded data should have LDBK1 prefix",
            )

            // 2. List Backups
            val listResp =
                client.get("/api/v1/backups") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                }
            assertEquals(HttpStatusCode.OK, listResp.status)
            val backups = json.decodeFromString<BackupListResponse>(listResp.bodyAsText()).backups
            assertEquals(1, backups.size)
            assertEquals(uploadResp.id, backups.first().id)
            assertEquals(manifest, backups.first().manifest)

            // 3. Download Backup
            val downloadResp =
                client.get("/api/v1/backups/${uploadResp.id}/binary") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                }
            assertEquals(HttpStatusCode.OK, downloadResp.status)
            assertEquals("encrypted-data", downloadResp.bodyAsText())
        }

    @Test
    fun `backup upload returns 503 when storage is not configured`() =
        testApplication {
            val repository = InMemorySyncRepository()
            application {
                install(ServerContentNegotiation) {
                    json(json)
                }
                routing {
                    route("/api/v1") {
                        syncRoutes(
                            tokenService = jwtService,
                            mediaStorage = null,
                            metrics = SyncMetricsRegistry(),
                            collectionsRepository = repository.asLogDateCollectionsRepository(),
                            mediaBlobRepository = repository.asLogDateMediaRepository().asLogDateMediaBlobRepository(),
                            backupRepository = repository.asLogDateBackupRepository(),
                        )
                    }
                }
            }

            val token = jwtService.generateAccessToken(testUserId.toString())
            val response =
                client.post("/api/v1/backups") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    setBody(
                        backupUploadMultipartContent(
                            deviceId = "device-1",
                            manifest = """{"version":1}""",
                            data = "encrypted-data".toByteArray(),
                        ),
                    )
                }

            assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
            assertTrue(response.bodyAsText().contains("BACKUP_STORAGE_UNAVAILABLE"))
        }

    @Test
    fun `backup download returns 503 when storage is not configured`() =
        testApplication {
            val repository = InMemorySyncRepository()
            val backupId = UUID.randomUUID()
            repository.createBackupRecord(
                testUserId,
                BackupRecord(
                    id = backupId,
                    userId = testUserId,
                    deviceId = "device-1",
                    manifest = """{"version":1}""",
                    storagePath = "users/$testUserId/backups/$backupId.enc",
                    createdAt = 1234L,
                    sizeBytes = 100L,
                ),
            )

            application {
                install(ServerContentNegotiation) {
                    json(json)
                }
                routing {
                    route("/api/v1") {
                        syncRoutes(
                            tokenService = jwtService,
                            mediaStorage = null,
                            metrics = SyncMetricsRegistry(),
                            collectionsRepository = repository.asLogDateCollectionsRepository(),
                            mediaBlobRepository = repository.asLogDateMediaRepository().asLogDateMediaBlobRepository(),
                            backupRepository = repository.asLogDateBackupRepository(),
                        )
                    }
                }
            }

            val token = jwtService.generateAccessToken(testUserId.toString())
            val response =
                client.get("/api/v1/backups/$backupId/binary") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                }

            assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
            assertTrue(response.bodyAsText().contains("BACKUP_STORAGE_UNAVAILABLE"))
        }
}
