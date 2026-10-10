package app.logdate.server.routes.sync

import app.logdate.server.configureSyncTestApp
import app.logdate.server.routes.support.authHeader
import app.logdate.server.routes.support.configureInMemorySyncApp
import app.logdate.server.routes.support.contentUploadBody
import app.logdate.server.routes.support.journalUploadBody
import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.head
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Strict create-only uploads, run against the in-memory sync app and the repo-backed app. */
class SyncConditionalCreateRoutesTest {
    @Test
    fun `a duplicate strict create changes nothing`() =
        bothHarnesses { auth ->
            val created = client.putContent(auth, "note-1", "first", strict = true)
            assertEquals(HttpStatusCode.Created, created.status)
            assertEquals("/api/v1/contents/note-1", created.headers[HttpHeaders.Location])
            val version =
                created
                    .json()
                    .getValue("serverVersion")
                    .jsonPrimitive.long

            val duplicate = client.putContent(auth, "note-1", "replayed", strict = true)

            assertEquals(HttpStatusCode.PreconditionFailed, duplicate.status)
            val error = duplicate.json()
            assertEquals("CONTENT_EXISTS", error.getValue("code").jsonPrimitive.content)
            assertEquals(
                version.toString(),
                error
                    .getValue("details")
                    .jsonObject
                    .getValue("serverVersion")
                    .jsonPrimitive.content,
            )
            val feed = client.getAs(auth, "/api/v1/contents?since=$version").json()
            assertTrue(feed.getValue("changes").jsonArray.isEmpty(), "a duplicate must not reach the change feed")
            assertTrue(feed.getValue("deletions").jsonArray.isEmpty())
            assertEquals(
                "first",
                client
                    .getAs(auth, "/api/v1/contents/note-1")
                    .json()
                    .getValue("content")
                    .jsonPrimitive.content,
            )
            val head = client.headAs(auth, "/api/v1/contents/note-1")
            assertEquals(HttpStatusCode.OK, head.status)
            assertEquals(version.toString(), head.headers["X-LogDate-Server-Version"])
        }

    @Test
    fun `a strict create after a delete answers gone and the record stays deleted`() =
        bothHarnesses { auth ->
            assertEquals(HttpStatusCode.Created, client.putContent(auth, "note-2", "first", strict = true).status)
            assertEquals(HttpStatusCode.NoContent, client.deleteAs(auth, "/api/v1/contents/note-2").status)

            val gone = client.putContent(auth, "note-2", "resurrected", strict = true)

            assertEquals(HttpStatusCode.Gone, gone.status)
            val error = gone.json()
            assertEquals("CONTENT_DELETED", error.getValue("code").jsonPrimitive.content)
            assertNotNull(error.getValue("details").jsonObject["deletedAt"])
            assertEquals(HttpStatusCode.NotFound, client.getAs(auth, "/api/v1/contents/note-2").status)
            val head = client.headAs(auth, "/api/v1/contents/note-2")
            assertEquals(HttpStatusCode.Gone, head.status)
            assertNotNull(head.headers["X-LogDate-Deleted-At"])
            assertEquals(HttpStatusCode.NotFound, client.headAs(auth, "/api/v1/contents/never-written").status)
        }

    @Test
    fun `If-None-Match still recreates deleted content for the Mac app`() =
        bothHarnesses { auth ->
            assertEquals(HttpStatusCode.Created, client.putContent(auth, "mac-1", "first", ifNoneMatch = true).status)
            val duplicate = client.putContent(auth, "mac-1", "again", ifNoneMatch = true)
            assertEquals(HttpStatusCode.PreconditionFailed, duplicate.status)
            assertNotNull(duplicate.json().getValue("details").jsonObject["serverVersion"])

            assertEquals(HttpStatusCode.NoContent, client.deleteAs(auth, "/api/v1/contents/mac-1").status)

            assertEquals(HttpStatusCode.Created, client.putContent(auth, "mac-1", "recreated", ifNoneMatch = true).status)
        }

