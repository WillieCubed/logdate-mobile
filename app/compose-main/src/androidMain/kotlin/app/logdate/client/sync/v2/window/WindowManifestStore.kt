package app.logdate.client.sync.v2.window

import app.logdate.client.datastore.KeyValueStorage
import app.logdate.client.sync.datalayer.v2.PeerDeliveryContext
import app.logdate.client.sync.datalayer.v2.WearJson
import app.logdate.client.sync.datalayer.v2.WindowReason
import io.github.aakira.napier.Napier
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable

@Serializable
data class PublishedItem(
    val contentVersion: Long,
    val reasons: Set<WindowReason>,
    val revision: Long = 0,
    val bodySha256: String? = null,
    val syncVersion: Long = 0,
)

/** What the phone last published to the watch's window, by note id, and when each tombstone was written. */
@Serializable
data class PublishedWindow(
    val items: Map<String, PublishedItem> = emptyMap(),
    val tombstones: Map<String, Long> = emptyMap(),
    val moments: Map<String, PublishedItem> = emptyMap(),
    val momentTombstones: Map<String, Long> = emptyMap(),
)

class WindowManifestStore(
    private val storage: KeyValueStorage,
) {
    private val allocating = Mutex()

    suspend fun read(peer: PeerDeliveryContext): PublishedWindow {
        val text = storage.getString(key(peer)) ?: return PublishedWindow()
        return try {
            WearJson.json.decodeFromString(PublishedWindow.serializer(), text)
        } catch (e: IllegalArgumentException) {
            Napier.e("The record of what this phone sent the watch's window could not be read; the whole window is sent again", e)
            PublishedWindow()
        }
    }

    suspend fun write(
        peer: PeerDeliveryContext,
        window: PublishedWindow,
    ) {
        storage.putString(key(peer), WearJson.encode(PublishedWindow.serializer(), window))
    }

    suspend fun beginPublication(peer: PeerDeliveryContext): Boolean {
        val interrupted = storage.getString(key(peer) + ".publishing") != null
        storage.putString(key(peer) + ".publishing", "1")
        return interrupted
    }

    suspend fun finishPublication(peer: PeerDeliveryContext) {
        storage.remove(key(peer) + ".publishing")
    }

    suspend fun reserveRevision(peer: PeerDeliveryContext): Long =
        allocating.withLock {
            val previous = revision(peer)
            check(previous < Long.MAX_VALUE) { "The watch library revision is exhausted" }
            val next = previous + 1
            storage.putString(key(peer) + ".revision", next.toString())
            next
        }

    suspend fun revision(peer: PeerDeliveryContext): Long {
        val saved = storage.getString(key(peer) + ".revision") ?: return 0
        return checkNotNull(saved.toLongOrNull()?.takeIf { it >= 0 }) { "The watch library revision is unreadable" }
    }

    private fun key(peer: PeerDeliveryContext): String = "$KEY.${WearJson.encode(PeerDeliveryContext.serializer(), peer)}"

    companion object {
        const val KEY = "phone.watch_window.published.v1"
    }
}
