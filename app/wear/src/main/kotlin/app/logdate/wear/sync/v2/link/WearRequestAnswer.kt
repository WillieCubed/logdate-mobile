package app.logdate.wear.sync.v2.link

import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.TaskCompletionSource
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Every request must settle even when its service fails or stops before the coroutine can start. */
internal fun answerWearRequest(
    scope: CoroutineScope,
    answer: suspend () -> ByteArray?,
): Task<ByteArray> {
    val response = TaskCompletionSource<ByteArray>()
    scope
        .launch {
            try {
                val bytes = withContext(NonCancellable) { answer() }
                if (bytes == null) {
                    response.trySetException(IllegalArgumentException("This watch cannot answer the request"))
                } else {
                    response.trySetResult(bytes)
                }
            } catch (e: Exception) {
                Napier.w("The watch could not answer the phone's request; the phone may retry", e)
                response.trySetException(e)
            }
        }.invokeOnCompletion { cause ->
            if (cause != null) response.trySetException(Exception("The watch request was interrupted", cause))
        }
    return response.task
}
