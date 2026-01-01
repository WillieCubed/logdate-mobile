package app.logdate.server.routes

import app.logdate.server.module
import io.ktor.client.call.body
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SyncRoutesTest {

    private val json = Json { ignoreUnknownKeys = true }

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

    @Serializable
    private data class ContentChangesPayload(
        val data: ChangesData
    ) {
        @Serializable
        data class ChangesData(
            val changes: List<ContentChange>,
            val deletions: List<ContentDeletion>,
            val lastTimestamp: Long
        )

        @Serializable
        data class ContentChange(
            val id: String,
            val serverVersion: Long
        )

        @Serializable
        data class ContentDeletion(
            val id: String,
            val deletedAt: Long
        )
    }

    @Test
    fun `sync status returns ok and counts`() = testApplication {
        application { module() }

        val response = client.get("/api/v1/sync/status")
        assertEquals(HttpStatusCode.OK, response.status)

        val payload = json.decodeFromString<SyncStatusPayload>(response.bodyAsText())
        assertTrue(payload.data.contentCount >= 0)
        assertTrue(payload.data.journalCount >= 0)
        assertTrue(payload.data.associationCount >= 0)
    }

    @Test
    fun `content upload appears in change feed`() = testApplication {
        application { module() }

        val upload = client.post("/api/v1/sync/content") {
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

        val changes = client.get("/api/v1/sync/content/changes?since=0")
        assertEquals(HttpStatusCode.OK, changes.status)

        val payload = json.decodeFromString<ContentChangesPayload>(changes.bodyAsText())
        assertEquals(1, payload.data.changes.size)
        assertEquals("note-1", payload.data.changes.first().id)
    }

    @Test
    fun `content update detects conflict when version is stale`() = testApplication {
        application { module() }

        // seed initial content
        client.post("/api/v1/sync/content") {
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
                  "deviceId": "dev-1"
                }
                """.trimIndent()
            )
        }

        // update with stale version constraint
        val conflict = client.post("/api/v1/sync/content/note-2") {
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "content": "second",
                  "mediaUri": null,
                  "lastUpdated": 2,
                  "deviceId": "dev-1",
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
        application { module() }

        client.post("/api/v1/sync/content") {
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

        val delete = client.post("/api/v1/sync/content/note-3/delete")
        assertEquals(HttpStatusCode.OK, delete.status)

        val changes = client.get("/api/v1/sync/content/changes?since=0")
        val payload = json.decodeFromString<ContentChangesPayload>(changes.bodyAsText())
        assertTrue(payload.data.deletions.any { it.id == "note-3" })
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
        application { module() }

        // Upload journal
        val upload = client.post("/api/v1/sync/journals") {
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
        val changes = client.get("/api/v1/sync/journals/changes?since=0")
        assertEquals(HttpStatusCode.OK, changes.status)

        val payload = json.decodeFromString<JournalChangesPayload>(changes.bodyAsText())
        assertEquals(1, payload.changes.size)
        assertEquals("journal-1", payload.changes.first().id)
        assertEquals("My Journal", payload.changes.first().title)
    }

    @Test
    fun `journal update works with version constraint`() = testApplication {
        application { module() }

        // Create journal
        client.post("/api/v1/sync/journals") {
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
        application { module() }

        // Create journal
        client.post("/api/v1/sync/journals") {
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
        val delete = client.post("/api/v1/sync/journals/journal-3/delete")
        assertEquals(HttpStatusCode.OK, delete.status)

        // Check deletions
        val changes = client.get("/api/v1/sync/journals/changes?since=0")
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
        application { module() }

        // Upload associations
        val upload = client.post("/api/v1/sync/associations") {
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
        val changes = client.get("/api/v1/sync/associations/changes?since=0")
        assertEquals(HttpStatusCode.OK, changes.status)
    }

    @Test
    fun `missing since parameter returns bad request`() = testApplication {
        application { module() }

        val response = client.get("/api/v1/sync/content/changes")
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `sync trigger endpoint returns started status`() = testApplication {
        application { module() }

        val response = client.post("/api/v1/sync/")
        assertEquals(HttpStatusCode.OK, response.status)
    }
}
