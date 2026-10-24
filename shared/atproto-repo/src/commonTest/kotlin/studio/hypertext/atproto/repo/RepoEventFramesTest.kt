package studio.hypertext.atproto.repo

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import studio.hypertext.atproto.identity.AtprotoDid
import studio.hypertext.atproto.syntax.Nsid
import studio.hypertext.atproto.syntax.RecordKey
import studio.hypertext.atproto.syntax.Tid
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.time.Instant

/**
 * The frames of `com.atproto.sync.subscribeRepos` are two DAG-CBOR values back to back, so these
 * tests write each kind of message and read it again, and check the header a relay looks at first.
 */
class RepoEventFramesTest {
    private val did = AtprotoDid.require("did:plc:ewvi7nxzyoun6zhxrhs64oiz")
    private val time = Instant.parse("2026-10-23T12:30:45.123Z")
    private val commit = Cid.sha256(DAG_CBOR_CODEC, byteArrayOf(1, 2, 3))
    private val record = Cid.sha256(DAG_CBOR_CODEC, byteArrayOf(4, 5, 6))
    private val previousRecord = Cid.sha256(DAG_CBOR_CODEC, byteArrayOf(7, 8, 9))

    @Test
    fun `a commit event survives a round trip with every field`() {
        val event =
            RepoCommitEvent(
                seq = 42,
                time = time,
                repo = did,
                commit = commit,
                rev = Tid.fromLong(1_000_000L),
                since = Tid.fromLong(999_999L),
                blocks = byteArrayOf(9, 8, 7),
                ops =
                    listOf(
                        RepoOperation(RepoOperationAction.CREATE, collection("app.logdate.actor.profile"), key("self"), record, null),
                        RepoOperation(
                            RepoOperationAction.UPDATE,
                            collection("site.standard.document"),
                            key("3kabc"),
                            record,
                            previousRecord,
                        ),
                        RepoOperation(RepoOperationAction.DELETE, collection("site.standard.document"), key("3kdef"), null, previousRecord),
                    ),
                prevData = record,
            )

        val decoded = assertIs<RepoCommitEvent>(RepoEventFrames.decode(RepoEventFrames.encode(event)))

        assertEquals(event, decoded)
    }

    @Test
    fun `the first commit of a repository has no since and no previous tree`() {
        val event =
            RepoCommitEvent(
                seq = 1,
                time = time,
                repo = did,
                commit = commit,
                rev = Tid.fromLong(5L),
                since = null,
                blocks = byteArrayOf(),
                ops = emptyList(),
                prevData = null,
            )

        val decoded = assertIs<RepoCommitEvent>(RepoEventFrames.decode(RepoEventFrames.encode(event)))

        assertNull(decoded.since)
        assertNull(decoded.prevData)
    }

    @Test
    fun `sync identity and account events survive a round trip`() {
        val sync = RepoSyncEvent(seq = 2, time = time, did = did, rev = Tid.fromLong(7L), blocks = byteArrayOf(1))
        val identity = RepoIdentityEvent(seq = 3, time = time, did = did, handle = "alice.logdate.app")
        val bareIdentity = RepoIdentityEvent(seq = 4, time = time, did = did, handle = null)
        val account = RepoAccountEvent(seq = 5, time = time, did = did, active = false, status = "deleted")
        val active = RepoAccountEvent(seq = 6, time = time, did = did, active = true, status = null)

        assertEquals(sync, RepoEventFrames.decode(RepoEventFrames.encode(sync)))
        assertEquals(identity, RepoEventFrames.decode(RepoEventFrames.encode(identity)))
        assertEquals(bareIdentity, RepoEventFrames.decode(RepoEventFrames.encode(bareIdentity)))
        assertEquals(account, RepoEventFrames.decode(RepoEventFrames.encode(account)))
        assertEquals(active, RepoEventFrames.decode(RepoEventFrames.encode(active)))
    }

    @Test
    fun `info and error messages survive a round trip`() {
        val info = RepoInfoMessage(name = "OutdatedCursor", message = "The cursor is older than the oldest event")
        val error = RepoErrorMessage(error = "FutureCursor", message = "Cursor in the future")

        assertEquals(info, RepoEventFrames.decode(RepoEventFrames.encode(info)))
        assertEquals(error, RepoEventFrames.decode(RepoEventFrames.encode(error)))
    }

    @Test
    fun `an event header carries op 1 and its type while an error header carries op minus 1`() {
        val eventHeader = DagCborCodec.decodeSequence(RepoEventFrames.encode(RepoIdentityEvent(1, time, did, null))).first().jsonObject
        val errorHeader = DagCborCodec.decodeSequence(RepoEventFrames.encode(RepoErrorMessage("FutureCursor", null))).first().jsonObject

        assertEquals(mapOf("op" to "1", "t" to "#identity"), eventHeader.mapValues { it.value.jsonPrimitive.content })
        assertEquals(mapOf("op" to "-1"), errorHeader.mapValues { it.value.jsonPrimitive.content })
    }

    @Test
    fun `a frame that is not a header and a body is refused`() {
        assertFailsWith<IllegalArgumentException> { RepoEventFrames.decode(DagCborCodec.encode(JsonPrimitive(1))) }
    }

    private fun collection(value: String) = Nsid.require(value)

    private fun key(value: String) = RecordKey.require(value)
}
