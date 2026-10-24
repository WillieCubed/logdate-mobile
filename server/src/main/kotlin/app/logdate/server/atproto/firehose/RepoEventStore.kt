package app.logdate.server.atproto.firehose

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import studio.hypertext.atproto.identity.AtprotoDid
import kotlin.time.Instant

/** The kinds of event the log keeps; the stream's `#info` and error messages are not events. */
enum class RepoEventKind(
    val code: String,
    /** True for events that say what a repository's revision is, which later commits are checked against. */
    val carriesRevision: Boolean,
) {
    COMMIT("commit", carriesRevision = true),
    SYNC("sync", carriesRevision = true),
    IDENTITY("identity", carriesRevision = false),
    ACCOUNT("account", carriesRevision = false),
    ;

    companion object {
        fun fromCode(code: String): RepoEventKind = entries.first { it.code == code }
    }
}

/**
 * An event waiting for its place in the sequence. The store calls [encode] with the number it
 * assigns, because the number is part of the frame that goes on the wire.
 *
 * @property revision for a commit or a sync, the repository revision the event announces.
 */
class RepoEventDraft(
    val repo: AtprotoDid,
    val kind: RepoEventKind,
    val revision: Long?,
    val time: Instant,
    val encode: (seq: Long) -> ByteArray,
)

/** An event as the log keeps it: the frame exactly as it is sent to every follower. */
class StoredRepoEvent(
    val seq: Long,
    val repo: AtprotoDid,
    val kind: RepoEventKind,
    val frame: ByteArray,
)

/**
 * The durable, ordered log of everything the server has announced about repositories.
 *
 * Events are numbered 1, 2, 3 and so on with no gaps, and a reader that has seen number N never
 * misses a number below it: the store numbers an event and makes it visible in one step, and the
 * next event waits for that step to finish. Server instances share one log, so any instance can
 * serve any follower.
 */
interface RepoEventStore {
    /** Numbers [draft] and stores it. Concurrent appends are numbered one after the other. */
    suspend fun append(draft: RepoEventDraft): StoredRepoEvent

    /** Up to [limit] events numbered above [afterSeq], oldest first. */
    suspend fun read(
        afterSeq: Long,
        limit: Int,
    ): List<StoredRepoEvent>

    /** The highest number handed out, or 0 before the first event. */
    suspend fun latestSeq(): Long

    /** The lowest number still stored, or `null` when the log is empty. */
    suspend fun earliestSeq(): Long?

    /** The revision of the newest commit or sync event for [repo], or `null` when it has none. */
    suspend fun lastRevision(repo: AtprotoDid): Long?

    /**
     * True when followers were ever told about [repo]: any event for it was stored, even if the retention
     * sweep has deleted every one since. A takedown or deletion months after the announcement still has to
     * reach relays that heard about the account, so this outlives the events.
     */
    suspend fun hasEvents(repo: AtprotoDid): Boolean

    /** Deletes events announced before [time] and returns how many went. */
    suspend fun deleteBefore(time: Instant): Int
}

/** A log held in memory, for tests and servers without a database. */
class InMemoryRepoEventStore : RepoEventStore {
    private val lock = Mutex()
    private val events = mutableListOf<Pair<Instant, StoredRepoEvent>>()
    private val revisions = mutableMapOf<AtprotoDid, Long>()
    private val announced = mutableSetOf<AtprotoDid>()
    private var last = 0L

    override suspend fun append(draft: RepoEventDraft): StoredRepoEvent =
        lock.withLock {
            val seq = last + 1
            val stored = StoredRepoEvent(seq, draft.repo, draft.kind, draft.encode(seq))
            last = seq
            events += draft.time to stored
            announced += draft.repo
            if (draft.kind.carriesRevision) draft.revision?.let { revisions[draft.repo] = it }
            stored
        }

    override suspend fun read(
        afterSeq: Long,
        limit: Int,
    ): List<StoredRepoEvent> =
        lock.withLock {
            events
                .asSequence()
                .map { it.second }
                .filter { it.seq > afterSeq }
                .take(limit)
                .toList()
        }

    override suspend fun latestSeq(): Long = lock.withLock { last }

    override suspend fun earliestSeq(): Long? = lock.withLock { events.firstOrNull()?.second?.seq }

    override suspend fun lastRevision(repo: AtprotoDid): Long? = lock.withLock { revisions[repo] }

    override suspend fun hasEvents(repo: AtprotoDid): Boolean = lock.withLock { repo in announced }

    override suspend fun deleteBefore(time: Instant): Int =
        lock.withLock {
            val before = events.size
            events.removeAll { it.first < time }
            before - events.size
        }
}
