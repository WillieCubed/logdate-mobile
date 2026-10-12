package app.logdate.client.sync.v2.window

import app.logdate.client.datastore.KeyValueStorage
import app.logdate.client.sync.datalayer.v2.PeerDeliveryContext
import app.logdate.client.sync.datalayer.v2.WearJson
import app.logdate.client.sync.datalayer.v2.WindowReason
import io.github.aakira.napier.Napier
import kotlinx.serialization.Serializable

@Serializable
data class PublishedItem(
    val contentVersion: Long,
    val reasons: Set<WindowReason>,
)

/** What the phone last published to the watch's window, by note id, and when each tombstone was written. */
@Serializable
data class PublishedWindow(
    val items: Map<String, PublishedItem> = emptyMap(),
    val tombstones: Map<String, Long> = emptyMap(),
)

class WindowManifestStore(
    private val storage: KeyValueStorage,
) {
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

    private fun key(peer: PeerDeliveryContext): String = "$KEY.${WearJson.encode(PeerDeliveryContext.serializer(), peer)}"

    companion object {
        const val KEY = "phone.watch_window.published.v1"
    }
}
