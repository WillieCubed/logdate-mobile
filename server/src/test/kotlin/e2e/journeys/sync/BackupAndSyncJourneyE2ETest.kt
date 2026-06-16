package app.logdate.server.e2e.journeys.sync

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
 * End-to-end sync journey tests for multi-device conflict handling and media flow.
 */
class BackupAndSyncJourneyE2ETest {
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

    @Test
    fun `sync flow round trips text image video and audio entries`() =
        testApplication {
            val env = configureSyncTestApp()
            val accountId = UUID.randomUUID().toString()
            val authHeader = "Bearer ${env.tokenService.generateAccessToken(accountId)}"

            val entries =
                listOf(
                    SyncEntryFixture(
                        id = "entry-text",
                        type = "TEXT",
                        content = "plain text entry",
                    ),
                    SyncEntryFixture(
                        id = "entry-image",
                        type = "IMAGE",
                        fileName = "photo.jpg",
                        mimeType = "image/jpeg",
                        bytes = byteArrayOf(1, 2, 3),
                    ),
                    SyncEntryFixture(
                        id = "entry-video",
                        type = "VIDEO",
                        fileName = "clip.mp4",
                        mimeType = "video/mp4",
                        bytes = byteArrayOf(4, 5, 6, 7),
                    ),
                    SyncEntryFixture(
                        id = "entry-audio",
                        type = "AUDIO",
                        fileName = "voice.m4a",
                        mimeType = "audio/mp4",
                        bytes = byteArrayOf(8, 9, 10),
                    ),
                )

            val uploadedMedia =
                entries
                    .filter { it.bytes != null }
                    .associate { entry ->
                        val upload =
                            client.post("/api/v1/media") {
                                header(HttpHeaders.Authorization, authHeader)
                                setBody(
                                    mediaUploadMultipartContent(
                                        contentId = entry.id,
                                        fileName = entry.fileName!!,
                                        mimeType = entry.mimeType!!,
                                        data = entry.bytes!!,
                                        deviceId = "device-a",
                                    ),
                                )
                            }
                        assertEquals(HttpStatusCode.Created, upload.status, upload.bodyAsText())
                        val uploadPayload = json.decodeFromString<MediaUploadResponse>(upload.bodyAsText())
                        entry.id to uploadPayload
                    }

            entries.forEachIndexed { index, entry ->
                val upload =
                    client.put("/api/v1/contents/${entry.id}") {
                        header(HttpHeaders.Authorization, authHeader)
                        contentType(ContentType.Application.Json)
                        setBody(entry.contentUploadJson(index + 1, uploadedMedia[entry.id]?.mediaId))
                    }
                assertEquals(HttpStatusCode.Created, upload.status, upload.bodyAsText())
            }

            val changes =
                client.get("/api/v1/contents?since=0") {
                    header(HttpHeaders.Authorization, authHeader)
                }
            assertEquals(HttpStatusCode.OK, changes.status)
            val changesPayload = json.decodeFromString<ContentChangesResponse>(changes.bodyAsText())
            entries.forEach { entry ->
                val change = changesPayload.changes.first { it.id == entry.id }
                assertEquals(entry.type, change.type)
                assertEquals(uploadedMedia[entry.id]?.mediaId, change.mediaUri)
            }

            entries.filter { it.bytes != null }.forEach { entry ->
                val mediaId = uploadedMedia.getValue(entry.id).mediaId
                val download =
                    client.get("/api/v1/media/$mediaId/binary") {
                        header(HttpHeaders.Authorization, authHeader)
                    }
                assertEquals(HttpStatusCode.OK, download.status)
                assertTrue(download.body<ByteArray>().contentEquals(entry.bytes))
            }
        }

    private data class SyncEntryFixture(
        val id: String,
        val type: String,
        val content: String? = null,
        val fileName: String? = null,
        val mimeType: String? = null,
        val bytes: ByteArray? = null,
    ) {
        fun contentUploadJson(
            index: Int,
            mediaId: String?,
        ): String =
            """
            {
              "id": "$id",
              "type": "$type",
              "content": ${content?.let { "\"$it\"" } ?: "null"},
              "mediaUri": ${mediaId?.let { "\"$it\"" } ?: "null"},
              "durationMs": ${if (type == "AUDIO" || type == "VIDEO") 1000L * index else 0L},
              "createdAt": ${1000L * index},
              "lastUpdated": ${1000L * index},
              "deviceId": "device-a"
            }
            """.trimIndent()
    }
}
