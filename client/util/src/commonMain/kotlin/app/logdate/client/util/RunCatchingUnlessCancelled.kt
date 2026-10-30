package app.logdate.client.util

import kotlin.coroutines.cancellation.CancellationException

/**
 * Like [runCatching], but rethrows [CancellationException] instead of capturing it.
 *
 * Structured concurrency relies on cancellation propagating. Capturing it as an ordinary failure
 * lets a cancelled caller keep running and reports a cancellation as if the work itself failed.
 */
inline fun <T> runCatchingUnlessCancelled(block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (failure: Throwable) {
        Result.failure(failure)
    }
