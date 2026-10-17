package app.logdate.wear.sync.v2

import app.logdate.client.sync.peer.state.ActivitySnapshot
import app.logdate.client.sync.peer.state.WatchState
import app.logdate.wear.sync.v2.link.WatchLinkRecord
import app.logdate.wear.sync.v2.link.peerLinkState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf

/** How much the watch has waiting and set aside, and how much space it has left. */
data class WatchCounts(
    val outbox: Int = 0,
    val deadLetters: Int = 0,
    val freeBytes: Long? = null,
)

/**
 * The watch-owned section of the watch's state document, assembled from the parts that own each field.
 * [features] lists only what this build implements.
 */
class WatchOwnState(
    private val links: Flow<WatchLinkRecord?>,
    private val features: Set<String> = emptySet(),
    private val counts: Flow<WatchCounts> = flowOf(WatchCounts()),
    private val activity: Flow<ActivitySnapshot?> = flowOf(null),
) {
    fun observe(): Flow<WatchState> =
        combine(links, counts, activity) { link, count, doing ->
            WatchState(
                outboxCount = count.outbox,
                deadLetterCount = count.deadLetters,
                freeBytes = count.freeBytes,
                link = link.peerLinkState(),
                features = features,
                linkProblem = link?.problem,
                linkId = link?.linkId,
                sessionId = link?.sessionId,
                activity = doing,
            )
        }.distinctUntilChanged()
}
