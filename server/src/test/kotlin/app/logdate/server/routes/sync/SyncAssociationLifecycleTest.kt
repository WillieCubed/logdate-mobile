package app.logdate.server.routes.sync

import app.logdate.server.routes.support.authHeader
import app.logdate.server.routes.support.configureInMemorySyncApp
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
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
 * Tests the lifecycle of journal-to-content associations in the Sync API.
 *
 * These tests ensure that associations between journals and content items can be
 * created and deleted, and that deletions are correctly propagated through the
 * synchronization changes feed to enable client-side reconciliation.
 */
class SyncAssociationLifecycleTest {
    @Test
    fun `association deletions are represented in changes feed`() =
        testApplication {
            val tokenService = configureInMemorySyncApp()
            val auth = authHeader(tokenService)

            val upsertAssociation =
                client.put("/api/v1/associations/journal-assoc/content-assoc") {
                    header(HttpHeaders.Authorization, auth)
                    contentType(ContentType.Application.Json)
                    setBody("""{"createdAt":1,"deviceId":"dev-1"}""")
                }
            assertEquals(HttpStatusCode.NoContent, upsertAssociation.status)

            val deleteAssociation =
                client.delete("/api/v1/associations/journal-assoc/content-assoc") {
                    header(HttpHeaders.Authorization, auth)
                }
            assertEquals(HttpStatusCode.NoContent, deleteAssociation.status)

            val associationChanges =
                client.get("/api/v1/associations?since=0&limit=20") {
                    header(HttpHeaders.Authorization, auth)
                }
            assertEquals(HttpStatusCode.OK, associationChanges.status)
            assertTrue(associationChanges.bodyAsText().contains("\"deletions\""))
            assertTrue(associationChanges.bodyAsText().contains("\"journalId\":\"journal-assoc\""))
        }
}
