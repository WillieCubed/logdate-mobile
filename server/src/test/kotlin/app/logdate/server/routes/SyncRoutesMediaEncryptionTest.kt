package app.logdate.server.routes

import app.logdate.server.auth.StubTokenService
import app.logdate.server.sync.InMemorySyncRepository
import app.logdate.server.sync.MediaAccessPolicy
import app.logdate.server.sync.MediaEncryptionService
import app.logdate.server.sync.SyncMetricsRegistry
import app.logdate.shared.model.sync.DeviceId
import app.logdate.shared.model.sync.MediaDownloadResponse
import app.logdate.shared.model.sync.MediaUploadRequest
import app.logdate.shared.model.sync.MediaUploadResponse
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
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
    fun `media upload encrypts at rest and download returns plaintext`() = testApplication {
        val repository = InMemorySyncRepository()
        val tokenService = StubTokenService()
        val metrics = SyncMetricsRegistry()
        val encryptionKey = ByteArray(32) { index -> (index + 1).toByte() }
        val mediaEncryption = MediaEncryptionService.fromKeyBytes(encryptionKey)

        application {
            install(ContentNegotiation) {
                json(json)
            }
            routing {
                route("/api/v1") {
                    syncRoutes(
                        repository = repository,
                        tokenService = tokenService,
                        mediaStorage = null,
                        metrics = metrics,
                        mediaAccessPolicy = MediaAccessPolicy(useSignedUrls = false, signedUrlTtlHours = 1),
                        mediaEncryption = mediaEncryption
                    )
                }
            }
        }

        val userId = UUID.randomUUID()
        val authHeader = "Bearer ${tokenService.generateAccessToken(userId.toString())}"
        val bytes = byteArrayOf(10, 11, 12, 13)
        val uploadRequest = MediaUploadRequest(
            contentId = "note-encrypted",
            fileName = "photo.jpg",
            mimeType = "image/jpeg",
            sizeBytes = bytes.size.toLong(),
            data = bytes,
            deviceId = DeviceId("dev-1")
        )
        val upload = client.post("/api/v1/sync/media") {
            header(HttpHeaders.Authorization, authHeader)
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(uploadRequest))
        }
        assertEquals(HttpStatusCode.OK, upload.status)

        val uploadPayload = json.decodeFromString<MediaUploadResponse>(upload.bodyAsText())
        val stored = repository.getMedia(userId, uploadPayload.mediaId)
        assertNotNull(stored)
        assertFalse(stored.data.contentEquals(bytes))
        assertTrue(stored.data.size > bytes.size)
        val prefix = String(stored.data.copyOfRange(0, 5), Charsets.UTF_8)
        assertEquals("LDSM1", prefix)

        val download = client.get("/api/v1/sync/media/${uploadPayload.mediaId}") {
            header(HttpHeaders.Authorization, authHeader)
        }
        assertEquals(HttpStatusCode.OK, download.status)

        val downloadPayload = json.decodeFromString<MediaDownloadResponse>(download.bodyAsText())
        assertTrue(downloadPayload.data.contentEquals(bytes))
    }

    @Test
    fun `client encrypted media round trips without server decryption`() = testApplication {
        val repository = InMemorySyncRepository()
        val tokenService = StubTokenService()
        val metrics = SyncMetricsRegistry()
        val serverKey = ByteArray(32) { index -> (index + 10).toByte() }
        val mediaEncryption = MediaEncryptionService.fromKeyBytes(serverKey)

        application {
            install(ContentNegotiation) {
                json(json)
            }
            routing {
                route("/api/v1") {
                    syncRoutes(
                        repository = repository,
                        tokenService = tokenService,
                        mediaStorage = null,
                        metrics = metrics,
                        mediaAccessPolicy = MediaAccessPolicy(useSignedUrls = false, signedUrlTtlHours = 1),
                        mediaEncryption = mediaEncryption
                    )
                }
            }
        }

        val userId = UUID.randomUUID()
        val authHeader = "Bearer ${tokenService.generateAccessToken(userId.toString())}"
        val clientKey = ByteArray(32) { index -> (index + 42).toByte() }
        val plaintext = "client-encrypted".encodeToByteArray()
        val clientCiphertext = encryptClientMedia(clientKey, plaintext)
        val uploadRequest = MediaUploadRequest(
            contentId = "note-client-encrypted",
            fileName = "secret.bin",
            mimeType = "application/octet-stream",
            sizeBytes = clientCiphertext.size.toLong(),
            data = clientCiphertext,
            deviceId = DeviceId("dev-1")
        )
        val upload = client.post("/api/v1/sync/media") {
            header(HttpHeaders.Authorization, authHeader)
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(uploadRequest))
        }
        assertEquals(HttpStatusCode.OK, upload.status)

        val uploadPayload = json.decodeFromString<MediaUploadResponse>(upload.bodyAsText())
        val stored = repository.getMedia(userId, uploadPayload.mediaId)
        assertNotNull(stored)
        assertTrue(stored.data.contentEquals(clientCiphertext))
        val prefix = String(stored.data.copyOfRange(0, 5), Charsets.UTF_8)
        assertEquals("LDCE1", prefix)

        val download = client.get("/api/v1/sync/media/${uploadPayload.mediaId}") {
            header(HttpHeaders.Authorization, authHeader)
        }
        assertEquals(HttpStatusCode.OK, download.status)

        val downloadPayload = json.decodeFromString<MediaDownloadResponse>(download.bodyAsText())
        assertTrue(downloadPayload.data.contentEquals(clientCiphertext))
        val decrypted = decryptClientMedia(clientKey, downloadPayload.data)
        assertTrue(decrypted.contentEquals(plaintext))
    }

    private fun encryptClientMedia(key: ByteArray, plaintext: ByteArray): ByteArray {
        // Fixed IV for deterministic test vectors.
        val iv = ByteArray(12) { index -> (index + 7).toByte() }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val keySpec = SecretKeySpec(key, "AES")
        val gcmSpec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec)
        val cipherText = cipher.doFinal(plaintext)
        val prefix = MediaEncryptionService.clientPrefixBytes()
        val output = ByteArray(prefix.size + iv.size + cipherText.size)
        System.arraycopy(prefix, 0, output, 0, prefix.size)
        System.arraycopy(iv, 0, output, prefix.size, iv.size)
        System.arraycopy(cipherText, 0, output, prefix.size + iv.size, cipherText.size)
        return output
    }

    private fun decryptClientMedia(key: ByteArray, payload: ByteArray): ByteArray {
        val prefix = MediaEncryptionService.clientPrefixBytes()
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
