package app.logdate.server.routes.sync

import app.logdate.server.auth.InMemoryAccountRepository
import app.logdate.server.auth.JwtTokenService
import app.logdate.server.configureSyncTestApp
import app.logdate.server.routes.support.DeviceLinkTestKit
import app.logdate.server.routes.support.assertSyncErrorCode
import app.logdate.server.routes.support.authHeader
import app.logdate.server.routes.support.contentUploadBody
import app.logdate.server.routes.support.jsonBody
import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
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
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class)
class SyncMomentRoutesTest {
    private val tokenService = JwtTokenService("moment-routes-secret")

    @Test
    fun `a moment round trips through put get patch and delete`() =
        testApplication {
            configureSyncTestApp(tokenService = tokenService)
            val auth = authHeader(tokenService)

            val created = client.putMoment(auth, "moment-1")
            assertEquals(HttpStatusCode.Created, created.status)
            assertEquals("/api/v1/moments/moment-1", created.headers[HttpHeaders.Location])
            val firstVersion =
                created
                    .jsonBody()
                    .getValue("serverVersion")
                    .jsonPrimitive.long
            assertEquals(HttpStatusCode.OK, client.putMoment(auth, "moment-1").status)

            val read = client.getAs(auth, "/api/v1/moments/moment-1").jsonBody()
            assertEquals("LDSE2:moment-1", read.getValue("payload").jsonPrimitive.content)
            assertEquals(1, read.getValue("schema").jsonPrimitive.int)
            val current = read.getValue("serverVersion").jsonPrimitive.long

            assertSyncErrorCode(client.patchMoment(auth, "moment-1", knownVersion = firstVersion), HttpStatusCode.Conflict, "CONFLICT")
            assertEquals(HttpStatusCode.OK, client.patchMoment(auth, "moment-1", knownVersion = current).status)
            val patched = client.getAs(auth, "/api/v1/moments/moment-1").jsonBody()
            assertEquals("LDSE2:edited", patched.getValue("payload").jsonPrimitive.content)
            assertSyncErrorCode(client.patchMoment(auth, "never-written", knownVersion = null), HttpStatusCode.NotFound, "NOT_FOUND")

            assertEquals(HttpStatusCode.NoContent, client.deleteAs(auth, "/api/v1/moments/moment-1").status)
            assertEquals(HttpStatusCode.NotFound, client.getAs(auth, "/api/v1/moments/moment-1").status)
            val feed = client.getAs(auth, "/api/v1/moments?since=0").jsonBody()
            assertTrue(feed.getValue("changes").jsonArray.isEmpty())
            assertEquals(listOf("moment-1"), feed.ids("deletions"))
        }

    @Test
    fun `moment payloads must be encrypted and ids must match`() =
        testApplication {
            configureSyncTestApp(tokenService = tokenService)
            val auth = authHeader(tokenService)

            assertSyncErrorCode(client.putMoment(auth, "m1", payload = "plain text"), HttpStatusCode.BadRequest, "INVALID_MOMENT")
            assertSyncErrorCode(client.putMoment(auth, "m1", schema = 0), HttpStatusCode.BadRequest, "INVALID_MOMENT")
            val oversized = "LDSE2:" + "x".repeat(MAX_MOMENT_PAYLOAD_LENGTH)
            assertSyncErrorCode(client.putMoment(auth, "m1", payload = oversized), HttpStatusCode.BadRequest, "INVALID_MOMENT")
            assertSyncErrorCode(client.putMoment(auth, "m1", bodyId = "m2"), HttpStatusCode.BadRequest, "VALIDATION_ERROR")
            assertEquals(HttpStatusCode.Unauthorized, client.putMoment(auth = null, id = "m1").status)
        }

    @Test
    fun `strict moment creation never brings back a deleted moment`() =
        testApplication {
            configureSyncTestApp(tokenService = tokenService)
            val auth = authHeader(tokenService)

            val created = client.putMoment(auth, "mark-1", strict = true)
            assertEquals(HttpStatusCode.Created, created.status)
            val version =
                created
                    .jsonBody()
                    .getValue("serverVersion")
                    .jsonPrimitive.long
            val duplicate = client.putMoment(auth, "mark-1", strict = true)
            assertSyncErrorCode(duplicate, HttpStatusCode.PreconditionFailed, "MOMENT_EXISTS")
            val stored =
                duplicate
                    .jsonBody()
                    .getValue("details")
                    .jsonObject
                    .getValue("serverVersion")
                    .jsonPrimitive.content
            assertEquals(version.toString(), stored)

            assertEquals(HttpStatusCode.NoContent, client.deleteAs(auth, "/api/v1/moments/mark-1").status)
            val gone = client.putMoment(auth, "mark-1", strict = true)
            assertSyncErrorCode(gone, HttpStatusCode.Gone, "MOMENT_DELETED")
            assertNotNull(gone.jsonBody().getValue("details").jsonObject["deletedAt"])
            assertEquals(HttpStatusCode.Created, client.putMoment(auth, "mark-1", ifNoneMatch = true).status)
        }

