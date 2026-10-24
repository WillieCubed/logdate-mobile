package app.logdate.server.atproto.firehose

import app.logdate.server.logging.RepoEventNotices
import io.github.aakira.napier.Napier
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import studio.hypertext.atproto.identity.AtprotoDid
import studio.hypertext.atproto.repo.CarCodec
import studio.hypertext.atproto.repo.RepoAccountEvent
import studio.hypertext.atproto.repo.RepoBlockStore
import studio.hypertext.atproto.repo.RepoCommitChange
import studio.hypertext.atproto.repo.RepoCommitChanges
import studio.hypertext.atproto.repo.RepoCommitEvent
import studio.hypertext.atproto.repo.RepoEventFrames
import studio.hypertext.atproto.repo.RepoHead
import studio.hypertext.atproto.repo.RepoIdentityEvent
import studio.hypertext.atproto.repo.RepoStreamMessage
import studio.hypertext.atproto.repo.RepoSyncEvent
import studio.hypertext.atproto.repo.SignedRepoCommit
import studio.hypertext.atproto.syntax.Tid
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Turns what happens to public repositories into the events relays follow.
 *
 * Every event passes two checks before it is stored. The account's repository must be one the
 * server may show ([PublicRepoGate]), and every record in it must be in a public collection
 * ([PublicRepoPolicy]). The second check is about the tree, not the commit: a record tree names
 * every record it holds, so a tree that held one private record would give that record's collection
 * and key to every relay, even if the commit changed something else. A repository that fails either
 * check produces no events at all.
 *
 * A commit is announced with its diff only when it follows the last revision announced for that
 * repository. Otherwise the server announces the repository's state with a `#sync` event, which
 * tells a relay to fetch the repository again, because a diff on top of a state the relay never saw
 * would not verify.
 *
 * @param blocks the block store the repositories are written to, read directly rather than through
 * the store that reports commits here.
 */
