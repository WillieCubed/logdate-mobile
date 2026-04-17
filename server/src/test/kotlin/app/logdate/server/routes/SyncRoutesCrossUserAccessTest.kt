package app.logdate.server.routes

import app.logdate.server.auth.JwtTokenService
import app.logdate.server.logdate.LogDateBlobStorage
import app.logdate.server.logdate.LogDateBlobWriteRequest
import app.logdate.server.logdate.asLogDateBackupRepository
import app.logdate.server.logdate.asLogDateCollectionsRepository
import app.logdate.server.logdate.asLogDateMediaBlobRepository
import app.logdate.server.logdate.asLogDateMediaRepository
import app.logdate.server.routes.support.backupUploadMultipartContent
import app.logdate.server.routes.support.mediaUploadMultipartContent
import app.logdate.server.sync.InMemorySyncRepository
import app.logdate.server.sync.SyncMetricsRegistry
import app.logdate.shared.model.sync.BackupUploadResponse
import app.logdate.shared.model.sync.MediaUploadResponse
import io.ktor.client.request.delete
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
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.mockk.every
import io.mockk.mockk
import kotlinx.serialization.json.Json
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.test.Test
import kotlin.test.assertEquals
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation as ServerContentNegotiation

/**
 * Locks in the invariant that one authenticated user cannot read another user's media or backup
 * binaries. The HTTP handler relies on repository lookups scoped by `userId`, so a query with a
 * foreign resource id returns null → 404. If a future refactor ever replaces those scoped lookups
 * with unscoped ones, these tests catch it.
 */
class SyncRoutesCrossUserAccessTest {
    private val alice = UUID.fromString("11111111-1111-1111-1111-111111111111")
    private val mallory = UUID.fromString("22222222-2222-2222-2222-222222222222")
    private val jwtService = JwtTokenService("cross-user-access-test-secret-key-32-chars-min")
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `mallory cannot download alice's media by id`() =
        testApplication {
            configureSync()
            val aliceToken = jwtService.generateAccessToken(alice.toString())
            val malloryToken = jwtService.generateAccessToken(mallory.toString())

            val upload =
                client.post("/api/v1/media") {
                    header(HttpHeaders.Authorization, "Bearer $aliceToken")
                    setBody(
                        mediaUploadMultipartContent(
                            contentId = "content-alice-1",
                            fileName = "alice.jpg",
                            mimeType = "image/jpeg",
                            data = "alices-private-photo".toByteArray(),
                        ),
                    )
                }
            assertEquals(HttpStatusCode.Created, upload.status, upload.bodyAsText())
            val mediaId = json.decodeFromString<MediaUploadResponse>(upload.bodyAsText()).mediaId

            val ok =
                client.get("/api/v1/media/$mediaId/binary") {
                    header(HttpHeaders.Authorization, "Bearer $aliceToken")
                }
            assertEquals(HttpStatusCode.OK, ok.status)
            assertEquals("alices-private-photo", ok.bodyAsText())

            val forbidden =
                client.get("/api/v1/media/$mediaId/binary") {
                    header(HttpHeaders.Authorization, "Bearer $malloryToken")
                }
            assertEquals(HttpStatusCode.NotFound, forbidden.status)
        }

    @Test
    fun `mallory cannot read alice's media metadata by id`() =
        testApplication {
            configureSync()
            val aliceToken = jwtService.generateAccessToken(alice.toString())
            val malloryToken = jwtService.generateAccessToken(mallory.toString())

            val upload =
                client.post("/api/v1/media") {
                    header(HttpHeaders.Authorization, "Bearer $aliceToken")
                    setBody(
                        mediaUploadMultipartContent(
                            contentId = "content-alice-2",
                            fileName = "alice.jpg",
                            mimeType = "image/jpeg",
                            data = "payload".toByteArray(),
                        ),
                    )
                }
            assertEquals(HttpStatusCode.Created, upload.status, upload.bodyAsText())
            val mediaId = json.decodeFromString<MediaUploadResponse>(upload.bodyAsText()).mediaId

            val forbidden =
                client.get("/api/v1/media/$mediaId") {
                    header(HttpHeaders.Authorization, "Bearer $malloryToken")
                }
            assertEquals(HttpStatusCode.NotFound, forbidden.status)
        }

    @Test
    fun `mallory's DELETE on alice's media leaves it intact`() =
        testApplication {
            configureSync()
            val aliceToken = jwtService.generateAccessToken(alice.toString())
            val malloryToken = jwtService.generateAccessToken(mallory.toString())

            val upload =
                client.post("/api/v1/media") {
                    header(HttpHeaders.Authorization, "Bearer $aliceToken")
                    setBody(
                        mediaUploadMultipartContent(
                            contentId = "content-alice-3",
                            fileName = "alice.jpg",
                            mimeType = "image/jpeg",
                            data = "payload".toByteArray(),
                        ),
                    )
                }
            assertEquals(HttpStatusCode.Created, upload.status, upload.bodyAsText())
            val mediaId = json.decodeFromString<MediaUploadResponse>(upload.bodyAsText()).mediaId

            // Mallory's DELETE is a no-op scoped to her own data; the handler is idempotent so the
            // status isn't what carries the guarantee — it's that Alice's data survives.
            client.delete("/api/v1/media/$mediaId") {
                header(HttpHeaders.Authorization, "Bearer $malloryToken")
            }

            val aliceGet =
                client.get("/api/v1/media/$mediaId/binary") {
                    header(HttpHeaders.Authorization, "Bearer $aliceToken")
                }
            assertEquals(HttpStatusCode.OK, aliceGet.status)
            assertEquals("payload", aliceGet.bodyAsText())
        }

