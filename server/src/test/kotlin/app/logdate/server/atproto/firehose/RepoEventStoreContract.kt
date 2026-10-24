package app.logdate.server.atproto.firehose

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.runTest
import studio.hypertext.atproto.identity.AtprotoDid
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * What every [RepoEventStore] must do, whatever it keeps events in. Each implementation's test class
 * calls these with a fresh store.
 */
internal object RepoEventStoreContract {
    private val alice = AtprotoDid.require("did:web:alice.logdate.app")
    private val bob = AtprotoDid.require("did:web:bob.logdate.app")

    private fun draft(
        repo: AtprotoDid,
        kind: RepoEventKind = RepoEventKind.COMMIT,
        revision: Long? = 10,
        time: Instant = Clock.System.now(),
    ) = RepoEventDraft(repo, kind, revision, time) { seq -> "frame-$seq-${repo.value}".encodeToByteArray() }

    fun numbersEventsFromOneWithoutGaps(store: RepoEventStore) =
        runTest {
            assertEquals(0, store.latestSeq())
            assertNull(store.earliestSeq())

            val first = store.append(draft(alice))
            val second = store.append(draft(bob))

            assertEquals(1, first.seq)
            assertEquals(2, second.seq)
            assertEquals(2, store.latestSeq())
            assertEquals(1, store.earliestSeq())
        }

    fun storesTheFrameBuiltForTheAssignedNumber(store: RepoEventStore) =
        runTest {
            store.append(draft(alice))
            store.append(draft(bob))

            val events = store.read(0, 10)

            assertContentEquals("frame-1-did:web:alice.logdate.app".encodeToByteArray(), events[0].frame)
            assertContentEquals("frame-2-did:web:bob.logdate.app".encodeToByteArray(), events[1].frame)
            assertEquals(listOf(alice, bob), events.map { it.repo })
        }

    fun readsOnlyEventsAfterTheGivenNumberUpToTheLimit(store: RepoEventStore) =
        runTest {
            repeat(5) { store.append(draft(alice)) }

            assertEquals(listOf(3L, 4L), store.read(2, 2).map { it.seq })
            assertEquals(listOf(5L), store.read(4, 10).map { it.seq })
            assertTrue(store.read(5, 10).isEmpty())
        }

    fun remembersTheKindOfEachEvent(store: RepoEventStore) =
        runTest {
            store.append(draft(alice, RepoEventKind.IDENTITY, revision = null))
            store.append(draft(alice, RepoEventKind.ACCOUNT, revision = null))
            store.append(draft(alice, RepoEventKind.SYNC, revision = 4))

            assertEquals(
                listOf(RepoEventKind.IDENTITY, RepoEventKind.ACCOUNT, RepoEventKind.SYNC),
                store.read(0, 10).map { it.kind },
            )
        }

    fun reportsTheNewestCommitOrSyncRevisionOfARepository(store: RepoEventStore) =
        runTest {
            assertNull(store.lastRevision(alice))

            store.append(draft(alice, RepoEventKind.COMMIT, revision = 10))
            store.append(draft(bob, RepoEventKind.COMMIT, revision = 99))
            store.append(draft(alice, RepoEventKind.IDENTITY, revision = null))
            assertEquals(10, store.lastRevision(alice))

            store.append(draft(alice, RepoEventKind.SYNC, revision = 12))
            store.append(draft(alice, RepoEventKind.ACCOUNT, revision = null))
            assertEquals(12, store.lastRevision(alice))
            assertEquals(99, store.lastRevision(bob))
        }

    fun knowsWhichRepositoriesHaveEvents(store: RepoEventStore) =
        runTest {
            assertFalse(store.hasEvents(alice))

            store.append(draft(alice, RepoEventKind.ACCOUNT, revision = null))

            assertTrue(store.hasEvents(alice))
            assertFalse(store.hasEvents(bob))
        }

    fun deletingOldEventsNeverReusesANumber(store: RepoEventStore) =
        runTest {
            val now = Clock.System.now()
            store.append(draft(alice, time = now - 10.hours))
            store.append(draft(alice, time = now - 9.hours))
            store.append(draft(alice, time = now))

            val deleted = store.deleteBefore(now - 5.hours)

            assertEquals(2, deleted)
            assertEquals(listOf(3L), store.read(0, 10).map { it.seq })
            assertEquals(3, store.earliestSeq())
            assertEquals(4, store.append(draft(alice)).seq)
        }

    fun deletingEverythingKeepsTheLatestNumber(store: RepoEventStore) =
        runTest {
            store.append(draft(alice, time = Clock.System.now() - 2.hours))

            store.deleteBefore(Clock.System.now() - 1.minutes)

            assertNull(store.earliestSeq())
            assertEquals(1, store.latestSeq())
            assertEquals(2, store.append(draft(alice)).seq)
        }

    fun concurrentAppendsGetDistinctNumbersWithoutGaps(store: RepoEventStore) =
        runTest {
            val stored = coroutineScope { (1..24).map { async { store.append(draft(if (it % 2 == 0) alice else bob)) } }.awaitAll() }

            assertEquals((1L..24L).toList(), stored.map { it.seq }.sorted())
            assertEquals((1L..24L).toList(), store.read(0, 100).map { it.seq })
        }
}