class RepoEventSequencer(
    private val store: RepoEventStore,
    private val blocks: RepoBlockStore,
    private val gate: PublicRepoGate,
    private val policy: PublicRepoPolicy,
    private val clock: Clock = Clock.System,
) {
    private val appended = MutableSharedFlow<Long>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** Emits the number of each event this instance stores, so followers on this instance wake at once. */
    val announcements: SharedFlow<Long> get() = appended

    /**
     * Announces [commit], which has just become the head of its repository. Does nothing for a
     * repository the server does not show, for a commit that is no longer the head, and for one
     * older than what was already announced.
     */
    suspend fun commitWritten(commit: SignedRepoCommit) {
        val repo = commit.commit.repo
        if (!gate.mayShow(repo)) return
        if (blocks.readHead(repo).getOrThrow()?.commitCid != commit.cid) return
        val lastRevision = store.lastRevision(repo)
        if (lastRevision != null && commit.commit.revision <= lastRevision) return
        val change = RepoCommitChanges.read(blocks, commit)
        change.exceptionOrNull()?.let { Napier.w(RepoEventNotices.COMMIT_UNREADABLE, it) }
        val tree =
            change.map(RepoCommitChange::collections).recoverCatching {
                RepoCommitChanges.collectionsAt(blocks, commit.commit.root).getOrThrow()
            }
        val collections = tree.getOrNull()
        if (collections == null || !policy.arePublic(collections)) {
            if (collections != null) Napier.i(RepoEventNotices.PRIVATE_RECORDS_HELD_BACK)
            return
        }
        val readable = change.getOrNull()
        if (readable != null && readable.previous?.commit?.revision == lastRevision) {
            val car = readable.toCommitCar()
            if (readable.operations.size <= MAX_OPERATIONS && car.size <= MAX_BLOCKS_BYTES) {
                appendCommit(readable, lastRevision, car)
                return
            }
        }
        appendSync(commit.commit.repo, commit.cid, commit.commit.revision)
    }

    /**
     * Tells followers about an account that is now public: its identity, that it is active, and the
     * state of its repository when it has one. Returns `false` and announces nothing when the server
     * does not show the account.
     */
    suspend fun announce(
        did: AtprotoDid,
        handle: String?,
    ): Boolean {
        if (!gate.mayShow(did)) return false
        appendIdentity(did, handle)
        appendAccount(did, active = true, status = null)
        val head = blocks.readHead(did).getOrThrow() ?: return true
        if (isPublicTree(head)) appendSync(did, head.commitCid, head.revision)
        return true
    }

    /**
     * Tells followers to look up [did]'s handle and keys again. Only for an account the server shows
     * or has announced before.
     */
    suspend fun identityChanged(
        did: AtprotoDid,
        handle: String?,
    ): Boolean {
        if (!isAnnounceable(did)) return false
        appendIdentity(did, handle)
        return true
    }

    /**
     * Tells followers that [did] became active or stopped being active here. Only for an account the
     * server shows or has announced before, so a deletion reaches the relays that heard of the account.
     */
    suspend fun accountStatusChanged(
        did: AtprotoDid,
        active: Boolean,
        status: String?,
    ): Boolean {
        if (!isAnnounceable(did)) return false
        appendAccount(did, active, status)
        return true
    }

    /** True when followers have already been told about [did]. */
    suspend fun hasAnnounced(did: AtprotoDid): Boolean = store.hasEvents(did)

    private suspend fun isAnnounceable(did: AtprotoDid): Boolean = gate.mayShow(did) || store.hasEvents(did)

    private suspend fun isPublicTree(head: RepoHead): Boolean {
        val collections = RepoCommitChanges.collectionsAt(blocks, head.root).getOrThrow()
        return policy.arePublic(collections).also { if (!it) Napier.i(RepoEventNotices.PRIVATE_RECORDS_HELD_BACK) }
    }

    private suspend fun appendCommit(
        change: RepoCommitChange,
        since: Long?,
        car: ByteArray,
    ) {
        val repo = change.commit.commit.repo
        val revision = change.commit.commit.revision
        val time = now()
        record(
            RepoEventDraft(repo, RepoEventKind.COMMIT, revision, time) { seq ->
                RepoEventFrames.encode(
                    RepoCommitEvent(
                        seq = seq,
                        time = time,
                        repo = repo,
                        commit = change.commit.cid,
                        rev = Tid.fromLong(revision),
                        since = since?.let(Tid::fromLong),
                        blocks = car,
                        ops = change.operations,
                        prevData = change.previous?.commit?.root,
                    ),
                )
            },
        )
    }

    private suspend fun appendSync(
        repo: AtprotoDid,
        commitCid: studio.hypertext.atproto.repo.Cid,
        revision: Long,
    ) {
        val block = requireNotNull(blocks.readBlock(commitCid).getOrThrow()) { "Missing commit block $commitCid" }
        val car = CarCodec.writeCar(roots = listOf(commitCid), blocks = listOf(block))
        val time = now()
        record(
            RepoEventDraft(repo, RepoEventKind.SYNC, revision, time) { seq ->
                RepoEventFrames.encode(RepoSyncEvent(seq = seq, time = time, did = repo, rev = Tid.fromLong(revision), blocks = car))
            },
        )
    }

    private suspend fun appendIdentity(
        did: AtprotoDid,
        handle: String?,
    ) {
        val time = now()
        record(
            RepoEventDraft(did, RepoEventKind.IDENTITY, revision = null, time = time) { seq ->
                RepoEventFrames.encode(RepoIdentityEvent(seq = seq, time = time, did = did, handle = handle))
            },
        )
    }

    private suspend fun appendAccount(
        did: AtprotoDid,
        active: Boolean,
        status: String?,
    ) {
        val time = now()
        record(
            RepoEventDraft(did, RepoEventKind.ACCOUNT, revision = null, time = time) { seq ->
                RepoEventFrames.encode(RepoAccountEvent(seq = seq, time = time, did = did, active = active, status = status))
            },
        )
    }

    private suspend fun record(draft: RepoEventDraft) {
        val stored = store.append(draft)
        appended.tryEmit(stored.seq)
    }

    private fun now(): Instant = Instant.fromEpochMilliseconds(clock.now().toEpochMilliseconds())

    private companion object {
        /** The event lexicon allows 200 operations in one commit event. */
        const val MAX_OPERATIONS = 200

        /** The event lexicon allows 2,000,000 bytes of blocks in one commit event. */
        const val MAX_BLOCKS_BYTES = 2_000_000
    }
}

/** Decodes a stored frame back into the message it carries; used by tests and diagnostics. */
internal fun StoredRepoEvent.decode(): RepoStreamMessage = RepoEventFrames.decode(frame)
