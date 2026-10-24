package app.logdate.server.atproto.firehose

import app.logdate.server.auth.Account
import app.logdate.server.logdate.LogDateBlobNamespace
import app.logdate.server.logdate.LogDateBlobWriteRequest
import app.logdate.server.space.InMemorySpaceStore
import app.logdate.server.space.SpaceBlob
import app.logdate.server.space.SpaceRecordWrite
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsBytes
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.ktor.websocket.Frame
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import studio.hypertext.atproto.repo.DefaultRepoEngine
import studio.hypertext.atproto.repo.RepoEvent
import studio.hypertext.atproto.repo.RepoRecordId
import studio.hypertext.atproto.syntax.Nsid
import studio.hypertext.atproto.syntax.RecordKey
import java.io.File
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.toJavaUuid

/**
 * Proof that nothing from a personal or shared space, a sealed record, or a `studio.hypertext.logdate`
 * collection reaches the stream or the public repository methods.
 *
 * Every private record in these tests carries a marker that appears nowhere else. Each test then
 * collects everything the server will say to a stranger, which is every public method for every
 * private collection plus the whole event stream from its start, and checks the bytes for the markers.
 * A marker found in a response would also show up in a leak of the record's key or its collection name,
 * not only its content.
 */
@OptIn(ExperimentalUuidApi::class, ExperimentalEncodingApi::class)
class PublicRepoPrivacyTest {
    private val profile = Nsid.require("app.logdate.actor.profile")
    private val document = Nsid.require("site.standard.document")

    private val privateCollections =
        listOf(
            "studio.hypertext.logdate.content",
            "studio.hypertext.logdate.journal",
            "studio.hypertext.logdate.association",
            "studio.hypertext.logdate.draft",
            "studio.hypertext.logdate.moment",
            "studio.hypertext.logdate.entry",
            "app.logdate.space.personal",
            "app.logdate.space.circle",
            "app.logdate.crypto.sealed",
            "app.logdate.journal.entry",
            "app.logdate.people.person",
            "app.logdate.place.sample",
        )

    private val markers =
        listOf(
            "MARKERKEY",
            "MARKERTEXT",
            "MARKERPERSONALREC",
            "MARKERCIRCLEREC",
            "MARKERCIRCLEKEY",
            "bmarkerspaceblobcid",
            PRIVATE_EPOCH_MILLIS.toString(),
            PRIVATE_ISO_TIME,
        ) + privateCollections

    private fun publicProfile(): JsonObject =
        buildJsonObject {
            put("\$type", profile.toString())
            put("displayName", "Alice")
        }

    private fun publicDocument(): JsonObject =
        buildJsonObject {
            put("\$type", document.toString())
            put("title", "A story I chose to publish")
            put("site", "https://lena.logdate.app")
            put("publishedAt", "2026-10-23T12:00:00.000Z")
        }

    /** Puts the markers where a careless implementation might find them: records of private collections in the repository tree. */
    private suspend fun FirehoseHarness.seedPrivateData(
        account: Account,
        inRepository: Boolean,
    ) {
        if (inRepository) {
            val engine = DefaultRepoEngine(blocks)
            privateCollections.forEach { collection ->
                engine
                    .putRecord(
                        RepoRecordId(
                            did(account),
                            Nsid.require(collection),
                            RecordKey.require("MARKERKEY-${collection.substringAfterLast('.')}"),
                        ),
                        buildJsonObject {
                            put("text", "MARKERTEXT in $collection")
                            put("createdAt", PRIVATE_EPOCH_MILLIS)
                            put("lastUpdated", PRIVATE_EPOCH_MILLIS)
                            put("capturedAt", PRIVATE_ISO_TIME)
                        },
                    ).getOrThrow()
            }
        }
    }

    private fun ApplicationTestBuilder.socketClient(): HttpClient = createClient { install(WebSockets) }

