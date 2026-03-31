package app.logdate.wear.sync

import android.content.Context
import app.logdate.client.sync.datalayer.WearAudioRequestPaths
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.ChannelClient
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.NodeClient
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.PutDataRequest
import com.google.android.gms.wearable.Wearable
import io.github.aakira.napier.Napier
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.uuid.Uuid

/**
 * Production [WearDataLayerClient] backed by Google Play Services Wearable APIs.
 */
fun interface WearPutDataRequestFactory {
    fun create(
        path: String,
        data: Map<String, String>,
    ): PutDataRequest
}

class GoogleWearDataLayerClient(
    private val dataClient: DataClient,
    private val channelClient: ChannelClient,
    private val capabilityClient: CapabilityClient,
    private val nodeClient: NodeClient,
    private val messageClient: MessageClient,
    private val coroutineScope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val audioTransferTimeoutMs: Long = AUDIO_TRANSFER_TIMEOUT_MS,
    private val putDataRequestFactory: WearPutDataRequestFactory = defaultWearPutDataRequestFactory,
) : WearDataLayerClient {
    constructor(
        context: Context,
        coroutineScope: CoroutineScope,
        ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
        audioTransferTimeoutMs: Long = AUDIO_TRANSFER_TIMEOUT_MS,
    ) : this(
        dataClient = Wearable.getDataClient(context),
        channelClient = Wearable.getChannelClient(context),
        capabilityClient = Wearable.getCapabilityClient(context),
        nodeClient = Wearable.getNodeClient(context),
        messageClient = Wearable.getMessageClient(context),
        coroutineScope = coroutineScope,
        ioDispatcher = ioDispatcher,
        audioTransferTimeoutMs = audioTransferTimeoutMs,
    )

    override suspend fun putDataItem(path: String, data: Map<String, String>): Boolean {
        return try {
            val request = putDataRequestFactory.create(path, data)
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

    override suspend fun getConnectedPhoneName(): String? {
        return try {
            val result = capabilityClient.getCapability(
                WearDataLayerClient.PHONE_CAPABILITY,
                CapabilityClient.FILTER_REACHABLE,
            ).await()
            result.nodes.firstOrNull()?.displayName
        } catch (e: Exception) {
            try {
                nodeClient.connectedNodes.await().firstOrNull()?.displayName
            } catch (e2: Exception) {
                Napier.w("Failed to get phone name", e2)
                null
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
            messageClient
                .sendMessage(phoneNode.id, path, data)
                .await()
            Napier.d("Message sent to phone: $path")
            true
        } catch (e: Exception) {
            Napier.w("Failed to send message: $path", e)
            false
        }
    }

    override suspend fun downloadAudioFromPhone(
        noteId: Uuid,
        destinationPath: String,
    ): Boolean {
        val transferPath = WearAudioRequestPaths.audioTransferPath(noteId)
        val requestPath = WearAudioRequestPaths.audioRequestPath(noteId)
        val received =
            withTimeoutOrNull(audioTransferTimeoutMs) {
                suspendCancellableCoroutine { continuation ->
                    var completed = false
                    lateinit var callback: ChannelClient.ChannelCallback
                    val scope = CoroutineScope(coroutineScope.coroutineContext + SupervisorJob() + ioDispatcher)

                    fun finish(success: Boolean) {
                        if (completed) return
                        completed = true
                        scope.cancel()
                        runCatching {
                            channelClient.unregisterChannelCallback(callback)
                        }.onFailure { error ->
                            Napier.w("Failed to unregister channel callback for $transferPath", error)
                        }
                        if (continuation.isActive) {
                            continuation.resume(success)
                        }
                    }

                    callback =
                        object : ChannelClient.ChannelCallback() {
                            override fun onChannelOpened(channel: ChannelClient.Channel) {
                                if (channel.path != transferPath) return

                                val destinationFile = File(destinationPath)
                                destinationFile.parentFile?.mkdirs()

                                scope.launch {
                                    runCatching {
                                        val inputStream = channelClient.getInputStream(channel).await()
                                        inputStream.use { input ->
                                            FileOutputStream(destinationFile).use { output ->
                                                input.copyTo(output)
                                            }
                                        }
                                    }.onFailure { error ->
                                        Napier.w("Failed to receive audio for note $noteId", error)
                                        destinationFile.delete()
                                    }

                                    runCatching {
                                        channelClient.close(channel).await()
                                    }.onFailure { error ->
                                        Napier.w("Failed to close incoming audio channel for note $noteId", error)
                                    }

                                    finish(destinationFile.exists() && destinationFile.length() > 0L)
                                }
                            }
                        }

                    channelClient.registerChannelCallback(callback)

                    continuation.invokeOnCancellation {
                        scope.cancel()
                        runCatching { channelClient.unregisterChannelCallback(callback) }
                    }

                    scope.launch {
                        val sent = sendMessage(requestPath)
                        if (!sent) {
                            finish(false)
                        }
                    }
                }
            }

        if (received != true) {
            File(destinationPath).delete()
            if (received == null) {
                Napier.w("Timed out waiting for phone audio transfer for note $noteId")
            }
        }
        return received == true
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

    private companion object {
        const val AUDIO_TRANSFER_TIMEOUT_MS = 30_000L

        val defaultWearPutDataRequestFactory =
            WearPutDataRequestFactory { path, data ->
                PutDataMapRequest.create(path).apply {
                    data.forEach { (key, value) ->
                        dataMap.putString(key, value)
                    }
                }.asPutDataRequest()
            }
    }
}
