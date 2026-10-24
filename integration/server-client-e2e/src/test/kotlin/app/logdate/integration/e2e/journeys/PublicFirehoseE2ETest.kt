package app.logdate.integration.e2e.journeys

import app.logdate.integration.e2e.fixtures.createAccountWithSyntheticPasskey
import app.logdate.integration.e2e.harness.ServerClientE2EHarness
import app.logdate.integration.e2e.harness.withServerClientHarness
import app.logdate.lexicon.app.logdate.crypto.SealedPayload
import app.logdate.space.RecordSwap
import app.logdate.space.SealedRecordCodec
import app.logdate.space.SealingKeys
import app.logdate.space.SpaceRef
import app.logdate.space.XrpcDataHome
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.websocket.ClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
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
import io.ktor.websocket.Frame
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import studio.hypertext.atproto.repo.CarCodec
import studio.hypertext.atproto.repo.DagCborCodec
import studio.hypertext.atproto.repo.RepoCommitEvent
import studio.hypertext.atproto.repo.RepoEventFrames
import studio.hypertext.atproto.repo.RepoOperationAction
import studio.hypertext.atproto.repo.RepoStreamMessage
import studio.hypertext.atproto.xrpc.BearerToken
import studio.hypertext.atproto.xrpc.KtorXrpcClient
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * A relay's view of a LogDate server with the repository event stream turned on: it subscribes, an
 * account publishes a record, and the commit arrives as a frame the AT Protocol library decodes.
 * Runs with personal spaces, `did:web` identities and `ATPROTO_FIREHOSE_ENABLED` set by the
 * `publicFirehoseTest` Gradle task, because accounts only count as public in a personal space.
 */
