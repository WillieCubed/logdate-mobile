package app.logdate.client.domain.export

import kotlin.coroutines.cancellation.CancellationException

/**
 * Like [runCatching], but rethrows [CancellationException] instead of capturing it.
 *
 * A cancelled export must stop; capturing the cancellation would record it as an issue and let
 * the export continue reading data sources for a collector that is gone.
 */
internal inline fun <T> runCatchingUnlessCancelled(block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (failure: Throwable) {
        Result.failure(failure)
    }
