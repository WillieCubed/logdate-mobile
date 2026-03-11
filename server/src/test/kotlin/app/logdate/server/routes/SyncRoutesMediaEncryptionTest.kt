package app.logdate.server.routes

import app.logdate.server.auth.StubTokenService
import app.logdate.server.crypto.PayloadPrefixes
import app.logdate.server.logdate.asLogDateBackupRepository
import app.logdate.server.logdate.asLogDateCollectionsRepository
import app.logdate.server.logdate.asLogDateMediaBlobRepository
import app.logdate.server.logdate.asLogDateMediaRepository
import app.logdate.server.routes.support.mediaUploadMultipartContent
import app.logdate.server.sync.InMemorySyncRepository
import app.logdate.server.sync.MediaAccessPolicy
import app.logdate.server.sync.SyncMetricsRegistry
import app.logdate.shared.model.sync.MediaUploadResponse
import io.ktor.client.call.body
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
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SyncRoutesMediaEncryptionTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `media upload encrypts at rest and download returns plaintext`() =
        testApplication {
            val repository = InMemorySyncRepository()
            val tokenService = StubTokenService()
            val metrics = SyncMetricsRegistry()

            application {
                install(ContentNegotiation) {
                    json(json)
                }
                routing {
                    route("/api/v1") {
                        syncRoutes(
                            tokenService = tokenService,
                            mediaStorage = null,
                            metrics = metrics,
                            mediaAccessPolicy = MediaAccessPolicy(useSignedUrls = false, signedUrlTtlHours = 1),
                            collectionsRepository = repository.asLogDateCollectionsRepository(),
                            mediaBlobRepository = repository.asLogDateMediaRepository().asLogDateMediaBlobRepository(),
                            backupRepository = repository.asLogDateBackupRepository(),
                        )
                    }
                }
            }

            val userId = UUID.randomUUID()
            val authHeader = "Bearer ${tokenService.generateAccessToken(userId.toString())}"
            val bytes = byteArrayOf(10, 11, 12, 13)
            val upload =
                client.post("/api/v1/media") {
                    header(HttpHeaders.Authorization, authHeader)
                    setBody(
                        mediaUploadMultipartContent(
                            contentId = "note-encrypted",
                            fileName = "photo.jpg",
                            mimeType = "image/jpeg",
                            data = bytes,
                            deviceId = "dev-1",
                        ),
                    )
                }
            assertEquals(HttpStatusCode.Created, upload.status)

            val uploadPayload = json.decodeFromString<MediaUploadResponse>(upload.bodyAsText())
            val stored = repository.getMedia(userId, uploadPayload.mediaId)
            assertNotNull(stored)
            assertFalse(stored.data.contentEquals(bytes))
            assertTrue(stored.data.size > bytes.size)
            val prefix = String(stored.data.copyOfRange(0, 5), Charsets.UTF_8)
            assertEquals("LDSM1", prefix)

            val download =
                client.get("/api/v1/media/${uploadPayload.mediaId}/binary") {
                    header(HttpHeaders.Authorization, authHeader)
                }
            assertEquals(HttpStatusCode.OK, download.status)
            assertTrue(download.body<ByteArray>().contentEquals(bytes))
        }

    @Test
    fun `client encrypted media round trips without server decryption`() =
        testApplication {
            val repository = InMemorySyncRepository()
            val tokenService = StubTokenService()
            val metrics = SyncMetricsRegistry()

            application {
                install(ContentNegotiation) {
                    json(json)
                }
                routing {
                    route("/api/v1") {
                        syncRoutes(
                            tokenService = tokenService,
                            mediaStorage = null,
                            metrics = metrics,
                            mediaAccessPolicy = MediaAccessPolicy(useSignedUrls = false, signedUrlTtlHours = 1),
                            collectionsRepository = repository.asLogDateCollectionsRepository(),
                            mediaBlobRepository = repository.asLogDateMediaRepository().asLogDateMediaBlobRepository(),
                            backupRepository = repository.asLogDateBackupRepository(),
                        )
                    }
                }
            }

            val userId = UUID.randomUUID()
            val authHeader = "Bearer ${tokenService.generateAccessToken(userId.toString())}"
            val clientKey = ByteArray(32) { index -> (index + 42).toByte() }
            val plaintext = "client-encrypted".encodeToByteArray()
            val clientCiphertext = encryptClientMedia(clientKey, plaintext)
            val upload =
                client.post("/api/v1/media") {
                    header(HttpHeaders.Authorization, authHeader)
                    setBody(
                        mediaUploadMultipartContent(
                            contentId = "note-client-encrypted",
                            fileName = "secret.bin",
                            mimeType = "application/octet-stream",
                            data = clientCiphertext,
                            deviceId = "dev-1",
                        ),
                    )
                }
            assertEquals(HttpStatusCode.Created, upload.status)

            val uploadPayload = json.decodeFromString<MediaUploadResponse>(upload.bodyAsText())
            val stored = repository.getMedia(userId, uploadPayload.mediaId)
            assertNotNull(stored)
            assertTrue(stored.data.contentEquals(clientCiphertext))
            val prefix = String(stored.data.copyOfRange(0, 5), Charsets.UTF_8)
            assertEquals("LDCE1", prefix)

            val download =
                client.get("/api/v1/media/${uploadPayload.mediaId}/binary") {
                    header(HttpHeaders.Authorization, authHeader)
                }
            assertEquals(HttpStatusCode.OK, download.status)
            val payload = download.body<ByteArray>()
            assertTrue(payload.contentEquals(clientCiphertext))
            val decrypted = decryptClientMedia(clientKey, payload)
            assertTrue(decrypted.contentEquals(plaintext))
        }

    private fun encryptClientMedia(
        key: ByteArray,
        plaintext: ByteArray,
    ): ByteArray {
        // Fixed IV for deterministic test vectors.
        val iv = ByteArray(12) { index -> (index + 7).toByte() }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val keySpec = SecretKeySpec(key, "AES")
        val gcmSpec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec)
        val cipherText = cipher.doFinal(plaintext)
        val prefix = PayloadPrefixes.CLIENT_MEDIA
        val output = ByteArray(prefix.size + iv.size + cipherText.size)
        System.arraycopy(prefix, 0, output, 0, prefix.size)
        System.arraycopy(iv, 0, output, prefix.size, iv.size)
        System.arraycopy(cipherText, 0, output, prefix.size + iv.size, cipherText.size)
        return output
    }

    private fun decryptClientMedia(
        key: ByteArray,
        payload: ByteArray,
    ): ByteArray {
        val prefix = PayloadPrefixes.CLIENT_MEDIA
        val ivStart = prefix.size
        val ivEnd = ivStart + 12
        val iv = payload.copyOfRange(ivStart, ivEnd)
        val cipherText = payload.copyOfRange(ivEnd, payload.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val keySpec = SecretKeySpec(key, "AES")
        val gcmSpec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec)
        return cipher.doFinal(cipherText)
    }
}
