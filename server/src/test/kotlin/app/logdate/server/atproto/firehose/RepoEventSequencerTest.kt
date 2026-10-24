package app.logdate.server.atproto.firehose

import app.logdate.shared.model.AccountStorageMode
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import studio.hypertext.atproto.repo.BatchRecordWrite
import studio.hypertext.atproto.repo.CarCodec
import studio.hypertext.atproto.repo.DefaultRepoEngine
import studio.hypertext.atproto.repo.RepoAccountEvent
import studio.hypertext.atproto.repo.RepoCommitEvent
import studio.hypertext.atproto.repo.RepoIdentityEvent
import studio.hypertext.atproto.repo.RepoOperationAction
import studio.hypertext.atproto.repo.RepoRecordId
import studio.hypertext.atproto.repo.RepoStreamMessage
import studio.hypertext.atproto.repo.RepoSyncEvent
import studio.hypertext.atproto.syntax.Nsid
import studio.hypertext.atproto.syntax.RecordKey
import studio.hypertext.atproto.syntax.Tid
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi

/**
 * What the sequencer announces for a repository, and what it holds back. Writes go through the real
 * sequencing block store, so these tests also show that an ordinary repository write is what
 * produces an event.
 */
@OptIn(ExperimentalUuidApi::class)
class RepoEventSequencerTest {
    private val profile = PublicRepoPolicy.PROFILE
    private val document = PublicRepoPolicy.DOCUMENT

    private fun profileValue(name: String): JsonObject =
        buildJsonObject {
            put("\$type", profile.toString())
            put("displayName", name)
        }

    private fun documentValue(title: String): JsonObject =
        buildJsonObject {
            put("\$type", document.toString())
            put("title", title)
        }

    private suspend fun FirehoseHarness.events(): List<RepoStreamMessage> = eventStore.read(0, 1000).map { it.decode() }

    private suspend fun FirehoseHarness.write(
        did: studio.hypertext.atproto.identity.AtprotoDid,
        collection: Nsid,
        key: String,
        value: JsonObject,
    ) {
        DefaultRepoEngine(blocks).putRecord(RepoRecordId(did, collection, RecordKey.require(key)), value).getOrThrow()
    }

    @Test
    fun `the first commit of a public repository is announced with its record`() =
        runTest {
            val h = FirehoseHarness()
            val alice = h.createAccount("alice")
            h.write(h.did(alice), profile, "self", profileValue("Alice"))

            val event = assertIs<RepoCommitEvent>(h.events().single())

            assertEquals(1, event.seq)
            assertEquals(h.did(alice), event.repo)
            assertNull(event.since)
            assertNull(event.prevData)
            val op = event.ops.single()
            assertEquals(RepoOperationAction.CREATE, op.action)
            assertEquals("app.logdate.actor.profile/self", op.path)
            val car = CarCodec.readCar(event.blocks)
            assertEquals(listOf(event.commit), car.roots)
            assertTrue(op.cid in car.blocks.map { it.cid })
        }

    @Test
    fun `a later commit names the revision and tree it follows`() =
        runTest {
            val h = FirehoseHarness()
            val alice = h.createAccount("alice")
            h.write(h.did(alice), profile, "self", profileValue("Alice"))
            h.write(h.did(alice), profile, "self", profileValue("Alice C."))

            val (first, second) = h.events().map { assertIs<RepoCommitEvent>(it) }

            assertEquals(first.rev, second.since)
            assertNull(first.prevData)
            assertNotNull(second.prevData)
            assertEquals(RepoOperationAction.UPDATE, second.ops.single().action)
            assertEquals(first.ops.single().cid, second.ops.single().prev)
            assertTrue(second.seq > first.seq)
        }

    @Test
    fun `a deletion is announced with the record it removed`() =
        runTest {
            val h = FirehoseHarness()
            val alice = h.createAccount("alice")
            h.write(h.did(alice), document, "3kabc", documentValue("A story"))
            h.write(h.did(alice), profile, "self", profileValue("Alice"))
            DefaultRepoEngine(h.blocks).deleteRecord(RepoRecordId(h.did(alice), document, RecordKey.require("3kabc"))).getOrThrow()

            val deletion = assertIs<RepoCommitEvent>(h.events().last())

            assertEquals(RepoOperationAction.DELETE, deletion.ops.single().action)
            assertNull(deletion.ops.single().cid)
        }

    @Test
    fun `an account on legacy storage produces no events`() =
        runTest {
            val h = FirehoseHarness()
            val legacy = h.createAccount("legacy", personalSpace = false)

            h.write(h.did(legacy), profile, "self", profileValue("Legacy"))

            assertTrue(h.events().isEmpty())
        }

