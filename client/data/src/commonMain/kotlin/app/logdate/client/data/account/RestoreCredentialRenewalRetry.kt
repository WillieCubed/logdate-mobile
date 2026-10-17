package app.logdate.client.data.account

import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive

/** Retries a durable pending renewal while its authenticated installation is still current. */
internal suspend fun retryRestoreCredentialRenewal(
    isPending: suspend () -> Boolean,
    isCurrent: suspend () -> Boolean,
    renew: suspend () -> Unit,
    pause: suspend (Long) -> Unit = { delay(it) },
    onFailure: suspend (Exception) -> Unit = {},
) {
    var interval = 30_000L
    while (true) {
        currentCoroutineContext().ensureActive()
        try {
            if (!isCurrent() || !isPending()) return
            renew()
            currentCoroutineContext().ensureActive()
            if (!isPending()) return
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            currentCoroutineContext().ensureActive()
            Napier.w("Restore key renewal remains pending and will retry automatically")
            try {
                if (!isCurrent()) return
                onFailure(error)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                currentCoroutineContext().ensureActive()
            }
        }
        try {
            if (!isCurrent()) return
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            currentCoroutineContext().ensureActive()
        }
        pause(interval)
        interval = (interval * 2).coerceAtMost(900_000L)
    }
}