    @Test
    fun `moments leave the entry and journal feeds and the status counts alone`() =
        testApplication {
            configureSyncTestApp(tokenService = tokenService)
            val auth = authHeader(tokenService)
            client.putMoment(auth, "mark-1")
            client.putMoment(auth, "mark-2")
            client.deleteAs(auth, "/api/v1/moments/mark-2")

            listOf("/api/v1/contents?since=0", "/api/v1/journals?since=0", "/api/v1/associations?since=0").forEach { path ->
                val feed = client.getAs(auth, path).jsonBody()
                assertTrue(feed.getValue("changes").jsonArray.isEmpty(), path)
                assertTrue(feed.getValue("deletions").jsonArray.isEmpty(), path)
            }
            val status =
                client
                    .getAs(auth, "/api/v1/ops/sync/status")
                    .jsonBody()
                    .getValue("data")
                    .jsonObject
            assertEquals(0, status.getValue("contentCount").jsonPrimitive.int)
            assertEquals(0, status.getValue("journalCount").jsonPrimitive.int)
            assertEquals(0, status.getValue("associationCount").jsonPrimitive.int)

            client.put("/api/v1/contents/note-1") {
                header(HttpHeaders.Authorization, auth)
                contentType(ContentType.Application.Json)
                setBody(contentUploadBody(id = "note-1"))
            }
            assertEquals(listOf("mark-1"), client.getAs(auth, "/api/v1/moments?since=0").jsonBody().ids("changes"))
        }

    @Test
    fun `a linked watch can write moments`() =
        testApplication {
            val kit = DeviceLinkTestKit(tokenService, InMemoryAccountRepository())
            configureSyncTestApp(tokenService = tokenService, accessTokenVerifier = kit.verifier)
            val watch = kit.linkWatch(kit.newAccount("moment_owner"))

            val marked = client.putMoment("Bearer ${watch.tokens.accessToken}", "watch-mark-1", strict = true)
            assertEquals(HttpStatusCode.Created, marked.status)
        }

    private suspend fun HttpClient.putMoment(
        auth: String?,
        id: String,
        payload: String = "LDSE2:$id",
        schema: Int = 1,
        bodyId: String = id,
        strict: Boolean = false,
        ifNoneMatch: Boolean = false,
    ): HttpResponse =
        put("/api/v1/moments/$id" + (if (strict) "?ifAbsent=true" else "")) {
            auth?.let { header(HttpHeaders.Authorization, it) }
            if (ifNoneMatch) header(HttpHeaders.IfNoneMatch, "*")
            contentType(ContentType.Application.Json)
            setBody(
                """{"id":"$bodyId","capturedAt":1789221791412,"lastUpdated":1789221791412,""" +
                    """"deviceId":"pixel-watch-3","payload":"$payload","schema":$schema}""",
            )
        }

    private suspend fun HttpClient.patchMoment(
        auth: String,
        id: String,
        knownVersion: Long?,
    ): HttpResponse =
        patch("/api/v1/moments/$id") {
            header(HttpHeaders.Authorization, auth)
            contentType(ContentType.Application.Json)
            val constraint = knownVersion?.let { """{"type":"known","serverVersion":$it}""" } ?: """{"type":"none"}"""
            setBody(
                """{"payload":"LDSE2:edited","schema":1,"lastUpdated":1789221800000,"deviceId":"pixel-watch-3",""" +
                    """"versionConstraint":$constraint}""",
            )
        }

    private suspend fun HttpClient.getAs(
        auth: String,
        path: String,
    ): HttpResponse = get(path) { header(HttpHeaders.Authorization, auth) }

    private suspend fun HttpClient.deleteAs(
        auth: String,
        path: String,
    ): HttpResponse = delete(path) { header(HttpHeaders.Authorization, auth) }

    private fun JsonObject.ids(list: String): List<String> =
        getValue(list).jsonArray.map {
            it.jsonObject
                .getValue("id")
                .jsonPrimitive.content
        }
}
