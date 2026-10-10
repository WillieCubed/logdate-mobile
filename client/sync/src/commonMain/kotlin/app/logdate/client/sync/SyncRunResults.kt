package app.logdate.client.sync

/**
 * The most actionable error in a run that may have failed several ways at once, rather than
 * whichever happened to run first in the fixed upload/download order (journal, then content, then
 * association, then draft) -- code order is not a severity order, and a network hiccup on the
 * first of those must not hide an auth lapse or a full quota discovered on the second. Every error
 * is still returned to the caller either way; this only decides which one is surfaced as *the*
 * status.
 */
internal fun List<SyncError>.mostSevere(): SyncError? = SEVERITY_ORDER.firstNotNullOfOrNull { type -> firstOrNull { it.type == type } }

private val SEVERITY_ORDER =
    listOf(
        SyncErrorType.AUTHENTICATION_ERROR,
        SyncErrorType.STORAGE_ERROR,
        SyncErrorType.CONFLICT_ERROR,
        SyncErrorType.SERVER_ERROR,
        SyncErrorType.NETWORK_ERROR,
        SyncErrorType.UNKNOWN_ERROR,
    )

/**
 * The media a note points at is no longer on disk, so no number of retries can upload it.
 */
class MissingMediaException(
    mediaRef: String,
    cause: Throwable,
) : Exception("Media no longer exists at $mediaRef: ${cause.message}", cause)

/** The current state of a [DefaultSyncManager] sync operation. */
internal sealed class SyncState {
    object Idle : SyncState()

    object Syncing : SyncState()
}