class PublicFirehoseE2ETest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `a follower receives the commit for a record created after it subscribed and decodes it`() =
        runBlocking<Unit> {
            withServerClientHarness {
                val account = signUp()
                withFollower(cursor = 0) {
                    val written = putProfile(account, "Lake Day Lena")

                    val event = nextCommitFor(account)

                    assertEquals(account.did, event.repo.toString())
                    assertTrue(event.seq > 0)
                    val operation = event.ops.single()
                    assertEquals(RepoOperationAction.CREATE, operation.action)
                    assertEquals("app.logdate.actor.profile/self", operation.path)
                    assertEquals(written.getValue("cid").jsonPrimitive.content, operation.cid.toString())
                    assertNull(event.since)
                    assertNull(event.prevData)

                    val car = CarCodec.readCar(event.blocks)
                    assertEquals(listOf(event.commit), car.roots)
                    val record = car.blocks.single { it.cid == operation.cid }
                    val value = DagCborCodec.decode(record.bytes).jsonObject
                    assertEquals("Lake Day Lena", value.getValue("displayName").jsonPrimitive.content)
                    val export = CarCodec.read(event.blocks)
                    assertEquals(event.rev.toLong(), export.head.revision)
                    assertEquals(event.commit, export.head.commitCid)
                }
            }
        }

    @Test
    fun `a relay that connects later catches up from cursor 0 and then follows live`() =
        runBlocking<Unit> {
            withServerClientHarness {
                val account = signUp()
                putProfile(account, "First name")

                withFollower(cursor = 0) {
                    val backfilled = nextCommitFor(account)
                    putProfile(account, "Second name")
                    val live = nextCommitFor(account)

                    assertTrue(live.seq > backfilled.seq)
                    assertEquals(backfilled.rev, live.since)
                    assertEquals(RepoOperationAction.UPDATE, live.ops.single().action)
                    assertEquals(backfilled.ops.single().cid, live.ops.single().prev)
                }
            }
        }

    @Test
    fun `a relay can read the same record and repository the stream announced`() =
        runBlocking<Unit> {
            withServerClientHarness {
                val account = signUp()
                putProfile(account, "Open to everyone")
                val origin = origin()

                val record = anonymous.get("$origin/xrpc/com.atproto.repo.getRecord?repo=${account.did}&collection=$PROFILE&rkey=self")
                val list = anonymous.get("$origin/xrpc/com.atproto.repo.listRecords?repo=${account.did}&collection=$PROFILE")
                val latest = anonymous.get("$origin/xrpc/com.atproto.sync.getLatestCommit?did=${account.did}")
                val status = anonymous.get("$origin/xrpc/com.atproto.sync.getRepoStatus?did=${account.did}")
                val repos = anonymous.get("$origin/xrpc/com.atproto.sync.listRepos")
                val export = anonymous.get("$origin/xrpc/com.atproto.sync.getRepo?did=${account.did}")

                listOf(record, list, latest, status, repos, export).forEach { assertEquals(HttpStatusCode.OK, it.status, it.bodyAsText()) }
                assertTrue("Open to everyone" in record.bodyAsText())
                assertTrue(account.did in repos.bodyAsText())
                val exported = CarCodec.read(export.bodyAsBytes())
                assertEquals(account.did, exported.repo.toString())
                assertEquals(
                    latest
                        .json()
                        .getValue("cid")
                        .jsonPrimitive.content,
                    exported.head.commitCid.toString(),
                )
                withFollower(cursor = 0) {
                    assertEquals(exported.head.commitCid, nextCommitFor(account).commit)
                }
            }
        }

    @Test
    fun `a record that does not follow its lexicon is refused and never announced`() =
        runBlocking<Unit> {
            withServerClientHarness {
                val account = signUp()

                val refused =
                    putRecord(
                        account,
                        PROFILE,
                        "self",
                        buildJsonObject {
                            put("\$type", PROFILE)
                            put("displayName", "x".repeat(65))
                        },
                    )

                assertEquals(HttpStatusCode.BadRequest, refused.status, refused.bodyAsText())
                assertTrue("displayName: longer than 64 graphemes" in refused.bodyAsText(), refused.bodyAsText())
                val read = anonymous.get("${origin()}/xrpc/com.atproto.repo.getRecord?repo=${account.did}&collection=$PROFILE&rkey=self")
                assertTrue(read.status != HttpStatusCode.OK, "a refused record must not be readable")
                withFollower(cursor = 0) {
                    val frames = drain().map { RepoEventFrames.decode(it) }
                    assertTrue(frames.none { it is RepoCommitEvent && it.repo.toString() == account.did })
                }
            }
        }

    @Test
    fun `nothing from the personal space or the journal reaches the stream or the public methods`() =
        runBlocking<Unit> {
            withServerClientHarness {
                val account = signUp()
                val space = SpaceRef.personal(account.did)
                val sealed = SealedRecordCodec.seal(SealingKeys.personal(IDENTITY_KEY), space, account.did, privateEntry())
                account.home.putRecord(space, sealed.rkey, sealed.record, swap = RecordSwap.Absent)
                putProfile(account, "Public name")
                val journalAttempt = putRecord(account, JOURNAL_COLLECTION, "entry-1", journalRecord())
                val origin = origin()

                val seen = mutableListOf<ByteArray>()
                listOf(
                    PROFILE,
                    JOURNAL_COLLECTION,
                    SPACE_ENTRY_COLLECTION,
                    "app.logdate.crypto.sealed",
                    "studio.hypertext.logdate.draft",
                ).forEach { collection ->
                    seen +=
                        anonymous.get("$origin/xrpc/com.atproto.repo.listRecords?repo=${account.did}&collection=$collection").bodyAsBytes()
                    seen +=
                        anonymous
                            .get(
                                "$origin/xrpc/com.atproto.repo.getRecord?repo=${account.did}&collection=$collection&rkey=${sealed.rkey}",
                            ).bodyAsBytes()
                    seen +=
                        anonymous
                            .get(
                                "$origin/xrpc/com.atproto.repo.getRecord?repo=${account.did}&collection=$collection&rkey=entry-1",
                            ).bodyAsBytes()
                }
                seen += anonymous.get("$origin/xrpc/com.atproto.sync.getRepo?did=${account.did}").bodyAsBytes()
                seen += anonymous.get("$origin/xrpc/com.atproto.sync.listRepos").bodyAsBytes()
                seen += anonymous.get("$origin/xrpc/com.atproto.sync.getLatestCommit?did=${account.did}").bodyAsBytes()
                withFollower(cursor = 0) { seen += drain() }

                assertEquals(
                    HttpStatusCode.BadRequest,
                    journalAttempt.status,
                    "journal records belong in the personal space: ${journalAttempt.bodyAsText()}",
                )
                val text = seen.joinToString("\n") { it.decodeToString() }
                val secrets = listOf(sealed.rkey, PRIVATE_TEXT, INNER_RKEY, SPACE_ENTRY_COLLECTION, JOURNAL_COLLECTION, DIARY_LINE)
                secrets.forEach { secret -> assertFalse(secret in text, "$secret reached a stranger") }
                assertTrue("Public name" in text, "the public profile is still announced")
            }
        }

    private class Account(
        val did: String,
        val token: String,
        val home: XrpcDataHome,
    )

    private val anonymous: HttpClient by lazy { HttpClient(OkHttp) }

    private val socketClient: HttpClient by lazy { HttpClient(OkHttp) { install(WebSockets) } }

    private suspend fun ServerClientE2EHarness.signUp(): Account {
        val created = apiClient.createAccountWithSyntheticPasskey("fire_${Random.nextInt(100000, 999999)}")
        val did = requireNotNull(created.data.account.did) { "The new account has no DID" }
        val token = created.data.tokens.accessToken
        val origin = origin()
        return Account(did, token, XrpcDataHome(KtorXrpcClient(httpClient, origin), httpClient, origin) { BearerToken(token) })
    }

    private fun ServerClientE2EHarness.origin(): String = baseUrl.removeSuffix("/api/v1")

    private suspend fun ServerClientE2EHarness.putProfile(
        account: Account,
        name: String,
    ) = putRecord(
        account,
        PROFILE,
        "self",
        buildJsonObject {
            put("\$type", PROFILE)
            put("displayName", name)
        },
    ).let { response ->
        assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
        response.json()
    }

    private suspend fun ServerClientE2EHarness.putRecord(
        account: Account,
        collection: String,
        rkey: String,
        record: kotlinx.serialization.json.JsonObject,
    ): HttpResponse =
        httpClient.post("${origin()}/xrpc/com.atproto.repo.putRecord") {
            header(HttpHeaders.Authorization, "Bearer ${account.token}")
            contentType(ContentType.Application.Json)
            setBody(
                buildJsonObject {
                    put("repo", account.did)
                    put("collection", collection)
                    put("rkey", rkey)
                    put("record", record)
                }.toString(),
            )
        }

    private suspend fun HttpResponse.json() = json.parseToJsonElement(bodyAsText()).jsonObject

    private suspend fun ServerClientE2EHarness.withFollower(
        cursor: Long,
        block: suspend ClientWebSocketSession.() -> Unit,
    ) {
        val url = origin().replaceFirst("http", "ws") + "/xrpc/com.atproto.sync.subscribeRepos?cursor=$cursor"
        socketClient.webSocket(url) { block() }
    }

    private suspend fun ClientWebSocketSession.next(): RepoStreamMessage =
        withTimeout(15.seconds) { RepoEventFrames.decode((incoming.receive() as Frame.Binary).data) }

    /** The next commit event for [account], skipping the identity and status events around it. */
    private suspend fun ClientWebSocketSession.nextCommitFor(account: Account): RepoCommitEvent {
        while (true) {
            val message = next()
            if (message is RepoCommitEvent && message.repo.toString() == account.did) return assertIs<RepoCommitEvent>(message)
        }
    }

    /** Everything the stream has to say, until it has been quiet for a moment. */
    private suspend fun ClientWebSocketSession.drain(): List<ByteArray> {
        val frames = mutableListOf<ByteArray>()
        while (true) {
            val frame = withTimeoutOrNull(600.milliseconds) { incoming.receive() } ?: return frames
            frames += (frame as Frame.Binary).data
        }
    }

    private fun privateEntry() =
        SealedPayload(
            collection = SPACE_ENTRY_COLLECTION,
            record =
                buildJsonObject {
                    put("\$type", SPACE_ENTRY_COLLECTION)
                    put("text", PRIVATE_TEXT)
                },
            rkey = INNER_RKEY,
        )

    private fun journalRecord() =
        buildJsonObject {
            put("\$type", JOURNAL_COLLECTION)
            put("type", "TEXT")
            put("content", DIARY_LINE)
            put("createdAt", 1)
            put("lastUpdated", 1)
        }

    private companion object {
        const val PROFILE = "app.logdate.actor.profile"
        const val JOURNAL_COLLECTION = "studio.hypertext.logdate.content"
        const val SPACE_ENTRY_COLLECTION = "app.logdate.journal.entry"
        const val PRIVATE_TEXT = "Lake day with the private sketchbook"
        const val DIARY_LINE = "A private diary line"
        const val INNER_RKEY = "3k2aqx7vd4m5n"
        val IDENTITY_KEY = ByteArray(32) { it.toByte() }
    }
}
