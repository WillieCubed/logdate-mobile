package app.logdate.client.sync.v2.window

import android.net.Uri
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.PutDataRequest
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

/** The phone's own window data items. */
interface WindowDataItems {
    /** An [urgent] item goes out at once; the Data Layer may delay an ordinary one by up to 30 minutes. */
    suspend fun put(
        path: String,
        data: Map<String, String>,
        urgent: Boolean,
    ): Boolean

    /** Deletes the item this phone wrote at [path]; true when it is gone, including when there was none. */
    suspend fun deleteOwn(path: String): Boolean
}

class GoogleWindowDataItems(
    private val dataClient: DataClient,
    private val localNodeId: suspend () -> String?,
) : WindowDataItems {
    override suspend fun put(
        path: String,
        data: Map<String, String>,
        urgent: Boolean,
    ): Boolean =
        try {
            val map = PutDataMapRequest.create(path)
            data.forEach { (key, value) -> map.dataMap.putString(key, value) }
            val request = map.asPutDataRequest()
            if (urgent) request.setUrgent()
            dataClient.putDataItem(request).await()
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Napier.w("Failed to write the watch window item at $path", e)
            false
        }

    override suspend fun deleteOwn(path: String): Boolean {
        val node = localNodeId()
        if (node == null) {
            Napier.w("This phone's Data Layer node is unknown; the window item at $path is deleted on a later publish")
            return false
        }
        return try {
            val uri =
                Uri
                    .Builder()
                    .scheme(PutDataRequest.WEAR_URI_SCHEME)
                    .authority(node)
                    .path(path)
                    .build()
            dataClient.deleteDataItems(uri).await()
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Napier.w("Failed to delete the watch window item at $path", e)
            false
        }
    }
}
