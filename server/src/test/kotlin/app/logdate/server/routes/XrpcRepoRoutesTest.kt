package app.logdate.server.routes

import app.logdate.server.auth.Account
import app.logdate.server.configureAuthV1TestApp
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Integration tests for AT Protocol `com.atproto.repo` XRPC endpoints.
 *
 * This class validates the core repository operations, including record creation, listing,
 * retrieval, and deletion. It specifically tests the enforcement of repository ownership,
 * AT Protocol URI generation, and the use of CIDs for optimistic concurrency control via
 * swap semantics.
 */
@OptIn(ExperimentalUuidApi::class)
class XrpcRepoRoutesTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `repo xrpc endpoints create list get and delete content records`() =
        testApplication {
            val env = configureAuthV1TestApp()
            val account =
                runBlocking {
                    env.accountRepository.save(
                        Account(
                            id = Uuid.random(),
                            username = "alice",
                            displayName = "Alice",
                            createdAt = Clock.System.now(),
                        ),
                    )
                }
            val accessToken = env.tokenService.generateAccessToken(account.id.toString())

            val create =
                client.post("/xrpc/com.atproto.repo.createRecord") {
                    contentType(ContentType.Application.Json)
                    header(HttpHeaders.Authorization, "Bearer $accessToken")
                    setBody(
                        """
                        {
                          "repo": "alice.logdate.app",
                          "collection": "studio.hypertext.logdate.content",
                          "rkey": "entry-1",
                          "record": {
                            "${'$'}type": "studio.hypertext.logdate.content",
                            "type": "TEXT",
                            "content": "hello from xrpc",
                            "createdAt": 1,
                            "lastUpdated": 1
                          }
                        }
                        """.trimIndent(),
                    )
                }
            assertEquals(HttpStatusCode.OK, create.status)
            val createdPayload = json.parseToJsonElement(create.bodyAsText()).jsonObject
            val createdCid = createdPayload["cid"]?.jsonPrimitive?.content
            assertTrue(createdCid?.startsWith("b") == true)

            val list =
                client.get("/xrpc/com.atproto.repo.listRecords?repo=alice.logdate.app&collection=studio.hypertext.logdate.content") {
                    header(HttpHeaders.Authorization, "Bearer $accessToken")
                }
            assertEquals(HttpStatusCode.OK, list.status)
            val listPayload = json.parseToJsonElement(list.bodyAsText()).jsonObject
            val listedRecord = listPayload["records"]?.jsonArray?.single()?.jsonObject
            assertTrue(
                listedRecord?.get("uri")?.jsonPrimitive?.content?.startsWith(
                    "at://did:plc:",
                ) == true,
            )

            val getRecord =
                client.get(
                    "/xrpc/com.atproto.repo.getRecord" +
                        "?repo=alice.logdate.app&collection=studio.hypertext.logdate.content&rkey=entry-1&cid=$createdCid",
                ) {
                    header(HttpHeaders.Authorization, "Bearer $accessToken")
                }
            assertEquals(HttpStatusCode.OK, getRecord.status)
            val getPayload = json.parseToJsonElement(getRecord.bodyAsText()).jsonObject
            assertEquals(
                "hello from xrpc",
                getPayload["value"]
                    ?.jsonObject
                    ?.get("content")
                    ?.jsonPrimitive
                    ?.content,
            )

            val delete =
                client.post("/xrpc/com.atproto.repo.deleteRecord") {
                    contentType(ContentType.Application.Json)
                    header(HttpHeaders.Authorization, "Bearer $accessToken")
                    setBody(
                        """
                        {
                          "repo": "alice.logdate.app",
                          "collection": "studio.hypertext.logdate.content",
                          "rkey": "entry-1",
                          "swapRecord": "$createdCid"
                        }
                        """.trimIndent(),
                    )
                }
            assertEquals(HttpStatusCode.OK, delete.status)

            val describeRepo = client.get("/xrpc/com.atproto.repo.describeRepo?repo=alice.logdate.app")
            assertEquals(HttpStatusCode.OK, describeRepo.status)
            val describePayload = json.parseToJsonElement(describeRepo.bodyAsText()).jsonObject
            assertTrue(describePayload["collections"]?.jsonArray?.isEmpty() == true)
        }

    @Test
    fun `repo xrpc endpoints enforce auth ownership and swap semantics`() =
        testApplication {
            val env = configureAuthV1TestApp()
            val first =
                runBlocking {
                    env.accountRepository.save(
                        Account(
                            id = Uuid.random(),
                            username = "brie",
                            displayName = "Brie",
                            createdAt = Clock.System.now(),
                        ),
                    )
                }
            val second =
                runBlocking {
                    env.accountRepository.save(
                        Account(
                            id = Uuid.random(),
                            username = "cora",
                            displayName = "Cora",
                            createdAt = Clock.System.now(),
                        ),
                    )
                }
            val firstToken = env.tokenService.generateAccessToken(first.id.toString())
            val secondToken = env.tokenService.generateAccessToken(second.id.toString())

            val unauthenticated =
                client.post("/xrpc/com.atproto.repo.createRecord") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"repo":"brie.logdate.app","collection":"studio.hypertext.logdate.content","record":{"type":"TEXT"}}""")
                }
            assertEquals(HttpStatusCode.Unauthorized, unauthenticated.status)

            val created =
                client.post("/xrpc/com.atproto.repo.createRecord") {
                    contentType(ContentType.Application.Json)
                    header(HttpHeaders.Authorization, "Bearer $firstToken")
                    setBody(
                        """
                        {
                          "repo": "brie.logdate.app",
                          "collection": "studio.hypertext.logdate.content",
                          "rkey": "entry-2",
                          "record": { "type": "TEXT" }
                        }
                        """.trimIndent(),
                    )
                }
            val createdCid =
                json
                    .parseToJsonElement(created.bodyAsText())
                    .jsonObject["cid"]
                    ?.jsonPrimitive
                    ?.content

            val wrongOwner =
                client.post("/xrpc/com.atproto.repo.putRecord") {
                    contentType(ContentType.Application.Json)
                    header(HttpHeaders.Authorization, "Bearer $secondToken")
                    setBody(
                        """
                        {
                          "repo": "brie.logdate.app",
                          "collection": "studio.hypertext.logdate.content",
                          "rkey": "entry-2",
                          "record": { "type": "TEXT" }
                        }
                        """.trimIndent(),
                    )
                }
            assertEquals(HttpStatusCode.Forbidden, wrongOwner.status)
            assertTrue(wrongOwner.bodyAsText().contains("RepoMismatch"))

            val invalidSwap =
                client.post("/xrpc/com.atproto.repo.putRecord") {
                    contentType(ContentType.Application.Json)
                    header(HttpHeaders.Authorization, "Bearer $firstToken")
                    setBody(
                        """
                        {
                          "repo": "brie.logdate.app",
                          "collection": "studio.hypertext.logdate.content",
                          "rkey": "entry-2",
                          "swapRecord": "bafk-invalid",
                          "record": { "type": "TEXT" }
                        }
                        """.trimIndent(),
                    )
                }
            assertEquals(HttpStatusCode.BadRequest, invalidSwap.status)
            assertTrue(invalidSwap.bodyAsText().contains("InvalidSwap"))

            val listWithCursor =
                client.get(
                    "/xrpc/com.atproto.repo.listRecords" +
                        "?repo=brie.logdate.app&collection=studio.hypertext.logdate.content&cursor=not-a-number",
                ) {
                    header(HttpHeaders.Authorization, "Bearer $firstToken")
                }
            assertEquals(HttpStatusCode.OK, listWithCursor.status)
            val cursorPayload = json.parseToJsonElement(listWithCursor.bodyAsText()).jsonObject
            assertTrue(cursorPayload.containsKey("records"))

            val deleteWithSwap =
                client.post("/xrpc/com.atproto.repo.deleteRecord") {
                    contentType(ContentType.Application.Json)
                    header(HttpHeaders.Authorization, "Bearer $firstToken")
                    setBody(
                        """
                        {
                          "repo": "brie.logdate.app",
                          "collection": "studio.hypertext.logdate.content",
                          "rkey": "entry-2",
                          "swapRecord": "$createdCid"
                        }
                        """.trimIndent(),
                    )
                }
            assertEquals(HttpStatusCode.OK, deleteWithSwap.status)
        }

    @Test
    fun `repo reads answer only the signed-in owner and never confirm another repo`() =
        testApplication {
            val env = configureAuthV1TestApp()
            val owner =
                runBlocking {
                    env.accountRepository.save(
                        Account(id = Uuid.random(), username = "dana", displayName = "Dana", createdAt = Clock.System.now()),
                    )
                }
            val other =
                runBlocking {
                    env.accountRepository.save(
                        Account(id = Uuid.random(), username = "eli", displayName = "Eli", createdAt = Clock.System.now()),
                    )
                }
            val ownerToken = env.tokenService.generateAccessToken(owner.id.toString())
            val otherToken = env.tokenService.generateAccessToken(other.id.toString())
            val created =
                client.post("/xrpc/com.atproto.repo.createRecord") {
                    contentType(ContentType.Application.Json)
                    header(HttpHeaders.Authorization, "Bearer $ownerToken")
                    setBody(
                        """
                        {
                          "repo": "dana.logdate.app",
                          "collection": "studio.hypertext.logdate.content",
                          "rkey": "entry-1",
                          "record": { "type": "TEXT", "content": "private words" }
                        }
                        """.trimIndent(),
                    )
                }
            assertEquals(HttpStatusCode.OK, created.status)
            val ownerDid = runBlocking { requireNotNull(env.atprotoIdentityService.findByHandle("dana.logdate.app")?.did) }

            fun readsOf(
                handle: String,
                did: String,
            ) = listOf(
                "/xrpc/com.atproto.repo.getRecord?repo=$handle&collection=studio.hypertext.logdate.content&rkey=entry-1",
                "/xrpc/com.atproto.repo.listRecords?repo=$handle&collection=studio.hypertext.logdate.content",
                "/xrpc/com.atproto.sync.getRepo?did=$did",
                "/xrpc/com.atproto.sync.getLatestCommit?did=$did",
                "/xrpc/com.atproto.sync.getRepoStatus?did=$did",
            )

            readsOf("dana.logdate.app", ownerDid)
                .zip(readsOf("nobody.logdate.app", MISSING_DID))
                .forEach { (ownerRead, missingRead) ->
                    val anonymous = client.get(ownerRead)
                    val anonymousMissing = client.get(missingRead)
                    assertEquals(HttpStatusCode.Unauthorized, anonymous.status, ownerRead)
                    assertTrue(anonymous.bodyAsText().contains("AuthRequired"), ownerRead)
                    assertEquals(anonymousMissing.status, anonymous.status, ownerRead)
                    assertEquals(anonymousMissing.bodyAsText(), anonymous.bodyAsText(), ownerRead)

                    val otherReading = client.get(ownerRead) { header(HttpHeaders.Authorization, "Bearer $otherToken") }
                    val otherMissing = client.get(missingRead) { header(HttpHeaders.Authorization, "Bearer $otherToken") }
                    assertNotEquals(HttpStatusCode.OK, otherReading.status, ownerRead)
                    assertEquals(otherMissing.status, otherReading.status, ownerRead)
                    // A missing repo's answer may name the identifier that was asked for, and nothing else.
                    assertEquals(otherMissing.bodyAsText().replace(MISSING_DID, ownerDid), otherReading.bodyAsText(), ownerRead)
                    assertFalse(otherReading.bodyAsText().contains("private words"), ownerRead)

                    val ownerReading = client.get(ownerRead) { header(HttpHeaders.Authorization, "Bearer $ownerToken") }
                    assertEquals(HttpStatusCode.OK, ownerReading.status, ownerRead)
                }

            val ownerRecord =
                client.get(readsOf("dana.logdate.app", ownerDid).first()) {
                    header(HttpHeaders.Authorization, "Bearer $ownerToken")
                }
            assertTrue(ownerRecord.bodyAsText().contains("private words"))
            val ownerExport =
                client.get("/xrpc/com.atproto.sync.getRepo?did=$ownerDid") {
                    header(HttpHeaders.Authorization, "Bearer $ownerToken")
                }
            assertEquals("application/vnd.ipld.car", ownerExport.contentType().toString())
        }

    private companion object {
        const val MISSING_DID = "did:plc:aaaaaaaaaaaaaaaaaaaaaaaa"
    }
}
