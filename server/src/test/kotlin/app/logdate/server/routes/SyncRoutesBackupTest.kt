package app.logdate.server.routes

import app.logdate.server.auth.JwtTokenService
import app.logdate.server.sync.GcsMediaStorage
import app.logdate.server.sync.InMemorySyncRepository
import app.logdate.server.sync.SyncMetricsRegistry
import app.logdate.shared.model.sync.BackupListResponse
import app.logdate.shared.model.sync.BackupUploadRequest
import app.logdate.shared.model.sync.BackupUploadResponse
import io.ktor.client.request.delete
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
import io.ktor.server.testing.testApplication
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation as ServerContentNegotiation
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import java.util.UUID
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SyncRoutesBackupTest {

    private val testUserId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000")
    private val jwtService = JwtTokenService("test-secret-for-jwt-signing-1234567890")
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `should upload backup successfully`() = testApplication {
        val repository = InMemorySyncRepository()
        val mockStorage = mockk<GcsMediaStorage>()
        val storageSlot = slot<ByteArray>()
        
        // Mock storage upload
        every { 
            mockStorage.uploadBackup(any(), any(), capture(storageSlot)) 
        } returns "users/$testUserId/backups/test-backup.enc"

        // Mock signed URL generation (if used) or just return direct URL logic
        every { mockStorage.getSignedDownloadUrl(any(), any()) } returns "https://signed-url.com"
        every { mockStorage.downloadMedia(any()) } returns "encrypted-data".toByteArray()

        application {
            install(ServerContentNegotiation) {
                json(json)
            }
            routing {
                route("/api/v1") {
                    syncRoutes(
                        repository = repository,
                        tokenService = jwtService,
                        mediaStorage = mockStorage,
                        metrics = SyncMetricsRegistry()
                    )
                }
            }
        }

        val token = jwtService.generateAccessToken(testUserId.toString())
        val manifest = """{"version":1,"timestamp":1234567890,"deviceId":"device-1","userId":"$testUserId","encryption":{}}"""
        val payload = "encrypted-data".toByteArray()

        // 1. Upload Backup
        val response = client.post("/api/v1/sync/backups") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(
                json.encodeToString(
                    BackupUploadRequest(
                        deviceId = "device-1",
                        manifest = manifest,
                        data = payload
                    )
                )
            )
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val uploadResp = json.decodeFromString<BackupUploadResponse>(response.bodyAsText())
        assertTrue(uploadResp.id.isNotBlank())
        assertEquals(payload.size.toLong(), uploadResp.sizeBytes)

        // Verify storage was called with encrypted data (LDBK1 prefix)
        assertTrue(storageSlot.captured.size > payload.size, "Encrypted data should be larger than plaintext")
        assertTrue(
            String(storageSlot.captured.copyOfRange(0, 5), Charsets.UTF_8) == "LDBK1",
            "Uploaded data should have LDBK1 prefix"
        )

        // 2. List Backups
        val listResp = client.get("/api/v1/sync/backups") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.OK, listResp.status)
        val backups = json.decodeFromString<BackupListResponse>(listResp.bodyAsText()).backups
        assertEquals(1, backups.size)
        assertEquals(uploadResp.id, backups.first().id)
        assertEquals(manifest, backups.first().manifest)

        // 3. Download Backup
        val downloadResp = client.get("/api/v1/sync/backups/${uploadResp.id}") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.OK, downloadResp.status)
        assertEquals("encrypted-data", downloadResp.bodyAsText())
        
    }
}
