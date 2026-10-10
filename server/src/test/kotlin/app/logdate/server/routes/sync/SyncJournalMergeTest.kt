package app.logdate.server.routes.sync

import app.logdate.server.configureSyncTestApp
import app.logdate.shared.model.sync.JournalChangesResponse
import io.ktor.client.request.get
import io.ktor.client.request.header
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

class SyncJournalMergeTest {
    @Test
    fun `authenticated merge exposes redirect tombstone and typed resurrection conflict`() =
        testApplication {
            val env = configureSyncTestApp()
            val authorization = "Bearer ${env.tokenService.generateAccessToken(UUID.randomUUID().toString())}"
            val source = UUID.randomUUID().toString()
            val destination = UUID.randomUUID().toString()
            val operation = UUID.randomUUID().toString()
            val note = UUID.randomUUID().toString()
            for (id in listOf(source, destination)) {
                val response =
                    client.put("/api/v1/journals/$id") {
                        header(HttpHeaders.Authorization, authorization)
                        contentType(ContentType.Application.Json)
                        setBody(
                            """
                            {"id":"$id","title":"Title","description":"Description",
                             "createdAt":1,"lastUpdated":1,"deviceId":"test"}
                            """.trimIndent(),
                        )
                    }
                assertEquals(HttpStatusCode.Created, response.status)
            }
            val mergeBody = """{"operationId":"$operation","destinationId":"$destination","contentIds":["$note"]}"""
            val unauthorized =
                client.post("/api/v1/journals/$source/merge") {
                    contentType(ContentType.Application.Json)
                    setBody(mergeBody)
                }
            assertEquals(HttpStatusCode.Unauthorized, unauthorized.status)
            repeat(2) {
                val response =
                    client.post("/api/v1/journals/$source/merge") {
                        header(HttpHeaders.Authorization, authorization)
                        contentType(ContentType.Application.Json)
                        setBody(mergeBody)
                    }
                assertEquals(HttpStatusCode.OK, response.status)
                assertTrue(response.bodyAsText().contains(destination))
            }
            val feed = client.get("/api/v1/journals?since=0") { header(HttpHeaders.Authorization, authorization) }
            val changes = Json.decodeFromString<JournalChangesResponse>(feed.bodyAsText())
            assertEquals(destination, changes.deletions.single { it.id == source }.mergedIntoJournalId)
            val resurrection =
                client.put("/api/v1/journals/$source") {
                    header(HttpHeaders.Authorization, authorization)
                    contentType(ContentType.Application.Json)
                    setBody("""{"id":"$source","title":"Old","description":"Old","createdAt":1,"lastUpdated":1,"deviceId":"test"}""")
                }
            assertEquals(HttpStatusCode.Conflict, resurrection.status)
            assertTrue(resurrection.bodyAsText().contains("JOURNAL_MERGED"))
            assertTrue(resurrection.bodyAsText().contains(destination))
        }
}
