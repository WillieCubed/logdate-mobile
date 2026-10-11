package app.logdate.wear.sync.v2.link

import app.logdate.client.datastore.KeyValueStorage
import app.logdate.client.sync.datalayer.v2.LinkMode
import app.logdate.client.sync.peer.link.PairKeyVault
import app.logdate.client.sync.peer.state.PeerLinkState
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlin.time.Clock

/**
 * The phone this watch is linked with and how.
 *
 * @property stale The watch moved to another phone since; it waits for that phone to link it again.
 */
@Serializable
data class WatchLinkRecord(
    val mode: LinkMode,
    val phoneNode: String,
    val linkedAt: Long,
    val stale: Boolean = false,
    val accountId: String? = null,
)

/** What the watch publishes about its link in its state document. */
fun WatchLinkRecord?.peerLinkState(): PeerLinkState {
    if (this == null) return PeerLinkState.UNLINKED
    if (stale) return PeerLinkState.NEEDS_RELINK
    return when (mode) {
        LinkMode.PAIR -> PeerLinkState.PAIRED
        LinkMode.CLOUD -> PeerLinkState.LINKED
    }
}

/** The watch's link record, as one JSON value, and the pair key in secure storage. */
class WatchLinkStore(
    private val storage: KeyValueStorage,
    private val pairKeys: PairKeyVault,
    private val clock: Clock = Clock.System,
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun observe(): Flow<WatchLinkRecord?> = storage.observeString(KEY).map(::decode).distinctUntilChanged()

    suspend fun current(): WatchLinkRecord? = decode(storage.getString(KEY))

    suspend fun savePairing(
        phoneNode: String,
        pairKey: ByteArray,
    ) {
        pairKeys.put(phoneNode, pairKey)
        write(WatchLinkRecord(mode = LinkMode.PAIR, phoneNode = phoneNode, linkedAt = clock.now().toEpochMilliseconds()))
    }

    /** Marks the link with [phoneNode] stale; false when the watch is not linked with that phone. */
    suspend fun markStale(phoneNode: String): Boolean {
        val record = current() ?: return false
        if (record.phoneNode != phoneNode || record.stale) return false
        write(record.copy(stale = true))
        return true
    }

    private suspend fun write(record: WatchLinkRecord) {
        storage.putString(KEY, json.encodeToString(WatchLinkRecord.serializer(), record))
    }

    private fun decode(text: String?): WatchLinkRecord? {
        if (text == null) return null
        return try {
            json.decodeFromString(WatchLinkRecord.serializer(), text)
        } catch (e: SerializationException) {
            Napier.e("This watch's link record could not be read; it reports no link so the phone links it again", e)
            null
        } catch (e: IllegalArgumentException) {
            Napier.e("This watch's link record could not be read; it reports no link so the phone links it again", e)
            null
        }
    }

    private companion object {
        const val KEY = "watch.link.record.v1"
    }
}
