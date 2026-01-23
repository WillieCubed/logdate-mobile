package app.logdate.server.e2e.sync

import app.logdate.server.auth.JwtTokenService
import app.logdate.server.module
import app.logdate.shared.model.sync.ContentChangesResponse
import app.logdate.shared.model.sync.MediaDownloadResponse
import app.logdate.shared.model.sync.MediaUploadResponse
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import org.koin.ktor.ext.getKoin
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * End-to-end sync tests that cover multi-device behavior, conflict handling, and media flow.
 */
class SyncE2ETest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `multi-device sync flow detects conflicts and downloads media`() = testApplication {
        lateinit var tokenService: JwtTokenService
        application {
            module()
            tokenService = getKoin().get()
        }
        startApplication()

        val accountId = UUID.randomUUID().toString()
        val authHeader = "Bearer ${tokenService.generateAccessToken(accountId)}"

        client.post("/api/v1/sync/journals") {
            header(HttpHeaders.Authorization, authHeader)
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "id": "journal-e2e",
                  "title": "E2E Journal",
                  "description": "Sync test",
                  "createdAt": 1000,
                  "lastUpdated": 1000,
                  "deviceId": "device-a"
                }
                """.trimIndent()
            )
        }

        client.post("/api/v1/sync/content") {
            header(HttpHeaders.Authorization, authHeader)
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "id": "note-e2e",
                  "type": "TEXT",
                  "content": "hello from device A",
                  "mediaUri": null,
                  "createdAt": 1000,
                  "lastUpdated": 1000,
                  "deviceId": "device-a"
                }
                """.trimIndent()
            )
        }

        val mediaBytes = byteArrayOf(9, 8, 7, 6)
        val encoded = mediaBytes.joinToString(prefix = "[", postfix = "]")
        val mediaUpload = client.post("/api/v1/sync/media") {
            header(HttpHeaders.Authorization, authHeader)
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "contentId": "note-e2e",
                  "fileName": "media.png",
                  "mimeType": "image/png",
                  "sizeBytes": ${mediaBytes.size},
                  "data": $encoded,
                  "deviceId": "device-a"
                }
                """.trimIndent()
            )
        }
        assertEquals(HttpStatusCode.OK, mediaUpload.status)
        val mediaUploadPayload = json.decodeFromString<MediaUploadResponse>(mediaUpload.bodyAsText())

        val changes = client.get("/api/v1/sync/content/changes?since=0") {
            header(HttpHeaders.Authorization, authHeader)
        }
        assertEquals(HttpStatusCode.OK, changes.status)
        val changesPayload = json.decodeFromString<ContentChangesResponse>(changes.bodyAsText())
        assertTrue(changesPayload.changes.any { it.id == "note-e2e" })

        val conflict = client.post("/api/v1/sync/content/note-e2e") {
            header(HttpHeaders.Authorization, authHeader)
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "content": "device b update",
                  "mediaUri": null,
                  "lastUpdated": 2000,
                  "deviceId": "device-b",
                  "versionConstraint": {
                    "type": "known",
                    "serverVersion": 0
                  }
                }
                """.trimIndent()
            )
        }
        assertEquals(HttpStatusCode.Conflict, conflict.status)

        val download = client.get("/api/v1/sync/media/${mediaUploadPayload.mediaId}") {
            header(HttpHeaders.Authorization, authHeader)
        }
        assertEquals(HttpStatusCode.OK, download.status)
        val downloadPayload = json.decodeFromString<MediaDownloadResponse>(download.bodyAsText())
        assertTrue(downloadPayload.data.contentEquals(mediaBytes))
    }
}
