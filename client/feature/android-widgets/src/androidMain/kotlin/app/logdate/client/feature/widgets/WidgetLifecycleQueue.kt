package app.logdate.client.feature.widgets

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** An empty dequeue finishes the OS job; only interrupted processing requests explicit retry. */
internal suspend fun <T : Any> runWidgetLifecycleQueue(
    prepare: suspend () -> Unit,
    dequeue: () -> T?,
    process: suspend (T) -> Unit,
    complete: (T) -> Unit,
    retry: suspend () -> Unit,
    failed: (Exception) -> Unit,
) {
    try {
        prepare()
        var work = dequeue()
        while (work != null) {
            process(work)
            complete(work)
            work = dequeue()
        }
    } catch (error: Exception) {
        // A local persistence/delivery deadline does not mean onStopJob owns this job.
        currentCoroutineContext().ensureActive()
        failed(error)
        retry()
    }
}
