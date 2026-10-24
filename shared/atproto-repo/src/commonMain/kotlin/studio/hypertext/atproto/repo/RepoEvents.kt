package studio.hypertext.atproto.repo

import studio.hypertext.atproto.identity.AtprotoDid
import studio.hypertext.atproto.syntax.Nsid
import studio.hypertext.atproto.syntax.RecordKey
import studio.hypertext.atproto.syntax.Tid
import kotlin.time.Instant

/**
 * What a commit did to one record.
 */
public enum class RepoOperationAction(
    /** The word the event stream uses for this action. */
    public val wireName: String,
) {
    /** The record did not exist before the commit. */
    CREATE("create"),

    /** The record existed and now has a different value. */
    UPDATE("update"),

    /** The record existed and is gone. */
    DELETE("delete"),
    ;

    public companion object {
        /** The action named [wireName], or `null` when the name is not one the protocol defines. */
        public fun fromWireName(wireName: String): RepoOperationAction? = entries.firstOrNull { it.wireName == wireName }
    }
}

/**
 * One record change inside a commit, as `com.atproto.sync.subscribeRepos` reports it.
 *
 * @property cid the record's new CID; `null` for a deletion.
 * @property prev the record's CID before the commit; `null` for a creation.
 */
public data class RepoOperation(
    val action: RepoOperationAction,
    val collection: Nsid,
    val recordKey: RecordKey,
    val cid: Cid?,
    val prev: Cid?,
) {
    /** The record's path inside the repository: `collection/recordKey`. */
    public val path: String
        get() = "$collection/$recordKey"
}

/**
 * Anything the repository event stream can carry in one WebSocket message.
 */
public sealed interface RepoStreamMessage

/**
 * A stream message that has a place in the sequence. Events are numbered per server, in the order
 * the server announced them, and a consumer resumes by naming the last number it saw.
 */
public sealed interface RepoEvent : RepoStreamMessage {
    /** The event's position in the server's sequence. */
    public val seq: Long

    /** When the server announced the event. */
    public val time: Instant
}

/**
 * A repository changed. [blocks] is a CAR file whose first root is [commit]; it holds the commit,
 * the tree that commit describes and every record the commit wrote, which is everything a consumer
 * needs to check [ops] against [prevData].
 *
 * @property since the revision of the last commit the server announced for this repository, or
 * `null` for the first.
 * @property prevData the tree root that commit had, or `null` for the first.
 */
public data class RepoCommitEvent(
    override val seq: Long,
    override val time: Instant,
    val repo: AtprotoDid,
    val commit: Cid,
    val rev: Tid,
    val since: Tid?,
    val blocks: ByteArray,
    val ops: List<RepoOperation>,
    val prevData: Cid?,
) : RepoEvent {
    override fun equals(other: Any?): Boolean =
        this === other ||
            (
                other is RepoCommitEvent &&
                    seq == other.seq &&
                    time == other.time &&
                    repo == other.repo &&
                    commit == other.commit &&
                    rev == other.rev &&
                    since == other.since &&
                    blocks.contentEquals(other.blocks) &&
                    ops == other.ops &&
                    prevData == other.prevData
            )

    override fun hashCode(): Int = 31 * seq.hashCode() + blocks.contentHashCode()
}

/**
 * The repository's current state, without a diff. A consumer that hears this fetches the
 * repository again with `com.atproto.sync.getRepo`. [blocks] is a CAR file holding only the commit.
 */
public data class RepoSyncEvent(
    override val seq: Long,
    override val time: Instant,
    val did: AtprotoDid,
    val rev: Tid,
    val blocks: ByteArray,
) : RepoEvent {
    override fun equals(other: Any?): Boolean =
        this === other ||
            (
                other is RepoSyncEvent &&
                    seq == other.seq &&
                    time == other.time &&
                    did == other.did &&
                    rev == other.rev &&
                    blocks.contentEquals(other.blocks)
            )

    override fun hashCode(): Int = 31 * seq.hashCode() + blocks.contentHashCode()
}

/**
 * An account's handle or keys changed, or the server wants every consumer to look them up again.
 */
public data class RepoIdentityEvent(
    override val seq: Long,
    override val time: Instant,
    val did: AtprotoDid,
    val handle: String?,
) : RepoEvent

/**
 * An account became active or stopped being active on this server.
 *
 * @property status why the account is not active: `deactivated`, `deleted`, `takendown`,
 * `suspended`, `desynchronized` or `throttled`. `null` while the account is active.
 */
public data class RepoAccountEvent(
    override val seq: Long,
    override val time: Instant,
    val did: AtprotoDid,
    val active: Boolean,
    val status: String?,
) : RepoEvent

/**
 * A note from the server that is not part of the sequence. `OutdatedCursor` tells a consumer that
 * the cursor it sent is older than the oldest event the server still keeps, so some events are gone.
 */
public data class RepoInfoMessage(
    val name: String,
    val message: String?,
) : RepoStreamMessage

/**
 * Why the server is about to close the stream. `FutureCursor` means the consumer named an event
 * the server has not sent yet; `ConsumerTooSlow` means it stopped reading.
 */
public data class RepoErrorMessage(
    val error: String,
    val message: String?,
) : RepoStreamMessage
