package app.logdate.ui.sync

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

    /** A sync run is in flight. Show a quiet shape-morphing progress affordance. */
    data class Syncing(
        val pendingCount: Int = 0,
    ) : SyncPresentation()

    /**
     * Items waiting to upload but no run currently in flight (e.g. offline, backoff, debounce).
     * Shown as a tonal pill in the TopAppBar.
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
     * A transient network error. Treated as quiet — surfaced as a chip, never a banner. The
     * runtime retries automatically; the chip gives the user agency without alarming them.
     */
    data class NetworkError(
        val pendingCount: Int,
    ) : SyncPresentation()
}

/** What the user can do with a [SyncPresentation] surface. */
sealed class SyncAction {
    data object SignIn : SyncAction()

    data object Retry : SyncAction()

    data object ManageStorage : SyncAction()

    data object ReviewConflicts : SyncAction()
}
