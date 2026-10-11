package app.logdate.wear.v2.moments

import app.logdate.client.repository.journals.JournalNote
import app.logdate.wear.v2.playback.PlaybackSpeed
import app.logdate.wear.v2.playback.PlaybackState
import kotlinx.datetime.TimeZone

/** What a moment's screen shows. */
sealed interface MomentUiState {
    data object Loading : MomentUiState

    /** The moment is not on this watch: deleted, removed on the phone, or never synced here. */
    data object Missing : MomentUiState

    /**
     * @property playback This moment's playback; [PlaybackState.Idle] while another recording plays.
     * @property deleteFailed Deleting it was tried and did not work.
     */
    data class Ready(
        val note: JournalNote,
        val timeZone: TimeZone,
        val playback: PlaybackState,
        val speed: PlaybackSpeed,
        val skipSilence: Boolean,
        val deleteFailed: Boolean = false,
    ) : MomentUiState
}