    @Test
    fun `strict journal creation follows the same rules`() =
        bothHarnesses { auth ->
            assertEquals(HttpStatusCode.Created, client.putJournal(auth, "journal-1", "Walks").status)
            val duplicate = client.putJournal(auth, "journal-1", "Other")
            assertEquals(HttpStatusCode.PreconditionFailed, duplicate.status)
            assertEquals(
                "JOURNAL_EXISTS",
                duplicate
                    .json()
                    .getValue("code")
                    .jsonPrimitive.content,
            )

            assertEquals(HttpStatusCode.NoContent, client.deleteAs(auth, "/api/v1/journals/journal-1").status)
            val gone = client.putJournal(auth, "journal-1", "Back")

            assertEquals(HttpStatusCode.Gone, gone.status)
            assertEquals(
                "JOURNAL_DELETED",
                gone
                    .json()
                    .getValue("code")
                    .jsonPrimitive.content,
            )
        }

    @Test
    fun `strict link creation skips links that exist or were deleted`() =
        bothHarnesses { auth ->
            val first = client.postLinks(auth, "a", "b")
            assertEquals(HttpStatusCode.OK, first.status)
            assertEquals(
                2,
                first
                    .json()
                    .getValue("uploadedCount")
                    .jsonPrimitive.int,
            )
            assertEquals(HttpStatusCode.NoContent, client.deleteAs(auth, "/api/v1/associations/journal-1/b").status)

            val second = client.postLinks(auth, "a", "b", "c").json()

            assertEquals(1, second.getValue("uploadedCount").jsonPrimitive.int)
            assertEquals(listOf("a"), second.contentIds("existing"))
            assertEquals(listOf("b"), second.contentIds("deleted"))
        }

    private fun bothHarnesses(block: suspend ApplicationTestBuilder.(auth: String) -> Unit) {
        testApplication { block(authHeader(configureInMemorySyncApp())) }
        testApplication { block(authHeader(configureSyncTestApp().tokenService)) }
    }

    private suspend fun HttpClient.putContent(
        auth: String,
        id: String,
        content: String,
        strict: Boolean = false,
        ifNoneMatch: Boolean = false,
    ): HttpResponse =
        put("/api/v1/contents/$id" + (if (strict) "?ifAbsent=true" else "")) {
            header(HttpHeaders.Authorization, auth)
            if (ifNoneMatch) header(HttpHeaders.IfNoneMatch, "*")
            contentType(ContentType.Application.Json)
            setBody(contentUploadBody(id = id, content = content))
        }

    private suspend fun HttpClient.putJournal(
        auth: String,
        id: String,
        title: String,
    ): HttpResponse =
        put("/api/v1/journals/$id?ifAbsent=true") {
            header(HttpHeaders.Authorization, auth)
            contentType(ContentType.Application.Json)
            setBody(journalUploadBody(id = id, title = title))
        }

    private suspend fun HttpClient.postLinks(
        auth: String,
        vararg contentIds: String,
    ): HttpResponse =
        post("/api/v1/associations?ifAbsent=true") {
            header(HttpHeaders.Authorization, auth)
            contentType(ContentType.Application.Json)
            val links = contentIds.joinToString(",") { """{"journalId":"journal-1","contentId":"$it","createdAt":1,"deviceId":"watch"}""" }
            setBody("""{"associations":[$links]}""")
        }

    private suspend fun HttpClient.getAs(
        auth: String,
        path: String,
    ): HttpResponse = get(path) { header(HttpHeaders.Authorization, auth) }

    private suspend fun HttpClient.headAs(
        auth: String,
        path: String,
    ): HttpResponse = head(path) { header(HttpHeaders.Authorization, auth) }

    private suspend fun HttpClient.deleteAs(
        auth: String,
        path: String,
    ): HttpResponse = delete(path) { header(HttpHeaders.Authorization, auth) }

    private suspend fun HttpResponse.json(): JsonObject = Json.parseToJsonElement(bodyAsText()).jsonObject

    private fun JsonObject.contentIds(list: String): List<String> =
        getValue(list).jsonArray.map {
            it.jsonObject
                .getValue("contentId")
                .jsonPrimitive.content
        }
}
