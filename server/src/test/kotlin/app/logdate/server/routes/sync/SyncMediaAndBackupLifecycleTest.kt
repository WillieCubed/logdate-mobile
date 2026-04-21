package app.logdate.server.routes.sync

import app.logdate.server.auth.JwtTokenService
import app.logdate.server.crypto.EncryptionService
import app.logdate.server.crypto.ProcessedPayload
import app.logdate.server.logdate.LogDateBlobNamespace
import app.logdate.server.logdate.asLogDateBackupRepository
import app.logdate.server.logdate.asLogDateCollectionsRepository
import app.logdate.server.logdate.asLogDateMediaBlobRepository
import app.logdate.server.logdate.asLogDateMediaRepository
import app.logdate.server.routes.support.backupMultipartWithFields
import app.logdate.server.routes.support.mediaMultipartWithFields
import app.logdate.server.routes.syncRoutes
import app.logdate.server.sync.GcsMediaStorage
import app.logdate.server.sync.InMemorySyncRepository
import app.logdate.server.sync.MediaAccessPolicy
import app.logdate.server.sync.MediaRecord
import app.logdate.server.sync.SyncMetricsRegistry
import app.logdate.shared.model.sync.DeviceId
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
import kotlin.time.Clock

/**
 * Integration tests for the media and backup lifecycle within the Sync API.
 *
 * This class validates the complex interaction between the sync routes, encryption
 * services, and external blob storage. It covers end-to-end scenarios including multipart
 * uploads, storage-level failures, decryption errors, and the retrieval of binaries via
 * both direct downloads and signed URLs.
 */
