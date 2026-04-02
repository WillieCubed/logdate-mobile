package app.logdate.feature.core.settings.ui.watch

import kotlin.time.Instant

/**
 * Describes the current connection state between the phone and a Wear OS watch.
 */
sealed interface WatchConnectionState {
    /**
     * No Wear OS watch is currently available to associate or connect.
     */
    data object NoPairedWatch : WatchConnectionState

    /**
     * A reachable watch exists, but LogDate has not been associated with it yet.
     */
    data class NeedsAssociation(
        val watchName: String,
    ) : WatchConnectionState

    /**
     * The system association flow is currently in progress.
     */
    data object AssociationPending : WatchConnectionState

    /**
     * A watch is paired but LogDate is not installed on it.
     */
    data class AppNotInstalled(
        val watchName: String,
    ) : WatchConnectionState

    /**
     * LogDate is installed on the watch and communication is possible.
     */
    data class Connected(
        val watchName: String,
        val lastSynced: Instant?,
        val pendingCount: Int,
    ) : WatchConnectionState

    /**
     * The watch was previously connected but is currently out of range.
     */
    data class OutOfRange(
        val watchName: String,
        val lastSynced: Instant?,
    ) : WatchConnectionState

    /**
     * Still determining the connection state.
     */
    data object Loading : WatchConnectionState
}
