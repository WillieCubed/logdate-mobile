package app.logdate.server.atproto.firehose

import app.logdate.server.logging.RepoEventNotices
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeoutOrNull
import studio.hypertext.atproto.repo.RepoErrorMessage
import studio.hypertext.atproto.repo.RepoEventFrames
import studio.hypertext.atproto.repo.RepoInfoMessage
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.coroutineContext
import kotlin.time.Clock
import kotlin.time.Duration

/**
 * What `com.atproto.sync.subscribeRepos` sends a follower: the frames, in order, starting where the
 * follower asked.
 *
 * A follower names the last event number it saw as its cursor. Everything after it is replayed from
 * the log, then new events follow as they are stored. A follower with no cursor starts at the present.
 * A cursor older than the oldest event the log still keeps gets an `OutdatedCursor` notice first and
 * then everything the log has, because some of what it missed is gone. A cursor beyond the newest
 * event is an error, since the server cannot have sent it.
 *
 * Servers share one log, so a follower is served the same way whichever instance it reaches. Events
 * another instance stores are found by polling every [pollInterval]; events this instance stores wake
 * its followers at once through [wakeups].
 */
class RepoEventStream(
    private val store: RepoEventStore,
    private val wakeups: SharedFlow<Long>,
    private val pollInterval: Duration,
    private val maxFollowers: Int,
    private val retention: Duration,
    private val clock: Clock = Clock.System,
) {
    private val followers = AtomicInteger(0)

    /**
     * Reserves a place for one follower, or returns `null` when [maxFollowers] are already connected.
     * The caller must close the returned reservation when the follower disconnects.
     */
    fun reserve(): FollowerReservation? {
        if (followers.incrementAndGet() > maxFollowers) {
            followers.decrementAndGet()
            return null
        }
        return FollowerReservation { followers.decrementAndGet() }
    }

    /** How many followers are connected now. */
    val followerCount: Int get() = followers.get()

    /**
     * The frames for a follower that sent [cursor]. The flow ends after an error frame and otherwise
     * runs until the collector stops.
     */
    fun frames(cursor: Long?): Flow<ByteArray> =
        flow {
            val latest = store.latestSeq()
            var position = latest
            if (cursor != null) {
                if (cursor > latest) {
                    emit(RepoEventFrames.encode(RepoErrorMessage("FutureCursor", "Cursor $cursor is ahead of the newest event $latest")))
                    return@flow
                }
                val earliest = store.earliestSeq()
                // With every event pruned there is no earliest one, yet a cursor behind the newest number missed some.
                val lastUnavailable = if (earliest == null) latest else earliest - 1
                if (cursor < lastUnavailable) {
                    emit(
                        RepoEventFrames.encode(
                            RepoInfoMessage("OutdatedCursor", "Events up to $lastUnavailable are no longer available"),
                        ),
                    )
                }
                position = cursor
            }
            while (coroutineContext.isActive) {
                val batch = store.read(position, BATCH_SIZE)
                batch.forEach { event ->
                    emit(event.frame)
                    position = event.seq
                }
                if (batch.size == BATCH_SIZE) continue
                withTimeoutOrNull(pollInterval) { wakeups.first() }
            }
        }

    /**
     * Deletes events older than the retention period and returns how many went. A failure is logged
     * and reported as zero, so the next sweep retries it.
     */
    suspend fun pruneExpired(): Int =
        try {
            store.deleteBefore(clock.now() - retention)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            Napier.e(RepoEventNotices.PRUNE_FAILED, failure)
            0
        }

    /** Runs [pruneExpired] every [interval] until the coroutine is cancelled. */
    suspend fun pruneForever(interval: Duration) {
        while (coroutineContext.isActive) {
            pruneExpired()
            delay(interval)
        }
    }

    /** A place held for one connected follower. */
    fun interface FollowerReservation : AutoCloseable

    private companion object {
        const val BATCH_SIZE = 50
    }
}
