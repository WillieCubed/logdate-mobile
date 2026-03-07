package app.logdate.server.e2e.sync

import app.logdate.server.configureSyncTestApp
import app.logdate.server.routes.support.mediaUploadMultipartContent
import app.logdate.shared.model.sync.ContentChangesResponse
import app.logdate.shared.model.sync.MediaUploadResponse
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * End-to-end sync tests that cover multi-device behavior, conflict handling, and media flow.
 */
class SyncE2ETest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `multi-device sync flow detects conflicts and downloads media`() =
        testApplication {
            val env = configureSyncTestApp()
            val accountId = UUID.randomUUID().toString()
            val authHeader = "Bearer ${env.tokenService.generateAccessToken(accountId)}"

            client.put("/api/v1/journals/journal-e2e") {
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
                    """.trimIndent(),
                )
            }

            client.put("/api/v1/contents/note-e2e") {
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
                    """.trimIndent(),
                )
            }

            val mediaBytes = byteArrayOf(9, 8, 7, 6)
            val mediaUpload =
                client.post("/api/v1/media") {
                    header(HttpHeaders.Authorization, authHeader)
                    setBody(
                        mediaUploadMultipartContent(
                            contentId = "note-e2e",
                            fileName = "media.png",
                            mimeType = "image/png",
                            data = mediaBytes,
                            deviceId = "device-a",
                        ),
                    )
                }
            assertEquals(HttpStatusCode.Created, mediaUpload.status)
            val mediaUploadPayload = json.decodeFromString<MediaUploadResponse>(mediaUpload.bodyAsText())

            val changes =
                client.get("/api/v1/contents?since=0") {
                    header(HttpHeaders.Authorization, authHeader)
                }
            assertEquals(HttpStatusCode.OK, changes.status)
            val changesPayload = json.decodeFromString<ContentChangesResponse>(changes.bodyAsText())
            assertTrue(changesPayload.changes.any { it.id == "note-e2e" })

            val conflict =
                client.patch("/api/v1/contents/note-e2e") {
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
                        """.trimIndent(),
                    )
                }
            assertEquals(HttpStatusCode.Conflict, conflict.status)

            val download =
                client.get("/api/v1/media/${mediaUploadPayload.mediaId}/binary") {
                    header(HttpHeaders.Authorization, authHeader)
                }
            assertEquals(HttpStatusCode.OK, download.status)
            assertTrue(download.body<ByteArray>().contentEquals(mediaBytes))
        }
}
