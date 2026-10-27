package app.logdate.client.sync

import app.logdate.client.sync.metadata.SyncDeadLetterReason

internal object SyncRetryPolicy {
    val TRANSIENT_REASONS =
        setOf(
            SyncDeadLetterReason.NETWORK_UNAVAILABLE,
            SyncDeadLetterReason.SERVER_UNAVAILABLE,
            SyncDeadLetterReason.SIGN_IN_REQUIRED,
        )
    const val MAX_RETRY_ATTEMPTS = 9

    /** Consecutive attempts that never finished before an entry is set aside. */
    const val MAX_UNFINISHED_ATTEMPTS = 2

    /** How long a transient retained failure waits before another automatic attempt. */
    const val DEAD_LETTER_RETRY_INTERVAL_MS = 24L * 60 * 60 * 1000
}
