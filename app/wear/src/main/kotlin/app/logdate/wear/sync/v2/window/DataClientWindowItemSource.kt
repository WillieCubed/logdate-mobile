package app.logdate.wear.sync.v2.window

import android.net.Uri
import app.logdate.client.sync.datalayer.v2.PeerDeliveryContext
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataMapItem
import kotlinx.coroutines.tasks.await

/** The URI authority is retained on every record; an unbound prefix query is never trusted. */
class DataClientWindowItemSource(
    private val data: DataClient,
) : WindowItemSource {
    override suspend fun items(peer: PeerDeliveryContext): List<WindowItem> {
        val uri =
            Uri
                .Builder()
                .scheme("wear")
                .authority(peer.phoneNode)
                .path("/logdate/v2/p2w/window/")
                .build()
        val buffer = data.getDataItems(uri, DataClient.FILTER_PREFIX).await()
        return try {
            buffer.mapNotNull { item ->
                val path = item.uri.path ?: return@mapNotNull null
                val source = item.uri.host ?: return@mapNotNull null
                val map = DataMapItem.fromDataItem(item).dataMap
                WindowItem(source, path, map.keySet().mapNotNull { key -> map.getString(key)?.let { key to it } }.toMap())
            }
        } finally {
            buffer.release()
        }
    }
}
