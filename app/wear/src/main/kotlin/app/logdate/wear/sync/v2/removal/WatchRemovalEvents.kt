package app.logdate.wear.sync.v2.removal

import android.content.Context
import app.logdate.client.sync.peer.removal.WatchRemovalProtocol
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

class WatchRemovalEvents(
    private val endpoint: WatchRemovalEndpoint,
    private val lock: WatchRemovalLock,
) {
    suspend fun replayPending(context: Context) {
        val items = Wearable.getDataClient(context).dataItems.await()
        try {
            items.forEach { item ->
                val path = item.uri.path ?: return@forEach
                if (!path.startsWith("${WatchRemovalProtocol.PATH}/")) return@forEach
                val source = item.uri.host ?: return@forEach
                try {
                    val request = DataMapItem.fromDataItem(item).dataMap.getString("request") ?: return@forEach
                    consume(source, path, mapOf("request" to request))
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    Napier.w("A retained watch removal request could not be replayed", error)
                }
            }
        } finally {
            items.release()
        }
    }

    suspend fun consume(
        node: String,
        path: String,
        data: Map<String, String>,
    ): Boolean {
        if (path.startsWith(WatchRemovalProtocol.PATH)) {
            data["request"]?.let { endpoint.answer(node, it.encodeToByteArray()) }
            return true
        }
        return lock.removed()
    }
}
