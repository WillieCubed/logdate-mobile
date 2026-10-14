package app.logdate.wear.sync.v2.media

import app.logdate.client.media.storage.StoredMediaReferences
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.sync.datalayer.v2.PeerDeliveryContext
import app.logdate.client.sync.datalayer.v2.RecordKind
import app.logdate.client.sync.datalayer.v2.WindowMemberRef
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.metadata.SyncMetadataService
import app.logdate.wear.sync.v2.delivery.WatchDeliveryPeer
import app.logdate.wear.sync.v2.window.WindowMembershipStore
import app.logdate.wear.sync.v2.window.WindowProjection
import app.logdate.wear.sync.v2.window.windowNoteFingerprint
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.uuid.Uuid

/** Downloads only current phone-owned window audio, through the shared verified cache boundary. */
class WatchMediaPrefetcher(
    private val peers: WatchDeliveryPeer,
    private val members: WindowMembershipStore,
    private val notes: JournalNotesRepository,
    private val outbox: SyncMetadataService,
    private val enabled: suspend () -> Boolean,
    private val supported: suspend (PeerDeliveryContext) -> Boolean,
    private val download: suspend (JournalNote.Audio, PeerDeliveryContext, suspend () -> Boolean) -> Unit,
    private val media: StoredMediaReferences = StoredMediaReferences.Unchanged,
) {
    private val running = Mutex()

    suspend fun run(): Int = running.withLock { runOnce() }

    private suspend fun runOnce(): Int {
        if (!enabled()) return 0
        val peer = peers.current() ?: return 0
        if (!active(peer)) return 0
        val entries =
            members
                .read(peer)
                .entries.entries
                .sortedByDescending { it.value.contentVersion }
        var completed = 0
        for ((id, projection) in entries) {
            currentCoroutineContext().ensureActive()
            if (!active(peer)) break
            val note = notes.getNoteById(Uuid.parse(id)) as? JournalNote.Audio ?: continue
            if (!eligible(peer, note, projection)) continue
            download(note, peer) { eligible(peer, note, projection) }
            if (!eligible(peer, note, projection)) break
            completed++
        }
        return completed
    }

    private suspend fun active(peer: PeerDeliveryContext): Boolean = enabled() && peers.isCurrent(peer) && supported(peer)

    private suspend fun eligible(
        peer: PeerDeliveryContext,
        note: JournalNote.Audio,
        projection: WindowProjection,
    ): Boolean {
        currentCoroutineContext().ensureActive()
        if (!active(peer) || outbox.hasPending(EntityType.NOTE, note.uid.toString())) return false
        val state = members.read(peer)
        val id = note.uid.toString()
        val snapshot = state.snapshotRevision ?: return false
        if (state.entries[id] != projection || (state.retired[id] ?: 0) >= projection.revision) return false
        if (projection.revision <= snapshot &&
            state.expected[id] != WindowMemberRef(RecordKind.NOTE, id, projection.contentVersion, projection.revision)
        ) {
            return false
        }
        val current = notes.getNoteById(note.uid) as? JournalNote.Audio ?: return false
        return current.lastUpdated.toEpochMilliseconds() == projection.contentVersion &&
            current.mediaRef == note.mediaRef &&
            windowNoteFingerprint(current, media) in projection.fingerprints &&
            active(peer)
    }
}
