package app.logdate.server.routes.sync

import app.logdate.server.routes.support.authHeader
import app.logdate.server.routes.support.configureInMemorySyncApp
import app.logdate.server.routes.support.contentUpdateBody
import app.logdate.server.routes.support.contentUploadBody
import app.logdate.server.routes.support.journalUpdateBody
import app.logdate.server.routes.support.journalUploadBody
import io.ktor.client.request.delete
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Validates the lifecycle management for content and journal records in the Sync API.
 *
 * This suite verifies the consistency of resource identifiers, the correct handling of
 * version-based concurrency conflicts during updates, and the proper processing of
 * full and partial (PATCH) updates for both content and journal collections.
 */
class SyncContentAndJournalLifecycleTest {
    @Test
    fun `content and journal endpoints enforce id consistency and version conflict behavior`() =
        testApplication {
            val tokenService = configureInMemorySyncApp()
            val auth = authHeader(tokenService)

            val createContent =
                client.put("/api/v1/contents/content-branch-1") {
                    header(HttpHeaders.Authorization, auth)
                    contentType(ContentType.Application.Json)
                    setBody(contentUploadBody(id = "content-branch-1", content = "one"))
                }
            assertEquals(HttpStatusCode.Created, createContent.status)

            val updateContent =
                client.put("/api/v1/contents/content-branch-1") {
                    header(HttpHeaders.Authorization, auth)
                    contentType(ContentType.Application.Json)
                    setBody(contentUploadBody(id = "content-branch-1", content = "two"))
                }
            assertEquals(HttpStatusCode.OK, updateContent.status)

            val contentMismatch =
                client.put("/api/v1/contents/content-path") {
                    header(HttpHeaders.Authorization, auth)
                    contentType(ContentType.Application.Json)
                    setBody(contentUploadBody(id = "content-body"))
                }
            assertEquals(HttpStatusCode.BadRequest, contentMismatch.status)

            val patchContent =
                client.patch("/api/v1/contents/content-patch-missing") {
                    header(HttpHeaders.Authorization, auth)
                    contentType(ContentType.Application.Json)
                    setBody(
                        contentUpdateBody(
                            content = "patched",
                            mediaUri = null,
                            lastUpdated = 123L,
                            deviceId = "dev-a",
                        ),
                    )
                }
            assertEquals(HttpStatusCode.OK, patchContent.status)

            val createJournal =
                client.put("/api/v1/journals/journal-branch-1") {
                    header(HttpHeaders.Authorization, auth)
                    contentType(ContentType.Application.Json)
                    setBody(journalUploadBody(id = "journal-branch-1", title = "A"))
                }
            assertEquals(HttpStatusCode.Created, createJournal.status)

            val updateJournal =
                client.put("/api/v1/journals/journal-branch-1") {
                    header(HttpHeaders.Authorization, auth)
                    contentType(ContentType.Application.Json)
                    setBody(journalUploadBody(id = "journal-branch-1", title = "B"))
                }
            assertEquals(HttpStatusCode.OK, updateJournal.status)

            val journalMismatch =
                client.put("/api/v1/journals/journal-path") {
                    header(HttpHeaders.Authorization, auth)
                    contentType(ContentType.Application.Json)
                    setBody(journalUploadBody(id = "journal-body"))
                }
            assertEquals(HttpStatusCode.BadRequest, journalMismatch.status)

            val patchJournal =
                client.patch("/api/v1/journals/journal-patch-missing") {
                    header(HttpHeaders.Authorization, auth)
                    contentType(ContentType.Application.Json)
                    setBody(
                        journalUpdateBody(
                            title = "patched",
                            description = "desc",
                            lastUpdated = 222L,
                            deviceId = "dev-a",
                        ),
                    )
                }
            assertEquals(HttpStatusCode.OK, patchJournal.status)

            val journalPatchConflict =
                client.patch("/api/v1/journals/journal-branch-1") {
                    header(HttpHeaders.Authorization, auth)
                    contentType(ContentType.Application.Json)
                    setBody(
                        journalUpdateBody(
                            title = "conflict",
                            description = "conflict",
                            lastUpdated = 333L,
                            deviceId = "dev-a",
                            knownVersion = 0L,
                        ),
                    )
                }
            assertEquals(HttpStatusCode.Conflict, journalPatchConflict.status)
            assertTrue(journalPatchConflict.bodyAsText().contains("CONFLICT"))

            assertEquals(
                HttpStatusCode.NoContent,
                client
                    .delete("/api/v1/contents/content-branch-1") {
                        header(HttpHeaders.Authorization, auth)
                    }.status,
            )
            assertEquals(
                HttpStatusCode.NoContent,
                client
                    .delete("/api/v1/journals/journal-branch-1") {
                        header(HttpHeaders.Authorization, auth)
                    }.status,
            )
        }
}
