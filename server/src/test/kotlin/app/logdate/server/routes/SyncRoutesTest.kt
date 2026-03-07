package app.logdate.server.routes

import app.logdate.server.auth.JwtTokenService
import app.logdate.server.configureSyncTestApp
import app.logdate.server.routes.support.mediaUploadMultipartContent
import app.logdate.shared.model.sync.ContentChangesResponse
import app.logdate.shared.model.sync.MediaMetadataResponse
import app.logdate.shared.model.sync.MediaUploadResponse
import io.ktor.client.call.body
import io.ktor.client.request.delete
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
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SyncRoutesTest {
    private val json = Json { ignoreUnknownKeys = true }

    private fun authHeader(
        tokenService: JwtTokenService,
        accountId: String = UUID.randomUUID().toString(),
    ): String = "Bearer ${tokenService.generateAccessToken(accountId)}"

    @Serializable
    private data class SyncStatusPayload(
        val data: StatusData,
    ) {
        @Serializable
        data class StatusData(
            val contentCount: Int,
            val journalCount: Int,
            val associationCount: Int,
            val lastTimestamp: Long,
        )
    }

    @Test
    fun `sync status returns ok and counts`() =
        testApplication {
            val env = configureSyncTestApp()
            val authHeader = authHeader(env.tokenService)
            val response =
                client.get("/api/v1/ops/sync/status") {
                    header(HttpHeaders.Authorization, authHeader)
                }
            assertEquals(HttpStatusCode.OK, response.status)

            val payload = json.decodeFromString<SyncStatusPayload>(response.bodyAsText())
            assertTrue(payload.data.contentCount >= 0)
            assertTrue(payload.data.journalCount >= 0)
            assertTrue(payload.data.associationCount >= 0)
        }

    @Test
    fun `content upload appears in change feed`() =
        testApplication {
            val env = configureSyncTestApp()
            val authHeader = authHeader(env.tokenService)

            val upload =
                client.put("/api/v1/contents/note-1") {
                    header(HttpHeaders.Authorization, authHeader)
                    contentType(ContentType.Application.Json)
                    setBody(
                        """
                        {
                          "id": "note-1",
                          "type": "TEXT",
                          "content": "hello",
                          "mediaUri": null,
                          "createdAt": 1,
                          "lastUpdated": 1,
                          "deviceId": "dev-1"
                        }
                        """.trimIndent(),
                    )
                }
            assertEquals(HttpStatusCode.Created, upload.status)

            val changes =
                client.get("/api/v1/contents?since=0") {
                    header(HttpHeaders.Authorization, authHeader)
                }
            assertEquals(HttpStatusCode.OK, changes.status)

            val payload = json.decodeFromString<ContentChangesResponse>(changes.bodyAsText())
            assertEquals(1, payload.changes.size)
            assertEquals("note-1", payload.changes.first().id)
        }

    @Test
    fun `content update detects conflict when version is stale`() =
        testApplication {
            val env = configureSyncTestApp()
            val authHeader = authHeader(env.tokenService, UUID.randomUUID().toString())

            // seed initial content
            client.put("/api/v1/contents/note-2") {
                header(HttpHeaders.Authorization, authHeader)
                contentType(ContentType.Application.Json)
                setBody(
                    """
                    {
                      "id": "note-2",
                      "type": "TEXT",
                      "content": "initial",
                      "mediaUri": null,
                      "createdAt": 1,
                      "lastUpdated": 1,
                      "deviceId": "device-a"
                    }
                    """.trimIndent(),
                )
            }

            // update with stale version constraint
            val conflict =
                client.patch("/api/v1/contents/note-2") {
                    header(HttpHeaders.Authorization, authHeader)
                    contentType(ContentType.Application.Json)
                    setBody(
                        """
                        {
                          "content": "second",
                          "mediaUri": null,
                          "lastUpdated": 2,
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
        }

    @Test
    fun `content delete surfaces as tombstone`() =
        testApplication {
            val env = configureSyncTestApp()
            val authHeader = authHeader(env.tokenService)

            client.put("/api/v1/contents/note-3") {
                header(HttpHeaders.Authorization, authHeader)
                contentType(ContentType.Application.Json)
                setBody(
                    """
                    {
                      "id": "note-3",
                      "type": "TEXT",
                      "content": "to-delete",
                      "mediaUri": null,
                      "createdAt": 1,
                      "lastUpdated": 1,
                      "deviceId": "dev-1"
                    }
                    """.trimIndent(),
                )
            }

            val delete =
                client.delete("/api/v1/contents/note-3") {
                    header(HttpHeaders.Authorization, authHeader)
                }
            assertEquals(HttpStatusCode.NoContent, delete.status)

            val changes =
                client.get("/api/v1/contents?since=0") {
                    header(HttpHeaders.Authorization, authHeader)
                }
            val payload = json.decodeFromString<ContentChangesResponse>(changes.bodyAsText())
            assertTrue(payload.deletions.any { it.id == "note-3" })
        }

    @Test
    fun `content changes paginates and reports hasMore`() =
        testApplication {
            val env = configureSyncTestApp()
            val authHeader = authHeader(env.tokenService)

            val payloads =
                listOf(
                    "note-p1" to
                        """{ "id": "note-p1", "type": "TEXT", "content": "one", "mediaUri": null, "createdAt": 1, "lastUpdated": 1, "deviceId": "dev-1" }""",
                    "note-p2" to
                        """{ "id": "note-p2", "type": "TEXT", "content": "two", "mediaUri": null, "createdAt": 2, "lastUpdated": 2, "deviceId": "dev-1" }""",
                    "note-p3" to
                        """{ "id": "note-p3", "type": "TEXT", "content": "three", "mediaUri": null, "createdAt": 3, "lastUpdated": 3, "deviceId": "dev-1" }""",
                )

            payloads.forEach { (id, payload) ->
                val upload =
                    client.put("/api/v1/contents/$id") {
                        header(HttpHeaders.Authorization, authHeader)
                        contentType(ContentType.Application.Json)
                        setBody(payload)
                    }
                assertTrue(upload.status == HttpStatusCode.Created || upload.status == HttpStatusCode.OK)
            }

            val changes =
                client.get("/api/v1/contents?since=0&limit=2") {
                    header(HttpHeaders.Authorization, authHeader)
                }
            assertEquals(HttpStatusCode.OK, changes.status)

            val payload = json.decodeFromString<ContentChangesResponse>(changes.bodyAsText())
            assertEquals(2, payload.changes.size)
            assertTrue(payload.hasMore)
        }

    @Serializable
    private data class JournalUploadResponse(
        val id: String,
        val serverVersion: Long,
        val uploadedAt: Long,
    )

    @Serializable
    private data class JournalChangesPayload(
        val changes: List<JournalChange>,
        val deletions: List<JournalDeletion>,
        val lastTimestamp: Long,
    ) {
        @Serializable
        data class JournalChange(
            val id: String,
            val title: String,
            val description: String?,
            val serverVersion: Long,
        )

        @Serializable
        data class JournalDeletion(
            val id: String,
            val deletedAt: Long,
        )
    }

    @Test
    fun `journal upload and changes work end-to-end`() =
        testApplication {
            val env = configureSyncTestApp()
            val authHeader = authHeader(env.tokenService)

            // Upload journal
            val upload =
                client.put("/api/v1/journals/journal-1") {
                    header(HttpHeaders.Authorization, authHeader)
                    contentType(ContentType.Application.Json)
                    setBody(
                        """
                        {
                          "id": "journal-1",
                          "title": "My Journal",
                          "description": "Test description",
                          "createdAt": 1000,
                          "lastUpdated": 1000,
                          "deviceId": "dev-1"
                        }
                        """.trimIndent(),
                    )
                }
            assertEquals(HttpStatusCode.Created, upload.status)

            // Check changes
            val changes =
                client.get("/api/v1/journals?since=0") {
                    header(HttpHeaders.Authorization, authHeader)
                }
            assertEquals(HttpStatusCode.OK, changes.status)

            val payload = json.decodeFromString<JournalChangesPayload>(changes.bodyAsText())
            assertEquals(1, payload.changes.size)
            assertEquals("journal-1", payload.changes.first().id)
            assertEquals("My Journal", payload.changes.first().title)
        }

    @Test
    fun `journal update works with version constraint`() =
        testApplication {
            val env = configureSyncTestApp()
            val authHeader = authHeader(env.tokenService)

            // Create journal
            client.put("/api/v1/journals/journal-2") {
                header(HttpHeaders.Authorization, authHeader)
                contentType(ContentType.Application.Json)
                setBody(
                    """
                    {
                      "id": "journal-2",
                      "title": "Original Title",
                      "description": "",
                      "createdAt": 1000,
                      "lastUpdated": 1000,
                      "deviceId": "dev-1"
                    }
                    """.trimIndent(),
                )
            }

            // Update with no version constraint (should succeed)
            val update =
                client.patch("/api/v1/journals/journal-2") {
                    header(HttpHeaders.Authorization, authHeader)
                    contentType(ContentType.Application.Json)
                    setBody(
                        """
                        {
                          "title": "Updated Title",
                          "description": "Added description",
                          "lastUpdated": 2000,
                          "deviceId": "dev-1",
                          "versionConstraint": { "type": "none" }
                        }
                        """.trimIndent(),
                    )
                }
            assertEquals(HttpStatusCode.OK, update.status)
        }

    @Test
    fun `journal delete creates tombstone`() =
        testApplication {
            val env = configureSyncTestApp()
            val authHeader = authHeader(env.tokenService)

            // Create journal
            client.put("/api/v1/journals/journal-3") {
                header(HttpHeaders.Authorization, authHeader)
                contentType(ContentType.Application.Json)
                setBody(
                    """
                    {
                      "id": "journal-3",
                      "title": "To Delete",
                      "description": "",
                      "createdAt": 1000,
                      "lastUpdated": 1000,
                      "deviceId": "dev-1"
                    }
                    """.trimIndent(),
                )
            }

            // Delete
            val delete =
                client.delete("/api/v1/journals/journal-3") {
                    header(HttpHeaders.Authorization, authHeader)
                }
            assertEquals(HttpStatusCode.NoContent, delete.status)

            // Check deletions
            val changes =
                client.get("/api/v1/journals?since=0") {
                    header(HttpHeaders.Authorization, authHeader)
                }
            val payload = json.decodeFromString<JournalChangesPayload>(changes.bodyAsText())
            assertTrue(payload.deletions.any { it.id == "journal-3" })
        }

    @Serializable
    private data class AssociationUploadResponse(
        val count: Int,
        val uploadedAt: Long,
    )

    @Serializable
    private data class PurgeResponse(
        val contentPurged: Int,
        val journalPurged: Int,
        val associationPurged: Int,
        val mediaPurged: Int,
        val cutoff: Long,
        val retentionDaysApplied: Long,
    )

    @Test
    fun `association upload and changes work`() =
        testApplication {
            val env = configureSyncTestApp()
            val authHeader = authHeader(env.tokenService)

            // Upload associations
            val upload =
                client.put("/api/v1/associations") {
                    header(HttpHeaders.Authorization, authHeader)
                    contentType(ContentType.Application.Json)
                    setBody(
                        """
                        {
                          "associations": [
                            {
                              "journalId": "journal-1",
                              "contentId": "note-1",
                              "createdAt": 1000,
                              "deviceId": "dev-1"
                            }
                          ]
                        }
                        """.trimIndent(),
                    )
                }
            assertEquals(HttpStatusCode.OK, upload.status)

            // Check changes
            val changes =
                client.get("/api/v1/associations?since=0") {
                    header(HttpHeaders.Authorization, authHeader)
                }
            assertEquals(HttpStatusCode.OK, changes.status)
        }

    @Test
    fun `media upload and download returns bytes`() =
        testApplication {
            val env = configureSyncTestApp()
            val authHeader = authHeader(env.tokenService)
            val bytes = byteArrayOf(1, 2, 3, 4)
            val upload =
                client.post("/api/v1/media") {
                    header(HttpHeaders.Authorization, authHeader)
                    setBody(
                        mediaUploadMultipartContent(
                            contentId = "note-media",
                            fileName = "photo.jpg",
                            mimeType = "image/jpeg",
                            data = bytes,
                            deviceId = "dev-1",
                        ),
                    )
                }
            assertEquals(HttpStatusCode.Created, upload.status)

            val uploadPayload = json.decodeFromString<MediaUploadResponse>(upload.bodyAsText())
            val metadata =
                client.get("/api/v1/media/${uploadPayload.mediaId}") {
                    header(HttpHeaders.Authorization, authHeader)
                }
            assertEquals(HttpStatusCode.OK, metadata.status)
            val metadataPayload = json.decodeFromString<MediaMetadataResponse>(metadata.bodyAsText())
            assertEquals(bytes.size.toLong(), metadataPayload.sizeBytes)

            val downloadBinary =
                client.get("/api/v1/media/${uploadPayload.mediaId}/binary") {
                    header(HttpHeaders.Authorization, authHeader)
                }
            assertEquals(HttpStatusCode.OK, downloadBinary.status)
            assertTrue(downloadBinary.body<ByteArray>().contentEquals(bytes))
        }

    @Test
    fun `media upload validates declared size`() =
        testApplication {
            val env = configureSyncTestApp()
            val authHeader = authHeader(env.tokenService)
            val bytes = byteArrayOf(1, 2, 3, 4)

            val upload =
                client.post("/api/v1/media") {
                    header(HttpHeaders.Authorization, authHeader)
                    setBody(
                        mediaUploadMultipartContent(
                            contentId = "note-size-mismatch",
                            fileName = "photo.jpg",
                            mimeType = "image/jpeg",
                            data = bytes,
                            deviceId = "dev-1",
                            declaredSizeBytes = 99,
                        ),
                    )
                }

            assertEquals(HttpStatusCode.BadRequest, upload.status)
            assertTrue(upload.bodyAsText().contains("sizeBytes does not match"))
        }

    @Test
    fun `maintenance purge returns concrete payload`() =
        testApplication {
            val env = configureSyncTestApp()
            val authHeader = authHeader(env.tokenService)

            val response =
                client.post("/api/v1/ops/sync/tombstones:purge?retentionDays=30") {
                    header(HttpHeaders.Authorization, authHeader)
                }

            assertEquals(HttpStatusCode.OK, response.status)
            val payload = json.decodeFromString<PurgeResponse>(response.bodyAsText())
            assertEquals(30L, payload.retentionDaysApplied)
            assertTrue(payload.cutoff > 0L)
        }

    @Test
    fun `maintenance purge validates retention days`() =
        testApplication {
            val env = configureSyncTestApp()
            val authHeader = authHeader(env.tokenService)

            val response =
                client.post("/api/v1/ops/sync/tombstones:purge?retentionDays=0") {
                    header(HttpHeaders.Authorization, authHeader)
                }

            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertTrue(response.bodyAsText().contains("retentionDays must be > 0"))
        }

    @Test
    fun `missing since parameter defaults to full feed`() =
        testApplication {
            val env = configureSyncTestApp()
            val authHeader = authHeader(env.tokenService)
            val response =
                client.get("/api/v1/contents") {
                    header(HttpHeaders.Authorization, authHeader)
                }
            assertEquals(HttpStatusCode.OK, response.status)
        }

    @Test
    fun `sync trigger endpoint is not available`() =
        testApplication {
            val env = configureSyncTestApp()
            val authHeader = authHeader(env.tokenService)
            val response =
                client.post("/api/v1/sync/") {
                    header(HttpHeaders.Authorization, authHeader)
                }
            assertEquals(HttpStatusCode.NotFound, response.status)
        }
}
