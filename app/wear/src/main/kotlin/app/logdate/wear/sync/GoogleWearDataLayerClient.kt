package app.logdate.wear.sync

import android.content.Context
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import io.github.aakira.napier.Napier
import kotlinx.coroutines.tasks.await
import java.io.FileInputStream

/**
 * Production [WearDataLayerClient] backed by Google Play Services Wearable APIs.
 */
class GoogleWearDataLayerClient(
    private val context: Context,
) : WearDataLayerClient {

    private val dataClient by lazy { Wearable.getDataClient(context) }
    private val channelClient by lazy { Wearable.getChannelClient(context) }
    private val capabilityClient by lazy { Wearable.getCapabilityClient(context) }
    private val nodeClient by lazy { Wearable.getNodeClient(context) }

    override suspend fun putDataItem(path: String, data: Map<String, String>): Boolean {
        return try {
            val request = PutDataMapRequest.create(path).apply {
                data.forEach { (key, value) ->
                    dataMap.putString(key, value)
                }
                // Force update even if data hasn't changed (timestamp ensures uniqueness)
                dataMap.putLong("_syncTimestamp", System.currentTimeMillis())
            }.asPutDataRequest().setUrgent()

            dataClient.putDataItem(request).await()
            Napier.d("Data item put at path: $path")
            true
        } catch (e: Exception) {
            Napier.w("Failed to put data item at path: $path", e)
            false
        }
    }

    override suspend fun deleteDataItem(path: String): Boolean {
        return try {
            dataClient.deleteDataItems(
                android.net.Uri.Builder()
                    .scheme("wear")
                    .path(path)
                    .build(),
            ).await()
            true
        } catch (e: Exception) {
            Napier.w("Failed to delete data item at path: $path", e)
            false
        }
    }

    override suspend fun isPhoneConnected(capability: String): Boolean {
        return try {
            val result = capabilityClient.getCapability(
                capability,
                CapabilityClient.FILTER_REACHABLE,
            ).await()
            result.nodes.isNotEmpty()
        } catch (e: Exception) {
            // Fallback: check if any nodes are connected at all
            try {
                val nodes = nodeClient.connectedNodes.await()
                nodes.isNotEmpty()
            } catch (e2: Exception) {
                Napier.w("Failed to check phone connectivity", e2)
                false
            }
        }
    }

    override suspend fun sendMessage(path: String, data: ByteArray): Boolean {
        return try {
            val nodes = nodeClient.connectedNodes.await()
            val phoneNode = nodes.firstOrNull() ?: run {
                Napier.w("No connected nodes for message")
                return false
            }
            Wearable.getMessageClient(context)
                .sendMessage(phoneNode.id, path, data)
                .await()
            Napier.d("Message sent to phone: $path")
            true
        } catch (e: Exception) {
            Napier.w("Failed to send message: $path", e)
            false
        }
    }

    override suspend fun sendFile(channelPath: String, localFilePath: String): Boolean {
        return try {
            val nodes = nodeClient.connectedNodes.await()
            val phoneNode = nodes.firstOrNull() ?: run {
                Napier.w("No connected nodes for file transfer")
                return false
            }

            val channel = channelClient.openChannel(phoneNode.id, channelPath).await()
            try {
                val outputStream = channelClient.getOutputStream(channel).await()
                outputStream.use { out ->
                    val input = FileInputStream(localFilePath)
                    input.use { it.copyTo(out, bufferSize = 8192) }
                }
            } finally {
                channelClient.close(channel).await()
            }
            Napier.d("File sent via channel: $channelPath")
            true
        } catch (e: Exception) {
            Napier.w("Failed to send file via channel: $channelPath", e)
            false
        }
    }
}
