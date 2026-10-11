package app.logdate.client.sync.v2.receipts

import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.TaskCompletionSource
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Every request must settle even when its service fails or stops before the coroutine can start. */
fun answerPhoneRequest(
    scope: CoroutineScope,
    answer: suspend () -> ByteArray?,
): Task<ByteArray> {
    val response = TaskCompletionSource<ByteArray>()
    scope
        .launch {
            try {
                val bytes = withContext(NonCancellable) { answer() }
                if (bytes == null) {
                    response.trySetException(IllegalArgumentException("This phone cannot answer the request"))
                } else {
                    response.trySetResult(bytes)
                }
            } catch (e: Exception) {
                Napier.w("The phone could not answer the watch's request; the watch may retry", e)
                response.trySetException(e)
            }
        }.invokeOnCompletion { cause ->
            if (cause != null) response.trySetException(Exception("The phone request was interrupted", cause))
        }
    return response.task
}
