package app.logdate.wear.sync.v2.delivery

import app.logdate.client.sync.peer.delivery.PhoneReach
import app.logdate.wear.sync.PhoneNodeSelector
import app.logdate.wear.sync.WearDataLayerClient
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.Node
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

/** What the watch's version 2 delivery needs from the Data Layer. Every write is urgent: someone just made it. */
interface DataLayerTransportV2 {
    suspend fun reach(): PhoneReach

    suspend fun put(
        path: String,
        data: Map<String, String>,
    ): Boolean

    /** Deletes the item this watch wrote at [path]; true when it is gone, including when there was none. */
    suspend fun deleteOwn(path: String): Boolean

    suspend fun sendFile(
        channelPath: String,
        localFilePath: String,
    ): Boolean

    /** Asks the phone with `MessageClient.sendRequest`; fails when no phone is in reach or it does not answer in time. */
    suspend fun request(
        path: String,
        payload: ByteArray,
    ): Result<ByteArray>
}

/** [DataLayerTransportV2] over the version 1 client for writes and files, and [MessageClient] for requests. */
class GoogleDataLayerTransportV2(
    private val dataLayer: WearDataLayerClient,
    private val phones: PhoneNodeSelector,
    private val messageClient: MessageClient,
    private val timeoutMs: Long = REQUEST_TIMEOUT_MS,
) : DataLayerTransportV2 {
    override suspend fun reach(): PhoneReach {
        val phone = selectPhone() ?: return PhoneReach.UNREACHABLE
        return if (phone.isNearby) PhoneReach.NEARBY else PhoneReach.RELAY_ONLY
    }

    override suspend fun put(
        path: String,
        data: Map<String, String>,
    ): Boolean = dataLayer.putDataItem(path, data, urgent = true)

    override suspend fun deleteOwn(path: String): Boolean = dataLayer.deleteOwnDataItem(path)

    override suspend fun sendFile(
        channelPath: String,
        localFilePath: String,
    ): Boolean = dataLayer.sendFile(channelPath, localFilePath)

    override suspend fun request(
        path: String,
        payload: ByteArray,
    ): Result<ByteArray> {
        val phone = selectPhone() ?: return Result.failure(IllegalStateException("No phone is in reach for $path"))
        return try {
            val answer = withTimeoutOrNull(timeoutMs) { messageClient.sendRequest(phone.id, path, payload).await() }
            if (answer == null) Result.failure(IllegalStateException("The phone did not answer $path in time")) else Result.success(answer)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun selectPhone(): Node? =
        try {
            phones.select()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Napier.w("Could not look for the phone; it counts as out of reach", e)
            null
        }

    private companion object {
        const val REQUEST_TIMEOUT_MS = 10_000L
    }
}
