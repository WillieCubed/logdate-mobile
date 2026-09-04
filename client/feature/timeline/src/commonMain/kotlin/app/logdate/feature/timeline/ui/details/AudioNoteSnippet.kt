@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.timeline.ui.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.logdate.ui.audio.LocalAudioPlaybackState
import app.logdate.ui.audio.MomentAudioCard
import app.logdate.ui.common.noteDragSource
import app.logdate.ui.media.MediaDeviceSelector
import app.logdate.ui.theme.Spacing
import app.logdate.ui.timeline.AudioNoteUiState
import app.logdate.ui.timeline.MomentAudioUiState

/**
 * An audio note in a day's detail.
 *
 * The recording is rendered by the same component the timeline feed uses, so a note does not
 * change appearance when the user taps into the day. This wrapper adds only what the detail
 * view needs on top: output-device routing while this note is the one playing.
 */
@Composable
fun AudioNoteSnippet(
    uiState: AudioNoteUiState,
    modifier: Modifier = Modifier,
) {
    val audioPlaybackState = LocalAudioPlaybackState.current
    val isThisCurrent = audioPlaybackState.currentlyPlayingId == uiState.noteId

    Column(
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        modifier = modifier.padding(vertical = Spacing.xs).noteDragSource(uiState.noteId.toString()),
    ) {
        MomentAudioCard(
            audio =
                MomentAudioUiState(
                    uri = uiState.uri,
                    durationMs = uiState.duration,
                    noteId = uiState.noteId,
                    recordedAt = uiState.timestamp,
                ),
            timeOfDay = null,
        )

        if (isThisCurrent) {
            MediaDeviceSelector(
                selection = audioPlaybackState.outputSelection,
                onDeviceSelected = audioPlaybackState.selectOutputDevice,
                label = "Audio output",
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
