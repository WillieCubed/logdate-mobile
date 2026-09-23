package app.logdate.feature.core.sync

/**
 * UI-only projection of the sync pipeline state. Composables in this package render against
 * [SyncPresentation] directly and never see business types — so the same surface works on
 * any platform, in previews, in screenshot tests, and from a viewmodel.
 *
 * The mapping from `SyncStatus` (in `client/sync`) plus auth state lives in a viewmodel
 * outside this module, since `client/ui` deliberately doesn't depend on the sync engine.
 */
sealed class SyncPresentation {
    /**
     * Render nothing. Covers: no account, healthy idle, syncing-with-zero-pending in some
     * configurations. The chip and banner both honor this by not composing.
     */
    data object Hidden : SyncPresentation()

    /**
     * A sync run is in flight. [progressPercent] is how far through the run it is, in
     * [PROGRESS_STEP_PERCENT] steps, or null before the run's size is known.
     *
     * Stepped rather than exact so a long backup changes this value a few dozen times instead of
     * once per uploaded item: every change recomposes the home screen around the status button.
     */
    data class Syncing(
        val progressPercent: Int? = null,
    ) : SyncPresentation() {
        companion object {
            const val PROGRESS_STEP_PERCENT = 5

            /** [progressPercent] for [completed] of [total], or null when [total] is unknown or zero. */
            fun progressPercent(
                completed: Int,
                total: Int?,
            ): Int? {
                if (total == null || total <= 0) return null
                val percent = (completed.coerceIn(0, total) * 100) / total
                return percent - percent % PROGRESS_STEP_PERCENT
            }
        }
    }

    /**
     * Items waiting to upload but no run currently in flight (e.g. offline, backoff, debounce).
     * Shown as a count badge on the backup status button.
     */
    data class Pending(
        val pendingCount: Int,
    ) : SyncPresentation()

    /**
     * Auth lapsed. The most user-visible failure mode — the banner asks for re-sign-in.
     */
    data object AuthError : SyncPresentation()

    /** Quota / storage limit hit. Banner with a "Manage storage" action. */
    data class StorageError(
        val pendingCount: Int,
    ) : SyncPresentation()

    /** Edit conflicts present. Banner with a "Review conflicts" action. */
    data class ConflictError(
        val conflictCount: Int,
    ) : SyncPresentation()

    /**
     * A network error. The runtime retries automatically, so the error itself is a chip. Once
     * entries are waiting to back up, a banner also offers the list of them, since that is the
     * one thing the user can act on.
     */
    data class NetworkError(
        val pendingCount: Int,
    ) : SyncPresentation()

    /**
     * This device has no identity key, but the account already has data in the cloud. Backing up
     * is paused rather than risking a new key that could never read what is already there -- the
     * user needs to enter their recovery phrase again before anything else can happen.
     */
    data object NeedsRecovery : SyncPresentation()
}

/** What the user can do with a [SyncPresentation] surface. */
sealed class SyncAction {
    data object SignIn : SyncAction()

    data object ManageStorage : SyncAction()

    data object ReviewConflicts : SyncAction()

    /** Open the list of entries that are stuck, separate from [ReviewConflicts]'s edit conflicts. */
    data object ReviewIssues : SyncAction()

    /** Show what is waiting to back up, and why. */
    data object OpenStatus : SyncAction()

    /** Resolve [SyncPresentation.NeedsRecovery] by entering the recovery phrase again. */
    data object EnterRecoveryPhrase : SyncAction()
}
