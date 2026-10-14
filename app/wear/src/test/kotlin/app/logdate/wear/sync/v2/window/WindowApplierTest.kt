package app.logdate.wear.sync.v2.window

import app.logdate.client.repository.journals.JournalNote
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class WindowApplierTest {
    private val f = WindowFixture()

    @Test fun `verified phone selection reasons survive projection and removal`() =
        runTest {
            val reasons = setOf(app.logdate.client.sync.datalayer.v2.WindowReason.REWIND)
            assertEquals(WindowApplied(1, 0), f.applier.reconcile(f.peer, f.response(), listOf(f.item(reasons = reasons))))
            assertEquals(
                reasons,
                f.members
                    .read(f.peer)
                    .entries[f.note.uid.toString()]
                    ?.reasons,
            )
            assertTrue(f.applier.onTombstone(f.tombstone()))
            assertTrue(
                f.members
                    .read(f.peer)
                    .entries
                    .isEmpty(),
            )
        }

    @Test fun `initial verified snapshot creates and tracks an absent phone note`() =
        runTest {
            assertEquals(WindowApplied(1, 0), f.seed())
            assertEquals(f.note, f.stored[f.note.uid])
            assertEquals(
                1L,
                f.members
                    .read(f.peer)
                    .entries[f.note.uid.toString()]
                    ?.revision,
            )
        }

    @Test fun `without a snapshot live events cannot reconstruct lost ownership`() =
        runTest {
            assertFalse(f.applier.onItem(f.item()))
            assertFalse(f.applier.onTombstone(f.tombstone()))
            assertTrue(f.stored.isEmpty())
        }

    @Test fun `unknown local notes including an identical watch capture never become evictable`() =
        runTest {
            f.stored[f.note.uid] = f.note
            assertNotNull(f.seed())
            assertTrue(
                f.members
                    .read(f.peer)
                    .entries
                    .isEmpty(),
            )
            assertNotNull(f.applier.reconcile(f.peer, f.response(emptyList(), 2), emptyList()))
            assertEquals(f.note, f.stored[f.note.uid])
            assertTrue(f.forgotten.isEmpty())
        }

    @Test fun `verified tombstones remove only managed notes and pulled media`() =
        runTest {
            f.seed()
            assertTrue(f.applier.onTombstone(f.tombstone()))
            assertNull(f.stored[f.note.uid])
            assertEquals(listOf(f.note.uid), f.forgotten)
            assertTrue(
                f.members
                    .read(f.peer)
                    .entries
                    .isEmpty(),
            )
        }

    @Test fun `pending removals retain ownership and retry after the local edit settles`() =
        runTest {
            f.seed()
            f.pending += f.note.uid.toString()
            assertFalse(f.applier.onTombstone(f.tombstone()))
            assertTrue(f.note.uid.toString() in f.members.read(f.peer).entries)
            f.pending.clear()
            assertTrue(f.applier.onTombstone(f.tombstone()))
            assertNull(f.stored[f.note.uid])
        }

    @Test fun `local edits before outbox enqueue survive updates and removal`() =
        runTest {
            f.seed()
            val edited = f.note.copy(content = "watch edit")
            f.stored[f.note.uid] = edited
            assertFalse(f.applier.onItem(f.item(f.note.copy(lastUpdated = Instant.fromEpochMilliseconds(6)), 2)))
            assertFalse(f.applier.onTombstone(f.tombstone(3)))
            assertEquals(edited, f.stored[f.note.uid])
        }

    @Test fun `a returned unchanged note supersedes its delayed tombstone`() =
        runTest {
            f.seed()
            f.applier.onTombstone(f.tombstone())
            assertFalse(f.applier.onItem(f.item()))
            assertTrue(f.applier.onItem(f.item(revision = 3)))
            assertFalse(f.applier.onTombstone(f.tombstone()))
            assertEquals(f.note, f.stored[f.note.uid])
        }

    @Test fun `foreign sources and old exchanges cannot update or remove a note`() =
        runTest {
            f.seed()
            assertFalse(f.applier.onTombstone(f.tombstone().copy(sourceNodeId = "other")))
            assertFalse(f.applier.onItem(f.item(revision = 4).copy(sourceNodeId = "other")))
            val previous = f.peer.copy(phoneNode = "former")
            assertFalse(f.applier.onItem(f.item(revision = 4, context = previous)))
            assertEquals(f.note, f.stored[f.note.uid])
        }

    @Test fun `an edit appearing at transaction entry is preserved`() =
        runTest {
            f.seed()
            val edited = f.note.copy(content = "concurrent local edit")
            f.beforeTransaction = { f.stored[f.note.uid] = edited }
            assertFalse(f.applier.onTombstone(f.tombstone()))
            assertEquals(edited, f.stored[f.note.uid])
        }

    @Test fun `a peer change during a database write rolls the mutation back`() =
        runTest {
            f.seed()
            f.afterWrite = { f.current = null }
            assertFalse(f.applier.onTombstone(f.tombstone()))
            assertEquals(f.note, f.stored[f.note.uid])
            assertTrue(f.forgotten.isEmpty())
        }

    @Test fun `a snapshot does not prune a newer live item`() =
        runTest {
            f.seed()
            assertTrue(f.applier.onItem(f.item(revision = 3)))
            assertNotNull(f.applier.reconcile(f.peer, f.response(emptyList(), 2), emptyList()))
            assertEquals(f.note, f.stored[f.note.uid])
        }

    @Test fun `missing desired items and stale snapshots never prune the existing library`() =
        runTest {
            f.seed()
            val next = f.note.copy(uid = kotlin.uuid.Uuid.random())
            assertNull(f.applier.reconcile(f.peer, f.response(listOf(next), 2), emptyList()))
            assertEquals(f.note, f.stored[f.note.uid])
            assertNotNull(f.applier.reconcile(f.peer, f.response(emptyList(), 3), emptyList()))
            assertNull(f.applier.reconcile(f.peer, f.response(revision = 1), listOf(f.item())))
        }

    @Test fun `duplicate members and wrong body identities cannot establish a snapshot`() =
        runTest {
            val duplicate = f.response().let { it.copy(members = it.members + it.members) }
            assertNull(f.applier.reconcile(f.peer, duplicate, listOf(f.item())))
            val other = f.item(f.note.copy(uid = kotlin.uuid.Uuid.random())).copy(path = f.item().path)
            assertNull(f.applier.reconcile(f.peer, f.response(), listOf(other)))
            assertTrue(f.stored.isEmpty())
        }

    @Test fun `a removal arriving before its note still prevents late resurrection`() =
        runTest {
            assertNotNull(f.applier.reconcile(f.peer, f.response(emptyList(), 0), emptyList()))
            assertFalse(f.applier.onTombstone(f.tombstone()))
            assertFalse(f.applier.onItem(f.item()))
            assertTrue(f.stored.isEmpty())
            assertTrue(f.applier.onItem(f.item(revision = 3)))
        }

    @Test fun `one publication revision cannot carry different content`() =
        runTest {
            f.seed()
            assertFalse(f.applier.onItem(f.item(f.note.copy(content = "changed without a new revision"))))
            assertEquals(f.note, f.stored[f.note.uid])
        }

    @Test fun `failed ownership persistence occurs before the database mutation`() =
        runTest {
            var writes = 0
            f.beforeStoreWrite = { if (++writes == 2) error("storage unavailable") }
            assertFailsWith<IllegalStateException> { f.seed() }
            assertTrue(f.stored.isEmpty())
            assertTrue(
                f.members
                    .read(f.peer)
                    .entries
                    .isEmpty(),
            )
        }

    @Test fun `a crash after database commit retains intent for safe recovery`() =
        runTest {
            var writes = 0
            f.beforeStoreWrite = { if (++writes == 3) error("storage unavailable after commit") }
            assertFailsWith<IllegalStateException> { f.seed() }
            assertEquals(f.note, f.stored[f.note.uid])
            f.beforeStoreWrite = {}
            assertNotNull(f.seed())
            assertTrue(f.applier.onTombstone(f.tombstone()))
            assertNull(f.stored[f.note.uid])
        }

    @Test fun `corrupt ownership metadata preserves local notes and holds live events`() =
        runTest {
            f.seed()
            val key =
                f.backing.values.keys
                    .single()
            f.storage.putString(key, "broken")
            assertFalse(f.applier.onTombstone(f.tombstone()))
            assertFalse(f.applier.onItem(f.item(revision = 3)))
            assertNotNull(f.applier.reconcile(f.peer, f.response(emptyList(), 4), emptyList()))
            assertEquals(f.note, f.stored[f.note.uid])
        }

    @Test fun `cache cleanup failures retain membership for another removal attempt`() =
        runTest {
            f.seed()
            var fails = true
            val applier = WindowApplier(f.peers, f.notes, f.outbox, f.members, f.transactions, { if (fails) error("file unavailable") })
            assertFailsWith<IllegalStateException> { applier.onTombstone(f.tombstone()) }
            assertTrue(f.note.uid.toString() in f.members.read(f.peer).entries)
            fails = false
            assertTrue(applier.onTombstone(f.tombstone()))
            assertTrue(
                f.members
                    .read(f.peer)
                    .entries
                    .isEmpty(),
            )
        }

    @Test fun `lost local projections do not satisfy a snapshot when their body is missing`() =
        runTest {
            f.seed()
            f.stored.clear()
            assertNull(f.applier.reconcile(f.peer, f.response(), emptyList()))
        }

    @Test fun `a malformed removal timestamp cannot authorize eviction`() =
        runTest {
            f.seed()
            val item = f.tombstone()
            assertFalse(f.applier.onTombstone(item.copy(data = item.data - "removedAt")))
            assertEquals(f.note, f.stored[f.note.uid])
        }

    @Test fun `an edit after database commit is not adopted as the phone projection`() =
        runTest {
            val local = f.note.copy(content = "edit after Room commit")
            f.afterTransaction = { f.stored[f.note.uid] = local }
            assertNotNull(f.seed())
            f.afterTransaction = {}
            assertFalse(f.applier.onTombstone(f.tombstone()))
            assertEquals(local, f.stored[f.note.uid])
        }

    @Test fun `database timestamp precision can differ from the immutable wire body`() =
        runTest {
            val precise = f.note.copy(lastUpdated = Instant.fromEpochSeconds(0, 5_123_456))
            f.afterWrite = { f.stored[f.note.uid] = precise.copy(lastUpdated = Instant.fromEpochMilliseconds(5)) }
            val response = f.response(listOf(precise))
            assertNotNull(f.applier.reconcile(f.peer, response, listOf(f.item(precise))))
            assertTrue(f.applier.onItem(f.item(precise)))
            f.afterWrite = {}
            assertTrue(f.applier.onTombstone(f.tombstone()))
        }

    @Test fun `older plain audio transcripts become durable structured documents`() =
        runTest {
            val audio =
                JournalNote.Audio(
                    uid = f.note.uid,
                    mediaRef = "phone.m4a",
                    creationTimestamp = f.note.creationTimestamp,
                    lastUpdated = f.note.lastUpdated,
                    transcription = "Retained legacy words",
                )
            assertNotNull(f.applier.reconcile(f.peer, f.response(listOf(audio)), listOf(f.item(audio))))
            val saved = f.stored[audio.uid] as JournalNote.Audio
            assertEquals("Retained legacy words", saved.transcript?.plainText)
            assertEquals("", saved.transcription)
        }

    @Test fun `a settled edit to a known projection can be adopted only from its identical phone copy`() =
        runTest {
            f.seed()
            val edited = f.note.copy(content = "watch edit now stored on phone", lastUpdated = Instant.fromEpochMilliseconds(6))
            f.stored[f.note.uid] = edited
            f.pending += f.note.uid.toString()
            assertFalse(f.applier.onItem(f.item(edited, 2)))
            f.pending.clear()
            assertTrue(f.applier.onItem(f.item(edited, 2)))
            assertTrue(f.applier.onTombstone(f.tombstone(3)))
            assertNull(f.stored[f.note.uid])
        }

    @Test fun `a live tombstone cannot reuse the publication revision of a newer copy`() =
        runTest {
            f.seed()
            f.applier.onItem(f.item(revision = 3))
            f.applier.reconcile(f.peer, f.response(emptyList(), 2), emptyList())
            assertFalse(f.applier.onTombstone(f.tombstone(3)))
            assertEquals(f.note, f.stored[f.note.uid])
        }
}