class SyncMediaAndBackupLifecycleTest {
    @Test
    fun `media and backup endpoints surface storage failures and preserve backup list behavior`() =
        testApplication {
            val repository = InMemorySyncRepository()
            val tokenService = JwtTokenService("sync-binary-secret")
            val storage = mockk<GcsMediaStorage>()

            every {
                storage.putBlob(match { it.namespace == LogDateBlobNamespace.MEDIA })
            } throws IllegalStateException("media-upload-fail")
            every { storage.putBlob(match { it.namespace == LogDateBlobNamespace.BACKUP }) } returns "users/u/backups/b.enc"
            every { storage.getBlob(any()) } returns null
            every { storage.deleteBlob(any()) } returns true
            every { storage.getSignedDownloadUrl(any(), any()) } returns "https://signed.example/object"

            application {
                install(ContentNegotiation) { json() }
                routing {
                    route("/api/v1") {
                        syncRoutes(
                            tokenService = tokenService,
                            mediaStorage = storage,
                            metrics = SyncMetricsRegistry(),
                            mediaAccessPolicy = MediaAccessPolicy(useSignedUrls = true, signedUrlTtlHours = 1),
                            collectionsRepository = repository.asLogDateCollectionsRepository(),
                            mediaBlobRepository = repository.asLogDateMediaRepository().asLogDateMediaBlobRepository(),
                            backupRepository = repository.asLogDateBackupRepository(),
                        )
                    }
                }
            }

            val auth = "Bearer ${tokenService.generateAccessToken(UUID.randomUUID().toString())}"
            val payload = byteArrayOf(1, 2, 3, 4)

            val mediaUploadFailure =
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
                            sizeBytes = 4,
                            payload = payload,
                        ),
                    )
                }
            assertEquals(HttpStatusCode.InternalServerError, mediaUploadFailure.status)
            assertTrue(mediaUploadFailure.bodyAsText().contains("MEDIA_UPLOAD_FAILED"))

            val backupUpload =
                client.post("/api/v1/backups") {
                    header(HttpHeaders.Authorization, auth)
                    setBody(
                        backupMultipartWithFields(
                            includeDeviceId = true,
                            includeManifest = true,
                            includeData = true,
                            payload = payload,
                        ),
                    )
                }
            assertEquals(HttpStatusCode.Created, backupUpload.status)

            val listed = client.get("/api/v1/backups") { header(HttpHeaders.Authorization, auth) }
            assertEquals(HttpStatusCode.OK, listed.status)
        }

    @Test
    fun `binary endpoints cover encryption failures not-found responses and signed urls`() =
        testApplication {
            val repository = InMemorySyncRepository()
            val tokenService = JwtTokenService("sync-binary-secret")
            val storage = mockk<GcsMediaStorage>()
            val encryptionService = mockk<EncryptionService>()
            val userId = UUID.randomUUID()
            val auth = "Bearer ${tokenService.generateAccessToken(userId.toString())}"

            every { encryptionService.processMediaUpload(any(), any(), any(), any()) } throws IllegalStateException("enc media")
            every { encryptionService.processBackupUpload(any(), any(), any()) } throws IllegalStateException("enc backup")
            every { encryptionService.processMediaDownload(any(), any()) } answers { firstArg() }
            every { encryptionService.processBackupDownload(any(), any()) } answers { firstArg() }
            every { storage.putBlob(match { it.namespace == LogDateBlobNamespace.BACKUP }) } returns "users/u/backups/b.enc"
            every { storage.getSignedDownloadUrl(any(), any()) } returns "https://signed.example/object"

            application {
                install(ContentNegotiation) { json() }
                routing {
                    route("/api/v1") {
                        syncRoutes(
                            tokenService = tokenService,
                            mediaStorage = storage,
                            metrics = SyncMetricsRegistry(),
                            mediaAccessPolicy = MediaAccessPolicy(useSignedUrls = true, signedUrlTtlHours = 1),
                            encryptionService = encryptionService,
                            collectionsRepository = repository.asLogDateCollectionsRepository(),
                            mediaBlobRepository = repository.asLogDateMediaRepository().asLogDateMediaBlobRepository(),
                            backupRepository = repository.asLogDateBackupRepository(),
                        )
                    }
                }
            }

            val mediaEncryptFail =
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
                            sizeBytes = 4,
                            payload = byteArrayOf(1, 2, 3, 4),
                        ),
                    )
                }
            assertEquals(HttpStatusCode.InternalServerError, mediaEncryptFail.status)
            assertTrue(mediaEncryptFail.bodyAsText().contains("MEDIA_ENCRYPT_FAILED"))

            val backupEncryptFail =
                client.post("/api/v1/backups") {
                    header(HttpHeaders.Authorization, auth)
                    setBody(
                        backupMultipartWithFields(
                            includeDeviceId = true,
                            includeManifest = true,
                            includeData = true,
                            payload = byteArrayOf(1, 2, 3, 4),
                        ),
                    )
                }
            assertEquals(HttpStatusCode.InternalServerError, backupEncryptFail.status)
            assertTrue(backupEncryptFail.bodyAsText().contains("BACKUP_ENCRYPT_FAILED"))

            val createdAt = Clock.System.now().toEpochMilliseconds()
            repository.upsertMedia(
                userId,
                MediaRecord(
                    mediaId = "signed-meta",
                    contentId = "content-1",
                    userId = userId,
                    fileName = "photo.jpg",
                    mimeType = "image/jpeg",
                    sizeBytes = 3,
                    data = byteArrayOf(1, 2, 3),
                    storagePath = "users/$userId/media/signed-meta/photo.jpg",
                    createdAt = createdAt,
                    serverVersion = 1,
                    deviceId = DeviceId("device-1"),
                ),
            )
            repository.upsertMedia(
                userId,
                MediaRecord(
                    mediaId = "invalid-mime",
                    contentId = "content-2",
                    userId = userId,
                    fileName = "blob.bin",
                    mimeType = "bad/content/type",
                    sizeBytes = 3,
                    data = byteArrayOf(9, 8, 7),
                    storagePath = null,
                    createdAt = createdAt + 1,
                    serverVersion = 1,
                    deviceId = DeviceId("device-1"),
                ),
            )

            val metaSigned =
                client.get("/api/v1/media/signed-meta") {
                    header(HttpHeaders.Authorization, auth)
                }
            assertEquals(HttpStatusCode.OK, metaSigned.status)
            assertTrue(metaSigned.bodyAsText().contains("https://signed.example/object"))

            val mediaMissing =
                client.get("/api/v1/media/media-missing") {
                    header(HttpHeaders.Authorization, auth)
                }
            assertEquals(HttpStatusCode.NotFound, mediaMissing.status)

            val mediaBinaryMissing =
                client.get("/api/v1/media/media-missing/binary") {
                    header(HttpHeaders.Authorization, auth)
                }
            assertEquals(HttpStatusCode.NotFound, mediaBinaryMissing.status)

            val invalidMimeBinary =
                client.get("/api/v1/media/invalid-mime/binary") {
                    header(HttpHeaders.Authorization, auth)
                }
            assertEquals(HttpStatusCode.OK, invalidMimeBinary.status)

            val backupMissingId = UUID.randomUUID()
            val backupInfoMissing =
                client.get("/api/v1/backups/$backupMissingId") {
                    header(HttpHeaders.Authorization, auth)
                }
            assertEquals(HttpStatusCode.NotFound, backupInfoMissing.status)

            val backupBinaryMissing =
                client.get("/api/v1/backups/$backupMissingId/binary") {
                    header(HttpHeaders.Authorization, auth)
                }
            assertEquals(HttpStatusCode.NotFound, backupBinaryMissing.status)

            val deleteInvalidBackupId =
                client.delete("/api/v1/backups/not-a-uuid") {
                    header(HttpHeaders.Authorization, auth)
                }
            assertEquals(HttpStatusCode.BadRequest, deleteInvalidBackupId.status)
        }

    @Test
    fun `media upload success writes encrypted payload to external storage`() =
        testApplication {
            val repository = InMemorySyncRepository()
            val tokenService = JwtTokenService("sync-binary-secret")
            val storage = mockk<GcsMediaStorage>()
            val encryptionService = mockk<EncryptionService>()
            val userId = UUID.randomUUID()
            val auth = "Bearer ${tokenService.generateAccessToken(userId.toString())}"
            val payload = byteArrayOf(4, 5, 6, 7)

            every {
                encryptionService.processMediaUpload(any(), any(), any(), any())
            } returns ProcessedPayload(data = payload, encrypted = true)
            every { encryptionService.processMediaDownload(any(), any()) } answers { firstArg() }
            every { encryptionService.processBackupUpload(any(), any(), any()) } returns ProcessedPayload(byteArrayOf(1), true)
            every { encryptionService.processBackupDownload(any(), any()) } returns byteArrayOf(1)
            every { storage.putBlob(match { it.namespace == LogDateBlobNamespace.MEDIA }) } returns "users/u/media/m1/photo.jpg"

            application {
                install(ContentNegotiation) { json() }
                routing {
                    route("/api/v1") {
                        syncRoutes(
                            tokenService = tokenService,
                            mediaStorage = storage,
                            metrics = SyncMetricsRegistry(),
                            encryptionService = encryptionService,
                            collectionsRepository = repository.asLogDateCollectionsRepository(),
                            mediaBlobRepository = repository.asLogDateMediaRepository().asLogDateMediaBlobRepository(),
                            backupRepository = repository.asLogDateBackupRepository(),
                        )
                    }
                }
            }

            val response =
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
                            sizeBytes = payload.size.toLong(),
                            payload = payload,
                        ),
                    )
                }

            assertEquals(HttpStatusCode.Created, response.status)
        }
}