    @Test
    fun `a repository holding one private record is never announced`() =
        runTest {
            val h = FirehoseHarness()
            val alice = h.createAccount("alice")
            val engine = DefaultRepoEngine(h.blocks)
            engine
                .putRecord(
                    RepoRecordId(h.did(alice), Nsid.require("studio.hypertext.logdate.content"), RecordKey.require("entry-1")),
                    buildJsonObject { put("content", "my diary") },
                ).getOrThrow()

            h.write(h.did(alice), profile, "self", profileValue("Alice"))

            assertTrue(h.events().isEmpty(), "a tree names every record, so a private record keeps the whole repository off the stream")
        }

    @Test
    fun `a commit after an unannounced one is announced as a sync event`() =
        runTest {
            val h = FirehoseHarness()
            val alice = h.createAccount("alice", personalSpace = false)
            h.write(h.did(alice), profile, "self", profileValue("one"))
            check(h.accounts.updateStorageModeIfUnchanged(alice.id, AccountStorageMode.LEGACY, AccountStorageMode.PERSONAL_SPACE_V1))

            h.write(h.did(alice), profile, "self", profileValue("two"))

            val event = assertIs<RepoSyncEvent>(h.events().single())
            assertEquals(h.did(alice), event.did)
            val car = CarCodec.readCar(event.blocks)
            assertEquals(1, car.blocks.size)
            assertEquals(car.roots.single(), car.blocks.single().cid)
        }

    @Test
    fun `more operations than the event can carry are announced as a sync event`() =
        runTest {
            val h = FirehoseHarness()
            val alice = h.createAccount("alice")
            val engine = DefaultRepoEngine(h.blocks)

            engine
                .putRecords(
                    (1..201).map { index ->
                        BatchRecordWrite(
                            RepoRecordId(h.did(alice), document, RecordKey.require("3k" + index.toString().padStart(5, 'a'))),
                            documentValue("story $index"),
                        )
                    },
                ).getOrThrow()

            assertIs<RepoSyncEvent>(h.events().single())
        }

    @Test
    fun `an older commit than the one already announced is ignored`() =
        runTest {
            val h = FirehoseHarness()
            val alice = h.createAccount("alice")
            h.write(h.did(alice), profile, "self", profileValue("one"))
            h.write(h.did(alice), profile, "self", profileValue("two"))
            val first =
                h.rawBlocks
                    .listCommits(h.did(alice))
                    .getOrThrow()
                    .first()

            h.sequencer.commitWritten(first)

            assertEquals(2, h.events().size)
        }

    @Test
    fun `announcing an account tells followers its identity its status and its repository`() =
        runTest {
            val h = FirehoseHarness()
            val alice = h.createAccount("alice")
            h.write(h.did(alice), profile, "self", profileValue("Alice"))
            val before = h.events().size

            val announced = h.sequencer.announce(h.did(alice), alice.handle)

            assertTrue(announced)
            val added = h.events().drop(before)
            assertEquals(alice.handle, assertIs<RepoIdentityEvent>(added[0]).handle)
            assertTrue(assertIs<RepoAccountEvent>(added[1]).active)
            assertEquals(
                Tid.fromLong(
                    h.rawBlocks
                        .readHead(h.did(alice))
                        .getOrThrow()!!
                        .revision,
                ),
                assertIs<RepoSyncEvent>(added[2]).rev,
            )
        }

    @Test
    fun `announcing an account on legacy storage announces nothing`() =
        runTest {
            val h = FirehoseHarness()
            val legacy = h.createAccount("legacy", personalSpace = false)

            assertFalse(h.sequencer.announce(h.did(legacy), legacy.handle))
            assertFalse(h.sequencer.identityChanged(h.did(legacy), legacy.handle))

            assertTrue(h.events().isEmpty())
        }

    @Test
    fun `a deleted account is announced to followers that heard about it`() =
        runTest {
            val h = FirehoseHarness()
            val alice = h.createAccount("alice")
            h.sequencer.announce(h.did(alice), alice.handle)
            h.accounts.deleteAccount(alice.id)

            assertTrue(h.sequencer.accountStatusChanged(h.did(alice), active = false, status = "deleted"))

            val event = assertIs<RepoAccountEvent>(h.events().last())
            assertFalse(event.active)
            assertEquals("deleted", event.status)
        }

    @Test
    fun `a deleted account nobody heard about stays unannounced`() =
        runTest {
            val h = FirehoseHarness()
            val legacy = h.createAccount("legacy", personalSpace = false)
            h.accounts.deleteAccount(legacy.id)

            assertFalse(h.sequencer.accountStatusChanged(h.did(legacy), active = false, status = "deleted"))

            assertTrue(h.events().isEmpty())
        }

    @Test
    fun `event numbers follow each other without gaps`() =
        runTest {
            val h = FirehoseHarness()
            val alice = h.createAccount("alice")
            val bob = h.createAccount("bob")
            h.write(h.did(alice), profile, "self", profileValue("A"))
            h.write(h.did(bob), profile, "self", profileValue("B"))
            h.sequencer.announce(h.did(bob), bob.handle)

            assertEquals((1L..h.eventStore.latestSeq()).toList(), h.eventStore.read(0, 100).map { it.seq })
        }
}
