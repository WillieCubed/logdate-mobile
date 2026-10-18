package app.logdate.client.restore

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

internal enum class NativeMediaQuarantineResult {
    COMPLETE,
    HANDED_OFF,
    RETRY,
}

/** Holds foreground startup until the latest durable native-restore delivery is reconciled. */
internal suspend fun awaitNativeMediaQuarantine(
    pendingDelivery: () -> String?,
    quarantine: suspend (isCurrent: () -> Boolean) -> Boolean,
    acknowledge: (String) -> Boolean,
    enqueue: suspend (String) -> Unit,
) {
    var backoff = 1_000L
    while (true) {
        val delivery =
            try {
                pendingDelivery()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                delay(backoff)
                backoff = (backoff * 2).coerceAtMost(30_000L)
                continue
            } ?: return
        val result =
            NativeMediaQuarantineRecovery(
                pendingDelivery = pendingDelivery,
                quarantine = quarantine,
                acknowledge = acknowledge,
                enqueue = enqueue,
            ).run(delivery)
        when (result) {
            NativeMediaQuarantineResult.COMPLETE -> return
            NativeMediaQuarantineResult.HANDED_OFF -> continue
            NativeMediaQuarantineResult.RETRY -> {
                delay(backoff)
                backoff = (backoff * 2).coerceAtMost(30_000L)
            }
        }
    }
}

/** Reconciles one durable native-restore marker without ever acknowledging an incomplete pass. */
internal class NativeMediaQuarantineRecovery(
    private val pendingDelivery: () -> String?,
    private val quarantine: suspend (isCurrent: () -> Boolean) -> Boolean,
    private val acknowledge: (String) -> Boolean,
    private val enqueue: suspend (String) -> Unit,
) {
    suspend fun run(expectedDelivery: String): NativeMediaQuarantineResult =
        try {
            val currentDelivery = pendingDelivery() ?: return NativeMediaQuarantineResult.COMPLETE
            if (currentDelivery != expectedDelivery) {
                enqueue(currentDelivery)
                return NativeMediaQuarantineResult.HANDED_OFF
            }
            if (!quarantine { pendingDelivery() == expectedDelivery }) return retryOrScheduleCurrent(expectedDelivery)
            if (acknowledge(expectedDelivery)) return NativeMediaQuarantineResult.COMPLETE
            retryOrScheduleCurrent(expectedDelivery)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            retryOrScheduleCurrent(expectedDelivery)
        }

    private suspend fun retryOrScheduleCurrent(expectedDelivery: String): NativeMediaQuarantineResult =
        try {
            val currentDelivery = pendingDelivery()
            if (currentDelivery != null && currentDelivery != expectedDelivery) {
                enqueue(currentDelivery)
                NativeMediaQuarantineResult.HANDED_OFF
            } else {
                NativeMediaQuarantineResult.RETRY
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            NativeMediaQuarantineResult.RETRY
        }
}
