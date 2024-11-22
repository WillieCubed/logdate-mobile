package app.logdate.feature.rewind.ui.overview

import app.logdate.model.ActivityUpdate
import kotlin.uuid.Uuid

/**
 * A state representing the UI state of the Rewind overview screen.
 */
sealed interface RewindOverviewScreenUiState {
    /**
     * The UI state when the rewind data is loading.
     *
     * Clients should show a loading indicator and placeholder UI.
     */
    data object Loading : RewindOverviewScreenUiState

    /**
     * The UI state is able to show past rewinds, but not this week's.
     *
     * Clients should indicate that this week's rewind is still being prepared for the user.
     */
    data class NotReady(
        val pastRewinds: List<RewindHistoryUiState>,
    ) : RewindOverviewScreenUiState

    /**
     * The UI state when the rewind data is available.
     */
    data class Loaded(
        val pastRewinds: List<RewindHistoryUiState>,
        val mostRecentRewind: FocusRewindUiState,
    ) : RewindOverviewScreenUiState
}

/**
 * A state representing the UI state of a single focused rewind.
 */
data class FocusRewindUiState(
    /**
     * A greeting message to display to the user.
     */
    val message: String,
    val rewindId: Uuid,
    val activities: List<ActivityUiState> = listOf(),
    // TODO: Include activities
    /**
     * Whether this week's rewind is ready to be viewed.
     */
    val rewindAvailable: Boolean = false,
)

data class ActivityUiState(
    val updates: List<ActivityUpdate>,
)