    @Test
    fun `mallory cannot download alice's backup by id`() =
        testApplication {
            configureSync()
            val aliceToken = jwtService.generateAccessToken(alice.toString())
            val malloryToken = jwtService.generateAccessToken(mallory.toString())

            val manifest = """{"version":1,"timestamp":1234567890,"deviceId":"device-alice","userId":"$alice","encryption":{}}"""
            val upload =
                client.post("/api/v1/backups") {
                    header(HttpHeaders.Authorization, "Bearer $aliceToken")
                    setBody(
                        backupUploadMultipartContent(
                            deviceId = "device-alice",
                            manifest = manifest,
                            data = "alices-backup-contents".toByteArray(),
                        ),
                    )
                }
            assertEquals(HttpStatusCode.Created, upload.status, upload.bodyAsText())
            val backupId = json.decodeFromString<BackupUploadResponse>(upload.bodyAsText()).id

            val ok =
                client.get("/api/v1/backups/$backupId/binary") {
                    header(HttpHeaders.Authorization, "Bearer $aliceToken")
                }
            assertEquals(HttpStatusCode.OK, ok.status)

            val forbidden =
                client.get("/api/v1/backups/$backupId/binary") {
                    header(HttpHeaders.Authorization, "Bearer $malloryToken")
                }
            assertEquals(HttpStatusCode.NotFound, forbidden.status)
        }

    @Test
    fun `mallory cannot read alice's backup metadata and cannot destroy it`() =
        testApplication {
            configureSync()
            val aliceToken = jwtService.generateAccessToken(alice.toString())
            val malloryToken = jwtService.generateAccessToken(mallory.toString())

            val manifest = """{"version":1,"timestamp":1234567890,"deviceId":"device-alice","userId":"$alice","encryption":{}}"""
            val upload =
                client.post("/api/v1/backups") {
                    header(HttpHeaders.Authorization, "Bearer $aliceToken")
                    setBody(
                        backupUploadMultipartContent(
                            deviceId = "device-alice",
                            manifest = manifest,
                            data = "alices-backup".toByteArray(),
                        ),
                    )
                }
            val backupId = json.decodeFromString<BackupUploadResponse>(upload.bodyAsText()).id

            val readAttempt =
                client.get("/api/v1/backups/$backupId") {
                    header(HttpHeaders.Authorization, "Bearer $malloryToken")
                }
            assertEquals(HttpStatusCode.NotFound, readAttempt.status)

            // Mallory's DELETE is idempotent and scoped to her own row set; it returns 204, but the
            // real security property we care about is that Alice's backup still exists afterwards.
            client.delete("/api/v1/backups/$backupId") {
                header(HttpHeaders.Authorization, "Bearer $malloryToken")
            }

            val aliceGet =
                client.get("/api/v1/backups/$backupId") {
                    header(HttpHeaders.Authorization, "Bearer $aliceToken")
                }
            assertEquals(HttpStatusCode.OK, aliceGet.status)
        }

    private fun ApplicationTestBuilder.configureSync() {
        val repository = InMemorySyncRepository()
        val storage = createPermissiveStorage()
        application {
            install(ServerContentNegotiation) {
                json(json)
            }
            routing {
                route("/api/v1") {
                    syncRoutes(
                        tokenService = jwtService,
                        mediaStorage = storage,
                        metrics = SyncMetricsRegistry(),
                        collectionsRepository = repository.asLogDateCollectionsRepository(),
                        mediaBlobRepository = repository.asLogDateMediaRepository().asLogDateMediaBlobRepository(),
                        backupRepository = repository.asLogDateBackupRepository(),
                    )
                }
            }
        }
    }

    /**
     * In-memory fake that accepts any namespace (BACKUP or MEDIA). The canonical
     * [app.logdate.server.routes.support.createBackupStorageMock] refuses non-BACKUP writes, which
     * makes it unsuitable for tests that exercise both media and backup paths.
     */
    private fun createPermissiveStorage(): LogDateBlobStorage {
        val blobs = ConcurrentHashMap<String, ByteArray>()
        val storage = mockk<LogDateBlobStorage>()
        every { storage.putBlob(any()) } answers {
            val req = firstArg<LogDateBlobWriteRequest>()
            val path = "ns/${req.namespace.name.lowercase()}/${req.ownerId}/${req.blobId}"
            blobs[path] = req.bytes
            path
        }
        every { storage.getBlob(any()) } answers {
            blobs[firstArg<String>()]
        }
        every { storage.deleteBlob(any()) } answers {
            blobs.remove(firstArg<String>()) != null
        }
        every { storage.getSignedDownloadUrl(any(), any()) } returns "https://signed-url.example.com"
        return storage
    }
}
