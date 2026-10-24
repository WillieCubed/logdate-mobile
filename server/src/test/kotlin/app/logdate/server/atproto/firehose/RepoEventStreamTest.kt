package app.logdate.server.atproto.firehose

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import studio.hypertext.atproto.identity.AtprotoDid
import studio.hypertext.atproto.repo.RepoErrorMessage
import studio.hypertext.atproto.repo.RepoEventFrames
import studio.hypertext.atproto.repo.RepoInfoMessage
import studio.hypertext.atproto.repo.RepoStreamMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

/**
 * The follower's view of the log: where it starts, what it waits for, and how followers and old
 * events are limited. The WebSocket route is tested separately with a real connection.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RepoEventStreamTest {
    private val repo = AtprotoDid.require("did:web:alice.logdate.app")

    private fun draft(time: Instant = Clock.System.now()) =
        RepoEventDraft(repo, RepoEventKind.ACCOUNT, revision = null, time = time) { seq ->
            RepoEventFrames.encode(
                studio.hypertext.atproto.repo
                    .RepoAccountEvent(seq, time, repo, active = true, status = null),
            )
        }

    private fun stream(
        store: RepoEventStore,
        wakeups: MutableSharedFlow<Long> = MutableSharedFlow(),
        maxFollowers: Int = 2,
        clock: Clock = Clock.System,
    ) = RepoEventStream(store, wakeups, pollInterval = 100.milliseconds, maxFollowers = maxFollowers, retention = 7.days, clock = clock)

    private fun decode(frame: ByteArray): RepoStreamMessage = RepoEventFrames.decode(frame)

    @Test
    fun `a follower without a cursor starts at the present`() =
        runTest {
            val store = InMemoryRepoEventStore()
            store.append(draft())
            val received = mutableListOf<Long>()
            val job =
                launch {
                    stream(store).frames(null).collect {
                        received +=
                            (decode(it) as studio.hypertext.atproto.repo.RepoAccountEvent).seq
                    }
                }
            runCurrent()

            store.append(draft())
            advanceTimeBy(150.milliseconds)
            runCurrent()

            assertEquals(listOf(2L), received)
            job.cancel()
        }

    @Test
    fun `a follower with a cursor receives everything after it and then waits for more`() =
        runTest {
            val store = InMemoryRepoEventStore()
            repeat(3) { store.append(draft()) }
            val received = mutableListOf<Long>()
            val job =
                launch {
                    stream(store).frames(1).collect {
                        received +=
                            (decode(it) as studio.hypertext.atproto.repo.RepoAccountEvent).seq
                    }
                }
            runCurrent()
            assertEquals(listOf(2L, 3L), received)

            store.append(draft())
            advanceTimeBy(150.milliseconds)
            runCurrent()

            assertEquals(listOf(2L, 3L, 4L), received)
            job.cancel()
        }

    @Test
    fun `an event stored by this instance wakes a waiting follower before the next poll`() =
        runTest {
            val store = InMemoryRepoEventStore()
            val wakeups = MutableSharedFlow<Long>()
            val received = mutableListOf<Long>()
            val job =
                launch {
                    stream(store, wakeups).frames(0).collect {
                        received +=
                            (decode(it) as studio.hypertext.atproto.repo.RepoAccountEvent).seq
                    }
                }
            runCurrent()

            val stored = store.append(draft())
            wakeups.emit(stored.seq)
            runCurrent()

            assertEquals(listOf(1L), received)
            job.cancel()
        }

    @Test
    fun `a cursor beyond the newest event ends the stream with a FutureCursor error`() =
        runTest {
            val store = InMemoryRepoEventStore()
            store.append(draft())

            val frames = stream(store).frames(5).toList().map(::decode)

            val error = assertIs<RepoErrorMessage>(frames.single())
            assertEquals("FutureCursor", error.error)
        }

    @Test
    fun `a cursor older than the oldest kept event gets a notice before the events that remain`() =
        runTest {
            val store = InMemoryRepoEventStore()
            val old = Clock.System.now() - 30.days
            repeat(3) { store.append(draft(old)) }
            store.deleteBefore(Clock.System.now() - 1.days)
            store.append(draft())
            val received = mutableListOf<RepoStreamMessage>()
            val job = launch { stream(store).frames(0).collect { received += decode(it) } }
            runCurrent()

            assertEquals("OutdatedCursor", assertIs<RepoInfoMessage>(received[0]).name)
            assertEquals(4L, (received[1] as studio.hypertext.atproto.repo.RepoAccountEvent).seq)
            job.cancel()
        }

    @Test
    fun `a cursor just behind the oldest kept event gets no notice because nothing was missed`() =
        runTest {
            val store = InMemoryRepoEventStore()
            store.append(draft(Clock.System.now() - 30.days))
            store.deleteBefore(Clock.System.now() - 1.days)
            store.append(draft())
            val received = mutableListOf<RepoStreamMessage>()
            val job = launch { stream(store).frames(1).collect { received += decode(it) } }
            runCurrent()

            assertEquals(1, received.size)
            assertTrue(received.single() is studio.hypertext.atproto.repo.RepoAccountEvent)
            job.cancel()
        }

    @Test
    fun `followers are limited and a place is freed when one leaves`() {
        val stream = stream(InMemoryRepoEventStore(), maxFollowers = 2)

        val first = assertNotNull(stream.reserve())
        val second = assertNotNull(stream.reserve())
        assertNull(stream.reserve())
        assertEquals(2, stream.followerCount)

        first.close()
        assertEquals(1, stream.followerCount)
        assertNotNull(stream.reserve())
        second.close()
    }

    @Test
    fun `events older than the retention period are pruned`() =
        runTest {
            val store = InMemoryRepoEventStore()
            val now = Clock.System.now()
            store.append(draft(now - 8.days))
            store.append(draft(now - 1.hours))

            val deleted = stream(store).pruneExpired()

            assertEquals(1, deleted)
            assertEquals(listOf(2L), store.read(0, 10).map { it.seq })
        }
}
