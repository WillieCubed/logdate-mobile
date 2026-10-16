package app.logdate.client.data.account

import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive

/** Retries a durable pending renewal while its authenticated installation is still current. */
internal suspend fun retryRestoreCredentialRenewal(
    isPending: suspend () -> Boolean,
    isCurrent: () -> Boolean,
    renew: suspend () -> Unit,
    pause: suspend (Long) -> Unit = { delay(it) },
    onFailure: suspend (Exception) -> Unit = {},
) {
    var interval = 30_000L
    while (isCurrent()) {
        currentCoroutineContext().ensureActive()
        try {
            if (!isPending()) return
            renew()
            currentCoroutineContext().ensureActive()
            if (!isPending()) return
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            currentCoroutineContext().ensureActive()
            if (!isCurrent()) return
            Napier.w("Restore key renewal remains pending and will retry automatically")
            try {
                onFailure(error)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                currentCoroutineContext().ensureActive()
            }
        }
        if (!isCurrent()) return
        pause(interval)
        interval = (interval * 2).coerceAtMost(900_000L)
    }
}
