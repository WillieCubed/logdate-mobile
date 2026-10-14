package app.logdate.wear.sync.v2.window

import app.logdate.client.datastore.KeyValueStorage
import app.logdate.client.sync.datalayer.v2.PeerDeliveryContext
import app.logdate.client.sync.datalayer.v2.RecordKind
import app.logdate.client.sync.datalayer.v2.WearJson
import app.logdate.client.sync.datalayer.v2.WindowMemberRef
import app.logdate.client.sync.datalayer.v2.WindowReason
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

/** Both fingerprints survive an interrupted update: the accepted copy and its replacement intent. */
@Serializable
data class WindowProjection(
    val revision: Long,
    val contentVersion: Long,
    val fingerprints: Set<String>,
    val bodyFingerprint: String,
    val reasons: Set<WindowReason> = emptySet(),
)

@Serializable
data class WindowMembership(
    val entries: Map<String, WindowProjection> = emptyMap(),
    val retired: Map<String, Long> = emptyMap(),
    val snapshotRevision: Long? = null,
    val expected: Map<String, WindowMemberRef> = emptyMap(),
)

/** Unknown ownership never grants permission to delete local data. */
class WindowMembershipStore(
    private val storage: KeyValueStorage,
    private val kind: RecordKind = RecordKind.NOTE,
) {
    private val updates = MutableSharedFlow<Unit>(replay = 1)
    val changes = updates.asSharedFlow()

    suspend fun read(peer: PeerDeliveryContext): WindowMembership {
        val saved = storage.getString(key(peer)) ?: return WindowMembership()
        return try {
            WearJson.json.decodeFromString(WindowMembership.serializer(), saved).also { require(valid(it)) }
        } catch (e: IllegalArgumentException) {
            Napier.e("The watch library membership is unreadable; local notes are preserved until verified resync", e)
            WindowMembership()
        }
    }

    suspend fun write(
        peer: PeerDeliveryContext,
        state: WindowMembership,
    ) {
        require(valid(state)) { "Invalid watch library membership" }
        storage.putString(key(peer), WearJson.encode(WindowMembership.serializer(), state))
        updates.tryEmit(Unit)
    }

    private fun key(peer: PeerDeliveryContext): String {
        require(kind == RecordKind.NOTE || kind == RecordKind.MOMENT)
        val prefix = if (kind == RecordKind.NOTE) "watch.window.projections.v2." else "watch.window.moment_projections.v1."
        return prefix + WearJson.encode(PeerDeliveryContext.serializer(), peer)
    }

    private fun valid(state: WindowMembership): Boolean =
        (state.snapshotRevision == null || state.snapshotRevision >= 0) &&
            state.entries.all { (id, entry) ->
                Uuid.parseOrNull(id)?.toString() == id &&
                    entry.revision > 0 &&
                    entry.contentVersion >= 0 &&
                    isHash(entry.bodyFingerprint) &&
                    entry.fingerprints.isNotEmpty() &&
                    entry.fingerprints.size <= 2 &&
                    entry.fingerprints.all(::isHash)
            } &&
            state.retired.all { (id, revision) -> Uuid.parseOrNull(id)?.toString() == id && revision > 0 } &&
            state.expected.all { (id, ref) ->
                id == ref.id &&
                    Uuid.parseOrNull(id)?.toString() == id &&
                    ref.kind == kind &&
                    ref.revision > 0 &&
                    ref.revision <= (state.snapshotRevision ?: -1) &&
                    ref.contentVersion >= 0
            }

    private fun isHash(value: String) = value.length == 64 && value.all { it in '0'..'9' || it in 'a'..'f' }
}
