package app.logdate.client.sync

import kotlin.coroutines.cancellation.CancellationException

/**
 * Like [runCatching], but rethrows [CancellationException] instead of capturing it.
 *
 * Cancelled sync work must stop. Capturing the cancellation would report it as a sync failure and
 * keep working for a caller that is gone.
 */
internal inline fun <T> runCatchingUnlessCancelled(block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (failure: Throwable) {
        Result.failure(failure)
    }
