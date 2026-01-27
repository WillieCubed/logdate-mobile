package app.logdate.server.routes

import app.logdate.server.auth.JwtTokenService
import app.logdate.server.configureSyncTestApp
import app.logdate.shared.model.sync.ContentChangesResponse
import app.logdate.shared.model.sync.DeviceId
import app.logdate.shared.model.sync.MediaDownloadResponse
import app.logdate.shared.model.sync.MediaUploadRequest
import app.logdate.shared.model.sync.MediaUploadResponse
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SyncRoutesTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun authHeader(tokenService: JwtTokenService, accountId: String = UUID.randomUUID().toString()): String {
        return "Bearer ${tokenService.generateAccessToken(accountId)}"
    }

    @Serializable
    private data class SyncStatusPayload(
        val data: StatusData
    ) {
        @Serializable
        data class StatusData(
            val contentCount: Int,
            val journalCount: Int,
            val associationCount: Int,
            val lastTimestamp: Long
        )
    }

    @Test
    fun `sync status returns ok and counts`() = testApplication {
        val env = configureSyncTestApp()
        val authHeader = authHeader(env.tokenService)
        val response = client.get("/api/v1/sync/status") {
            header(HttpHeaders.Authorization, authHeader)
        }
        assertEquals(HttpStatusCode.OK, response.status)

        val payload = json.decodeFromString<SyncStatusPayload>(response.bodyAsText())
        assertTrue(payload.data.contentCount >= 0)
        assertTrue(payload.data.journalCount >= 0)
        assertTrue(payload.data.associationCount >= 0)
    }

    @Test
    fun `content upload appears in change feed`() = testApplication {
        val env = configureSyncTestApp()
        val authHeader = authHeader(env.tokenService)

        val upload = client.post("/api/v1/sync/content") {
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
                """.trimIndent()
            )
        }
        assertEquals(HttpStatusCode.OK, upload.status)

        val changes = client.get("/api/v1/sync/content/changes?since=0") {
            header(HttpHeaders.Authorization, authHeader)
        }
        assertEquals(HttpStatusCode.OK, changes.status)

        val payload = json.decodeFromString<ContentChangesResponse>(changes.bodyAsText())
        assertEquals(1, payload.changes.size)
        assertEquals("note-1", payload.changes.first().id)
    }

    @Test
    fun `content update detects conflict when version is stale`() = testApplication {
        val env = configureSyncTestApp()
        val authHeader = authHeader(env.tokenService, UUID.randomUUID().toString())

        // seed initial content
        client.post("/api/v1/sync/content") {
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
                """.trimIndent()
            )
        }

        // update with stale version constraint
        val conflict = client.post("/api/v1/sync/content/note-2") {
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
                """.trimIndent()
            )
        }
        assertEquals(HttpStatusCode.Conflict, conflict.status)
    }

    @Test
    fun `content delete surfaces as tombstone`() = testApplication {
        val env = configureSyncTestApp()
        val authHeader = authHeader(env.tokenService)

        client.post("/api/v1/sync/content") {
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
                """.trimIndent()
            )
        }

        val delete = client.post("/api/v1/sync/content/note-3/delete") {
            header(HttpHeaders.Authorization, authHeader)
        }
        assertEquals(HttpStatusCode.OK, delete.status)

        val changes = client.get("/api/v1/sync/content/changes?since=0") {
            header(HttpHeaders.Authorization, authHeader)
        }
        val payload = json.decodeFromString<ContentChangesResponse>(changes.bodyAsText())
        assertTrue(payload.deletions.any { it.id == "note-3" })
    }

    @Test
    fun `content changes paginates and reports hasMore`() = testApplication {
        val env = configureSyncTestApp()
        val authHeader = authHeader(env.tokenService)

        val payloads = listOf(
            """{ "id": "note-p1", "type": "TEXT", "content": "one", "mediaUri": null, "createdAt": 1, "lastUpdated": 1, "deviceId": "dev-1" }""",
            """{ "id": "note-p2", "type": "TEXT", "content": "two", "mediaUri": null, "createdAt": 2, "lastUpdated": 2, "deviceId": "dev-1" }""",
            """{ "id": "note-p3", "type": "TEXT", "content": "three", "mediaUri": null, "createdAt": 3, "lastUpdated": 3, "deviceId": "dev-1" }"""
        )

        payloads.forEach { payload ->
            val upload = client.post("/api/v1/sync/content") {
                header(HttpHeaders.Authorization, authHeader)
                contentType(ContentType.Application.Json)
                setBody(payload)
            }
            assertEquals(HttpStatusCode.OK, upload.status)
        }

        val changes = client.get("/api/v1/sync/content/changes?since=0&limit=2") {
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
        val uploadedAt: Long
    )

    @Serializable
    private data class JournalChangesPayload(
        val changes: List<JournalChange>,
        val deletions: List<JournalDeletion>,
        val lastTimestamp: Long
    ) {
        @Serializable
        data class JournalChange(
            val id: String,
            val title: String,
            val description: String?,
            val serverVersion: Long
        )

        @Serializable
        data class JournalDeletion(
            val id: String,
            val deletedAt: Long
        )
    }

    @Test
    fun `journal upload and changes work end-to-end`() = testApplication {
        val env = configureSyncTestApp()
        val authHeader = authHeader(env.tokenService)

        // Upload journal
        val upload = client.post("/api/v1/sync/journals") {
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
                """.trimIndent()
            )
        }
        assertEquals(HttpStatusCode.OK, upload.status)

        // Check changes
        val changes = client.get("/api/v1/sync/journals/changes?since=0") {
            header(HttpHeaders.Authorization, authHeader)
        }
        assertEquals(HttpStatusCode.OK, changes.status)

        val payload = json.decodeFromString<JournalChangesPayload>(changes.bodyAsText())
        assertEquals(1, payload.changes.size)
        assertEquals("journal-1", payload.changes.first().id)
        assertEquals("My Journal", payload.changes.first().title)
    }

    @Test
    fun `journal update works with version constraint`() = testApplication {
        val env = configureSyncTestApp()
        val authHeader = authHeader(env.tokenService)

        // Create journal
        client.post("/api/v1/sync/journals") {
            header(HttpHeaders.Authorization, authHeader)
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "id": "journal-2",
                  "title": "Original Title",
                  "description": null,
                  "createdAt": 1000,
                  "lastUpdated": 1000,
                  "deviceId": "dev-1"
                }
                """.trimIndent()
            )
        }

        // Update with no version constraint (should succeed)
        val update = client.post("/api/v1/sync/journals/journal-2") {
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
                """.trimIndent()
            )
        }
        assertEquals(HttpStatusCode.OK, update.status)
    }

    @Test
    fun `journal delete creates tombstone`() = testApplication {
        val env = configureSyncTestApp()
        val authHeader = authHeader(env.tokenService)

        // Create journal
        client.post("/api/v1/sync/journals") {
            header(HttpHeaders.Authorization, authHeader)
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "id": "journal-3",
                  "title": "To Delete",
                  "description": null,
                  "createdAt": 1000,
                  "lastUpdated": 1000,
                  "deviceId": "dev-1"
                }
                """.trimIndent()
            )
        }

        // Delete
        val delete = client.post("/api/v1/sync/journals/journal-3/delete") {
            header(HttpHeaders.Authorization, authHeader)
        }
        assertEquals(HttpStatusCode.OK, delete.status)

        // Check deletions
        val changes = client.get("/api/v1/sync/journals/changes?since=0") {
            header(HttpHeaders.Authorization, authHeader)
        }
        val payload = json.decodeFromString<JournalChangesPayload>(changes.bodyAsText())
        assertTrue(payload.deletions.any { it.id == "journal-3" })
    }

    @Serializable
    private data class AssociationUploadResponse(
        val count: Int,
        val uploadedAt: Long
    )

    @Test
    fun `association upload and changes work`() = testApplication {
        val env = configureSyncTestApp()
        val authHeader = authHeader(env.tokenService)

        // Upload associations
        val upload = client.post("/api/v1/sync/associations") {
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
                """.trimIndent()
            )
        }
        assertEquals(HttpStatusCode.OK, upload.status)

        // Check changes
        val changes = client.get("/api/v1/sync/associations/changes?since=0") {
            header(HttpHeaders.Authorization, authHeader)
        }
        assertEquals(HttpStatusCode.OK, changes.status)
    }

    @Test
    fun `media upload and download returns bytes`() = testApplication {
        val env = configureSyncTestApp()
        val authHeader = authHeader(env.tokenService)
        val bytes = byteArrayOf(1, 2, 3, 4)

        val uploadRequest = MediaUploadRequest(
            contentId = "note-media",
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
        val download = client.get("/api/v1/sync/media/${uploadPayload.mediaId}") {
            header(HttpHeaders.Authorization, authHeader)
        }
        assertEquals(HttpStatusCode.OK, download.status)

        val downloadPayload = json.decodeFromString<MediaDownloadResponse>(download.bodyAsText())
        assertEquals(bytes.size.toLong(), downloadPayload.sizeBytes)
        assertTrue(downloadPayload.data.contentEquals(bytes))
    }

    @Test
    fun `missing since parameter returns bad request`() = testApplication {
        val env = configureSyncTestApp()
        val authHeader = authHeader(env.tokenService)
        val response = client.get("/api/v1/sync/content/changes") {
            header(HttpHeaders.Authorization, authHeader)
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `sync trigger endpoint is not available`() = testApplication {
        val env = configureSyncTestApp()
        val authHeader = authHeader(env.tokenService)
        val response = client.post("/api/v1/sync/") {
            header(HttpHeaders.Authorization, authHeader)
        }
        assertEquals(HttpStatusCode.NotFound, response.status)
    }
}
