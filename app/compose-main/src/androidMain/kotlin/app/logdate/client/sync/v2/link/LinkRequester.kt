package app.logdate.client.sync.v2.link

import com.google.android.gms.wearable.MessageClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.TimeoutException

/** Sends one request to a watch and returns its answer, or why there is none. */
fun interface LinkRequester {
    suspend fun request(
        nodeId: String,
        path: String,
        payload: ByteArray,
    ): Result<ByteArray>
}

/** [LinkRequester] over `MessageClient.sendRequest`, giving up after [timeoutMs]. */
class MessageClientLinkRequester(
    private val messageClient: MessageClient,
    private val timeoutMs: Long = REQUEST_TIMEOUT_MS,
) : LinkRequester {
    override suspend fun request(
        nodeId: String,
        path: String,
        payload: ByteArray,
    ): Result<ByteArray> =
        try {
            val answer = withTimeoutOrNull(timeoutMs) { messageClient.sendRequest(nodeId, path, payload).await() }
            if (answer == null) {
                Result.failure(TimeoutException("The watch did not answer $path within $timeoutMs ms"))
            } else {
                Result.success(answer)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }

    private companion object {
        const val REQUEST_TIMEOUT_MS = 10_000L
    }
}