    /** Everything a stranger can ask the server about [account], as raw bytes. */
    private suspend fun ApplicationTestBuilder.whatAStrangerCanSee(
        account: Account,
        extraPaths: List<String> = emptyList(),
    ): List<ByteArray> {
        val did = account.did
        val paths =
            buildList {
                privateCollections.forEach { collection ->
                    add(
                        "/xrpc/com.atproto.repo.getRecord?repo=$did&collection=$collection&rkey=MARKERKEY-${collection.substringAfterLast(
                            '.',
                        )}",
                    )
                    add("/xrpc/com.atproto.repo.listRecords?repo=$did&collection=$collection")
                    add("/xrpc/com.atproto.repo.listRecords?repo=${account.handle}&collection=$collection&reverse=true")
                }
                add("/xrpc/com.atproto.sync.getRepo?did=$did")
                add("/xrpc/com.atproto.sync.getRepo?did=$did&since=2222222222222")
                add("/xrpc/com.atproto.sync.getLatestCommit?did=$did")
                add("/xrpc/com.atproto.sync.getRepoStatus?did=$did")
                add("/xrpc/com.atproto.sync.listRepos")
                add("/xrpc/com.atproto.repo.describeRepo?repo=$did")
                addAll(extraPaths)
            }
        val bodies = paths.map { client.get(it).bodyAsBytes() }.toMutableList()
        socketClient().webSocket("/xrpc/com.atproto.sync.subscribeRepos?cursor=0") {
            while (true) {
                val frame = withTimeoutOrNull(400.milliseconds) { incoming.receive() } ?: break
                bodies += (frame as Frame.Binary).data
            }
        }
        return bodies
    }

    private fun assertNoMarker(bodies: List<ByteArray>) {
        bodies.forEach { body ->
            val text = body.decodeToString()
            markers.forEach { marker ->
                assertTrue(marker !in text, "the marker $marker appeared in a response or event")
            }
        }
    }

    @Test
    fun `a repository that mixes private records with public ones shows nothing to strangers`() =
        testApplication {
            val h = FirehoseHarness().also { it.install(this) }
            val alice = h.createAccount("alice")
            h.seedPrivateData(alice, inRepository = true)
            h.put(client, alice, profile.toString(), "self", publicProfile())

            val seen = whatAStrangerCanSee(alice)

            assertNoMarker(seen)
            assertEquals(0, h.eventStore.read(0, 100).size, "no event is stored for a repository that holds private records")
        }

    @Test
    fun `a public repository streams its public records and none of the private data kept beside it`() =
        testApplication {
            val h = FirehoseHarness().also { it.install(this) }
            val alice = h.createAccount("alice")
            h.seedPrivateData(alice, inRepository = false)
            h.put(client, alice, profile.toString(), "self", publicProfile())
            h.put(client, alice, document.toString(), "3kaaa", publicDocument())

            val seen = whatAStrangerCanSee(alice)

            assertNoMarker(seen)
            val streamed = h.eventStore.read(0, 100)
            assertEquals(2, streamed.size, "the two public commits are announced")
            assertTrue(seen.any { "A story I chose to publish" in it.decodeToString() }, "the public story is visible")
        }

    @Test
    fun `private records written after a repository went public silence the stream instead of joining it`() =
        testApplication {
            val h = FirehoseHarness().also { it.install(this) }
            val alice = h.createAccount("alice")
            h.put(client, alice, profile.toString(), "self", publicProfile())
            val announcedBefore = h.eventStore.read(0, 100).size
            h.seedPrivateData(alice, inRepository = true)
            h.put(client, alice, document.toString(), "3kaaa", publicDocument())

            val seen = whatAStrangerCanSee(alice)

            assertNoMarker(seen)
            assertEquals(announcedBefore, h.eventStore.read(0, 100).size, "nothing is announced once the tree holds a private record")
        }

    @Test
    fun `an account on legacy storage shows and streams nothing at all`() =
        testApplication {
            val h = FirehoseHarness().also { it.install(this) }
            val legacy = h.createAccount("legacy", personalSpace = false)
            h.seedPrivateData(legacy, inRepository = true)
            DefaultRepoEngine(h.blocks)
                .putRecord(RepoRecordId(h.did(legacy), profile, RecordKey.require("self")), publicProfile())
                .getOrThrow()

            val seen = whatAStrangerCanSee(legacy)

            assertNoMarker(seen)
            assertTrue(h.eventStore.read(0, 100).isEmpty())
            assertTrue(seen.none { "Alice" in it.decodeToString() }, "even the public-looking profile of a legacy account stays closed")
        }

    @Test
    fun `personal and circle space records and blobs never reach a stranger or a relay`() =
        testApplication {
            val clock = FixedClock(Instant.parse("2030-01-01T00:00:00Z"))
            val h = FirehoseHarness(clock = clock).also { it.install(this) }
            val alice = h.createAccount("alice")
            val spaces = InMemorySpaceStore()
            val owner = alice.id.toJavaUuid()
            val personal = "at://${alice.did}/space/app.logdate.space.personal/self"
            val circle = "at://${alice.did}/space/app.logdate.space.circle/MARKERCIRCLEKEY"
            spaces.put(SpaceRecordWrite(owner, personal, alice.did!!, "MARKERPERSONALREC", sealedEnvelope(), emptyList(), null, false))
            spaces.put(SpaceRecordWrite(owner, circle, alice.did!!, "MARKERCIRCLEREC", sealedEnvelope(), emptyList(), null, false))
            val blobPath =
                h.blobStorage.putBlob(
                    LogDateBlobWriteRequest(
                        owner,
                        LogDateBlobNamespace.SPACE,
                        SPACE_BLOB_CID,
                        null,
                        "application/octet-stream",
                        byteArrayOf(1),
                    ),
                )
            spaces.putBlob(owner, personal, SpaceBlob(SPACE_BLOB_CID, "application/octet-stream", 1, blobPath))
            h.seedPrivateData(alice, inRepository = false)
            h.put(client, alice, profile.toString(), "self", publicProfile())
            h.put(client, alice, document.toString(), "3kaaa", publicDocument())
            h.announcer.identityChanged(alice)

            val seen =
                whatAStrangerCanSee(
                    alice,
                    extraPaths =
                        listOf(
                            "/xrpc/com.atproto.sync.getBlob?did=${alice.did}&cid=$SPACE_BLOB_CID",
                            "/xrpc/com.atproto.repo.getRecord?repo=${alice.did}&collection=app.logdate.space.circle&rkey=MARKERCIRCLEREC",
                            "/xrpc/com.atproto.repo.getRecord?repo=${alice.did}&collection=app.logdate.space.personal&rkey=MARKERPERSONALREC",
                        ),
                )

            assertNoMarker(seen)
            assertNoMarker(h.crawlRequests.map { it.encodeToByteArray() })
            assertTrue(seen.any { "A story I chose to publish" in it.decodeToString() }, "the public story is announced")
            assertEquals(1, spaces.changes(personal, alice.did!!, 0, 10).changes.size, "the space still holds its record")
        }

    @Test
    fun `relays are asked to crawl with the server hostname and nothing about any account`() =
        testApplication {
            val h = FirehoseHarness().also { it.install(this) }
            val alice = h.createAccount("alice")
            h.seedPrivateData(alice, inRepository = true)
            h.put(client, alice, profile.toString(), "self", publicProfile())

            h.announcer.identityChanged(alice)
            h.crawler.requestCrawl()

            assertTrue(h.crawlRequests.isNotEmpty())
            h.crawlRequests.forEach { request ->
                assertEquals("https://relay.example.com/xrpc/com.atproto.sync.requestCrawl {\"hostname\":\"logdate.app\"}", request)
            }
        }

    @Test
    fun `event times are the moment of announcement and never a time from a record`() =
        testApplication {
            val announcedAt = Instant.parse("2030-01-01T00:00:00Z")
            val h = FirehoseHarness(clock = FixedClock(announcedAt)).also { it.install(this) }
            val alice = h.createAccount("alice")
            h.put(client, alice, profile.toString(), "self", publicProfile())
            h.announcer.identityChanged(alice)

            val events = h.eventStore.read(0, 100).map { it.decode() }

            assertTrue(events.size >= 2)
            events.forEach { event -> assertEquals(announcedAt, (event as RepoEvent).time) }
        }

    @Test
    fun `the public code never reaches the stores that hold spaces`() {
        val publicSources =
            listOf(
                "src/main/kotlin/app/logdate/server/atproto/firehose",
                "src/main/kotlin/app/logdate/server/routes/PublicRepoReads.kt",
                "src/main/kotlin/app/logdate/server/routes/RepoEventStreamRoutes.kt",
            ).flatMap { path -> File(path).walkTopDown().filter { it.isFile && it.extension == "kt" }.toList() }

        assertTrue(publicSources.size > 10, "the public sources were not found from ${File(".").absolutePath}")
        publicSources.forEach { file ->
            val text = file.readText()
            assertTrue("app.logdate.server.space" !in text, "${file.name} must not use the space store")
            assertTrue("LogDateCollectionsRepository" !in text, "${file.name} must not use the journal adapters")
        }
    }

    @Test
    fun `no collection a person keeps in LogDate is public unless it is named in the policy`() {
        val policy = PublicRepoPolicy()

        privateCollections.forEach { collection ->
            assertTrue(!policy.isPublic(Nsid.require(collection)), "$collection must stay private")
        }
        assertTrue(policy.isPublic(profile))
        assertTrue(policy.isPublic(document))
    }

    private fun sealedEnvelope(): JsonObject =
        buildJsonObject {
            put("\$type", "app.logdate.crypto.sealed")
            put("version", 1)
            put("keyId", "abcdefghijklm")
            putJsonObject("nonce") { put("\$bytes", Base64.encode(ByteArray(12))) }
            putJsonObject("ciphertext") { put("\$bytes", Base64.encode(ByteArray(528))) }
        }

    private class FixedClock(
        private val instant: Instant,
    ) : Clock {
        override fun now(): Instant = instant
    }

    private companion object {
        const val PRIVATE_EPOCH_MILLIS = 1771111111111L
        const val PRIVATE_ISO_TIME = "2031-04-05T06:07:08.000Z"
        const val SPACE_BLOB_CID = "bmarkerspaceblobcid"
    }
}
