package app.logdate.client.device.restore

import android.content.Context
import io.github.aakira.napier.Napier

/** Raw framework deliveries need the same post-completion custody as immutable snapshot deliveries. */
internal class NativeCompletedRestoreReplay(
    private val context: Context,
    private val custody: NativeJournalReplayCustody,
) {
    fun shouldCapture(): Boolean = PostRestoreDetector(context).hasCompletedNativeRestoreDelivery()

    fun finishIfRequired(schedule: () -> Boolean): Boolean {
        if (!shouldCapture() || !custody.hasJournalInputs()) return false
        if (!custody.finish()) schedule()
        return true
    }
}

internal fun scheduleRetainedNativeJournalSafely(schedule: () -> Boolean): Boolean =
    try {
        schedule()
    } catch (_: Exception) {
        Napier.w("Retained journal reconciliation remains pending")
        false
    }

internal fun finishRetainedNativeDelivery(
    context: Context,
    callbackLock: Any,
): Boolean =
    synchronized(callbackLock) {
        val workspace = retainedJournalWorkspace(context)
        workspace.prepareNativeDelivery().also { if (it) workspace.finishIncomingDelivery() }
    }
