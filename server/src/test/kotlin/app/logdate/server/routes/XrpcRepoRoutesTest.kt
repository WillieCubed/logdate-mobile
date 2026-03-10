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
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

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

            val list = client.get("/xrpc/com.atproto.repo.listRecords?repo=alice.logdate.app&collection=studio.hypertext.logdate.content")
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
                )
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
                )
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
}
