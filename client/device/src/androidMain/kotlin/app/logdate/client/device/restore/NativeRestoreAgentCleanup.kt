package app.logdate.client.device.restore

import io.github.aakira.napier.Napier

/** Failure to inspect retained custody must never strand the native delivery mutex. */
internal fun finishNativeRestoreAgent(
    schedule: () -> Unit,
    finishNativeSession: () -> Unit,
    finishIncomingSession: () -> Unit,
    destroy: () -> Unit,
) {
    try {
        try {
            schedule()
        } catch (_: Exception) {
            Napier.w("Retained journal reconciliation remains pending")
        }
    } finally {
        try {
            finishNativeSession()
        } finally {
            try {
                finishIncomingSession()
            } finally {
                try {
                    AndroidNativeRestoreDeliveryGate.endRestore()
                } finally {
                    destroy()
                }
            }
        }
    }
}
