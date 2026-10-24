package app.logdate.server.atproto.firehose

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import studio.hypertext.atproto.repo.CarCodec
import studio.hypertext.atproto.repo.DefaultRepoEngine
import studio.hypertext.atproto.repo.RepoRecordId
import studio.hypertext.atproto.syntax.Nsid
import studio.hypertext.atproto.syntax.RecordKey
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi

/**
 * What anyone can read without signing in, and what stays closed. The repository is a real one
 * written through the real routes; the private records are written straight into the same tree so
 * the tests show the public methods refusing data that is actually there.
 */
@OptIn(ExperimentalUuidApi::class)
class PublicRepoRoutesTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val profile = "app.logdate.actor.profile"
    private val document = "site.standard.document"

    private fun profileRecord(name: String = "Alice"): JsonObject =
        buildJsonObject {
            put("\$type", profile)
            put("displayName", name)
        }

    private fun documentRecord(title: String): JsonObject =
        buildJsonObject {
            put("\$type", document)
            put("title", title)
        }

    private suspend fun HttpResponse.parsed(): JsonObject = json.parseToJsonElement(bodyAsText()).jsonObject

    private suspend fun ApplicationTestBuilder.get(path: String): HttpResponse = client.get(path)

    @Test
    fun `anyone can read a public record without signing in`() =
        testApplication {
            val h = FirehoseHarness().also { it.install(this) }
            val alice = h.createAccount("alice")
            assertEquals(HttpStatusCode.OK, h.put(client, alice, profile, "self", profileRecord()).status)

            val response = get("/xrpc/com.atproto.repo.getRecord?repo=${alice.did}&collection=$profile&rkey=self")

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(
                "Alice",
                response
                    .parsed()
                    .getValue("value")
                    .jsonObject
                    .getValue("displayName")
                    .jsonPrimitive.content,
            )
        }

    @Test
    fun `a record can be read by the handle of its account`() =
        testApplication {
            val h = FirehoseHarness().also { it.install(this) }
            val alice = h.createAccount("alice")
            h.put(client, alice, profile, "self", profileRecord())

            val response = get("/xrpc/com.atproto.repo.getRecord?repo=${alice.handle}&collection=$profile&rkey=self")

            assertEquals(HttpStatusCode.OK, response.status)
        }

    @Test
    fun `a record can be fetched by the exact version only`() =
        testApplication {
            val h = FirehoseHarness().also { it.install(this) }
            val alice = h.createAccount("alice")
            h.put(client, alice, profile, "self", profileRecord())
            val cid =
                get(
                    "/xrpc/com.atproto.repo.getRecord?repo=${alice.did}&collection=$profile&rkey=self",
                ).parsed().getValue("cid").jsonPrimitive.content

            val same = get("/xrpc/com.atproto.repo.getRecord?repo=${alice.did}&collection=$profile&rkey=self&cid=$cid")
            val other =
                get(
                    "/xrpc/com.atproto.repo.getRecord?repo=${alice.did}&collection=$profile&rkey=self&cid=bafyreigdyrzt5sfp7udm7hu76uh7y26nf3efuylqabf3oclgtqy55fbzdi",
                )

            assertEquals(HttpStatusCode.OK, same.status)
            assertEquals(HttpStatusCode.BadRequest, other.status)
        }

    @Test
    fun `anyone can list the records of a public collection in order`() =
        testApplication {
            val h = FirehoseHarness().also { it.install(this) }
            val alice = h.createAccount("alice")
            listOf("3kaaa", "3kbbb", "3kccc").forEach { key -> h.put(client, alice, document, key, documentRecord(key)) }

            val page = get("/xrpc/com.atproto.repo.listRecords?repo=${alice.did}&collection=$document&limit=2").parsed()
            val rest = get("/xrpc/com.atproto.repo.listRecords?repo=${alice.did}&collection=$document&cursor=3kbbb").parsed()
            val newestFirst = get("/xrpc/com.atproto.repo.listRecords?repo=${alice.did}&collection=$document&reverse=true").parsed()

            assertEquals(2, page.getValue("records").jsonArray.size)
            assertEquals("3kbbb", page.getValue("cursor").jsonPrimitive.content)
            assertEquals(1, rest.getValue("records").jsonArray.size)
            assertTrue(
                newestFirst
                    .getValue("records")
                    .jsonArray
                    .first()
                    .jsonObject
                    .getValue("uri")
                    .jsonPrimitive.content
                    .endsWith("3kccc"),
            )
        }

    @Test
    fun `private collections look empty and missing even though the records are in the repository`() =
        testApplication {
            val h = FirehoseHarness().also { it.install(this) }
            val alice = h.createAccount("alice")
            val did = h.did(alice)
            val privateCollections =
                listOf(
                    "studio.hypertext.logdate.content",
                    "studio.hypertext.logdate.journal",
                    "studio.hypertext.logdate.draft",
                    "app.logdate.space.personal",
                    "app.logdate.crypto.sealed",
                    "app.logdate.journal.entry",
                    "app.logdate.place.visit",
                )
            val engine = DefaultRepoEngine(h.rawBlocks)
            privateCollections.forEach { collection ->
                engine
                    .putRecord(
                        RepoRecordId(did, Nsid.require(collection), RecordKey.require("secret-1")),
                        buildJsonObject { put("content", "a secret in $collection") },
                    ).getOrThrow()
            }

            privateCollections.forEach { collection ->
                val read = get("/xrpc/com.atproto.repo.getRecord?repo=${alice.did}&collection=$collection&rkey=secret-1")
                val list = get("/xrpc/com.atproto.repo.listRecords?repo=${alice.did}&collection=$collection")

                assertEquals(HttpStatusCode.BadRequest, read.status, collection)
                assertTrue("RecordNotFound" in read.bodyAsText(), collection)
                assertFalse("secret" in read.bodyAsText(), collection)
                assertEquals(HttpStatusCode.OK, list.status, collection)
                assertEquals(
                    0,
                    list
                        .parsed()
                        .getValue("records")
                        .jsonArray.size,
                    collection,
                )
            }
        }

    @Test
    fun `an account on legacy storage answers every public read with an authentication challenge`() =
        testApplication {
            val h = FirehoseHarness().also { it.install(this) }
            val legacy = h.createAccount("legacy", personalSpace = false)
            DefaultRepoEngine(h.rawBlocks)
                .putRecord(RepoRecordId(h.did(legacy), Nsid.require(profile), RecordKey.require("self")), profileRecord("Legacy"))
                .getOrThrow()
            val did = legacy.did
            val paths =
                listOf(
                    "/xrpc/com.atproto.repo.getRecord?repo=$did&collection=$profile&rkey=self",
                    "/xrpc/com.atproto.repo.listRecords?repo=$did&collection=$profile",
                    "/xrpc/com.atproto.sync.getRepo?did=$did",
                    "/xrpc/com.atproto.sync.getLatestCommit?did=$did",
                    "/xrpc/com.atproto.sync.getRepoStatus?did=$did",
                    "/xrpc/com.atproto.sync.getBlob?did=$did&cid=bafkreigh2akiscaildcqabsyg3dfr6chu3fgpregiymsck7e7aqa4s52zy",
                )

            paths.forEach { path ->
                val response = get(path)
                assertEquals(HttpStatusCode.Unauthorized, response.status, path)
                assertFalse("Legacy" in response.bodyAsText(), path)
            }
        }

    @Test
    fun `the whole repository can be exported and read back with the library`() =
        testApplication {
            val h = FirehoseHarness().also { it.install(this) }
            val alice = h.createAccount("alice")
            h.put(client, alice, profile, "self", profileRecord())
            h.put(client, alice, document, "3kaaa", documentRecord("A story"))

            val response = get("/xrpc/com.atproto.sync.getRepo?did=${alice.did}")

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("application/vnd.ipld.car", response.headers[HttpHeaders.ContentType])
            val export = CarCodec.read(response.bodyAsBytes())
            assertEquals(h.did(alice), export.repo)
            assertEquals(
                h.rawBlocks
                    .readHead(h.did(alice))
                    .getOrThrow()!!
                    .commitCid,
                export.head.commitCid,
            )
        }

    @Test
    fun `a repository holding a private record cannot be exported`() =
        testApplication {
            val h = FirehoseHarness().also { it.install(this) }
            val alice = h.createAccount("alice")
            h.put(client, alice, profile, "self", profileRecord())
            DefaultRepoEngine(h.rawBlocks)
                .putRecord(
                    RepoRecordId(h.did(alice), Nsid.require("studio.hypertext.logdate.content"), RecordKey.require("entry-9")),
                    buildJsonObject { put("content", "diary") },
                ).getOrThrow()

            val response = get("/xrpc/com.atproto.sync.getRepo?did=${alice.did}")

            assertEquals(HttpStatusCode.NotFound, response.status)
            assertFalse("entry-9" in response.bodyAsText())
        }

    @Test
    fun `the latest commit and the status of a public repository are open`() =
        testApplication {
            val h = FirehoseHarness().also { it.install(this) }
            val alice = h.createAccount("alice")
            h.put(client, alice, profile, "self", profileRecord())
            val head = h.rawBlocks.readHead(h.did(alice)).getOrThrow()!!

            val latest = get("/xrpc/com.atproto.sync.getLatestCommit?did=${alice.did}").parsed()
            val status = get("/xrpc/com.atproto.sync.getRepoStatus?did=${alice.did}").parsed()

            assertEquals(head.commitCid.toString(), latest.getValue("cid").jsonPrimitive.content)
            assertEquals(latest.getValue("rev"), status.getValue("rev"))
            assertEquals("true", status.getValue("active").jsonPrimitive.content)
        }

    @Test
    fun `the repositories listing includes only repositories the server shows`() =
        testApplication {
            val h = FirehoseHarness().also { it.install(this) }
            val alice = h.createAccount("alice")
            val bob = h.createAccount("bob")
            val legacy = h.createAccount("legacy", personalSpace = false)
            val empty = h.createAccount("empty")
            h.put(client, alice, profile, "self", profileRecord())
            h.put(client, bob, profile, "self", profileRecord("Bob"))
            DefaultRepoEngine(h.rawBlocks)
                .putRecord(RepoRecordId(h.did(legacy), Nsid.require(profile), RecordKey.require("self")), profileRecord())
                .getOrThrow()

            val listed =
                get("/xrpc/com.atproto.sync.listRepos").parsed().getValue("repos").jsonArray.map {
                    it.jsonObject
                        .getValue("did")
                        .jsonPrimitive.content
                }

            assertEquals(listOf(alice.did, bob.did).sortedBy { it }, listed)
            assertFalse(legacy.did in listed)
            assertFalse(empty.did in listed)
        }

    @Test
    fun `the repositories listing continues from a cursor`() =
        testApplication {
            val h = FirehoseHarness().also { it.install(this) }
            val accounts = listOf("alice", "bob", "carol").map { h.createAccount(it) }
            accounts.forEach { h.put(client, it, profile, "self", profileRecord(it.username)) }
            val sorted = accounts.map { it.did!! }.sorted()

            val first = get("/xrpc/com.atproto.sync.listRepos?limit=2").parsed()
            val cursor = first.getValue("cursor").jsonPrimitive.content
            val second = get("/xrpc/com.atproto.sync.listRepos?limit=2&cursor=$cursor").parsed()

            assertEquals(
                sorted.take(2),
                first.getValue("repos").jsonArray.map {
                    it.jsonObject
                        .getValue("did")
                        .jsonPrimitive.content
                },
            )
            assertEquals(
                sorted.drop(2),
                second.getValue("repos").jsonArray.map {
                    it.jsonObject
                        .getValue("did")
                        .jsonPrimitive.content
                },
            )
        }

    @Test
    fun `a blob is public only while a public record refers to it`() =
        testApplication {
            val h = FirehoseHarness().also { it.install(this) }
            val alice = h.createAccount("alice")
            h.put(client, alice, profile, "self", profileRecord())
            val bytes = "avatar-bytes".encodeToByteArray()
            val upload =
                client.post("/xrpc/com.atproto.repo.uploadBlob") {
                    header(HttpHeaders.Authorization, "Bearer ${h.bearer(alice)}")
                    contentType(ContentType.Image.PNG)
                    setBody(bytes)
                }
            assertEquals(HttpStatusCode.OK, upload.status, upload.bodyAsText())
            val blob = upload.parsed().getValue("blob")
            val cid =
                blob.jsonObject
                    .getValue("ref")
                    .jsonObject
                    .getValue("\$link")
                    .jsonPrimitive.content

            val unreferenced = get("/xrpc/com.atproto.sync.getBlob?did=${alice.did}&cid=$cid")
            h.put(
                client,
                alice,
                profile,
                "self",
                buildJsonObject {
                    put("\$type", profile)
                    put("displayName", "Alice")
                    put("avatar", blob)
                },
            )
            val referenced = get("/xrpc/com.atproto.sync.getBlob?did=${alice.did}&cid=$cid")

            assertEquals(HttpStatusCode.NotFound, unreferenced.status)
            assertEquals(HttpStatusCode.OK, referenced.status)
            assertContentEquals(bytes, referenced.bodyAsBytes())
        }

    @Test
    fun `a blob that belongs to another account is not served from this one`() =
        testApplication {
            val h = FirehoseHarness().also { it.install(this) }
            val alice = h.createAccount("alice")
            val bob = h.createAccount("bob")
            h.put(client, alice, profile, "self", profileRecord())
            h.put(client, bob, profile, "self", profileRecord("Bob"))
            val upload =
                client.post("/xrpc/com.atproto.repo.uploadBlob") {
                    header(HttpHeaders.Authorization, "Bearer ${h.bearer(bob)}")
                    contentType(ContentType.Image.PNG)
                    setBody("bobs-avatar".encodeToByteArray())
                }
            val blob = upload.parsed().getValue("blob")
            val cid =
                blob.jsonObject
                    .getValue("ref")
                    .jsonObject
                    .getValue("\$link")
                    .jsonPrimitive.content
            h.put(
                client,
                alice,
                profile,
                "self",
                buildJsonObject {
                    put("\$type", profile)
                    put("avatar", blob)
                },
            )

            val response = get("/xrpc/com.atproto.sync.getBlob?did=${alice.did}&cid=$cid")

            assertEquals(HttpStatusCode.NotFound, response.status)
        }

    @Test
    fun `the owner can write public records and others cannot`() =
        testApplication {
            val h = FirehoseHarness().also { it.install(this) }
            val alice = h.createAccount("alice")
            val bob = h.createAccount("bob")

            val own = h.put(client, alice, profile, "self", profileRecord())
            val foreign = h.put(client, alice, profile, "self", profileRecord("Bob as Alice"), bearerOf = bob)
            val anonymous =
                client.post("/xrpc/com.atproto.repo.putRecord") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"repo":"${alice.did}","collection":"$profile","rkey":"self","record":{"${'$'}type":"$profile"}}""")
                }

            assertEquals(HttpStatusCode.OK, own.status)
            assertEquals(HttpStatusCode.Forbidden, foreign.status)
            assertEquals(HttpStatusCode.Unauthorized, anonymous.status)
        }

    @Test
    fun `an account on legacy storage cannot write public records`() =
        testApplication {
            val h = FirehoseHarness().also { it.install(this) }
            val legacy = h.createAccount("legacy", personalSpace = false)

            val response = h.put(client, legacy, profile, "self", profileRecord())

            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertTrue("personal space" in response.bodyAsText(), response.bodyAsText())
        }

    @Test
    fun `an account in a personal space cannot put journal records into its public repository`() =
        testApplication {
            val h = FirehoseHarness().also { it.install(this) }
            val alice = h.createAccount("alice")
            val legacy = h.createAccount("legacy", personalSpace = false)
            val entry =
                buildJsonObject {
                    put("\$type", "studio.hypertext.logdate.content")
                    put("type", "TEXT")
                    put("content", "a private note")
                    put("createdAt", 1)
                    put("lastUpdated", 1)
                }

            val refused = h.put(client, alice, "studio.hypertext.logdate.content", "entry-1", entry)
            val allowed = h.put(client, legacy, "studio.hypertext.logdate.content", "entry-1", entry)

            assertEquals(HttpStatusCode.BadRequest, refused.status)
            assertTrue("personal space" in refused.bodyAsText(), refused.bodyAsText())
            assertEquals(HttpStatusCode.OK, allowed.status, allowed.bodyAsText())
            assertTrue(h.eventStore.read(0, 10).isEmpty())
        }

    @Test
    fun `a profile record must use the key self and carry its own type`() =
        testApplication {
            val h = FirehoseHarness().also { it.install(this) }
            val alice = h.createAccount("alice")

            val wrongKey = h.put(client, alice, profile, "3kabc", profileRecord())
            val wrongType = h.put(client, alice, profile, "self", buildJsonObject { put("\$type", document) })

            assertEquals(HttpStatusCode.BadRequest, wrongKey.status)
            assertEquals(HttpStatusCode.BadRequest, wrongType.status)
        }

    @Test
    fun `creating a public record without a key gives it a TID key and deleting removes it`() =
        testApplication {
            val h = FirehoseHarness().also { it.install(this) }
            val alice = h.createAccount("alice")

            val created =
                client.post("/xrpc/com.atproto.repo.createRecord") {
                    header(HttpHeaders.Authorization, "Bearer ${h.bearer(alice)}")
                    contentType(ContentType.Application.Json)
                    setBody("""{"repo":"${alice.did}","collection":"$document","record":${documentRecord("Story")}}""")
                }
            val uri =
                created
                    .parsed()
                    .getValue("uri")
                    .jsonPrimitive.content
            val key = uri.substringAfterLast('/')
            val deleted =
                client.post("/xrpc/com.atproto.repo.deleteRecord") {
                    header(HttpHeaders.Authorization, "Bearer ${h.bearer(alice)}")
                    contentType(ContentType.Application.Json)
                    setBody("""{"repo":"${alice.did}","collection":"$document","rkey":"$key"}""")
                }

            assertEquals(HttpStatusCode.OK, created.status, created.bodyAsText())
            assertTrue(
                studio.hypertext.atproto.syntax.Tid
                    .isValid(key),
                key,
            )
            assertEquals(HttpStatusCode.OK, deleted.status, deleted.bodyAsText())
            assertEquals(
                HttpStatusCode.BadRequest,
                get("/xrpc/com.atproto.repo.getRecord?repo=${alice.did}&collection=$document&rkey=$key").status,
            )
        }
}
