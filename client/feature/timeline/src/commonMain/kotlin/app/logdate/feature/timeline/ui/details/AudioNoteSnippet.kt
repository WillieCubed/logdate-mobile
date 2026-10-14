@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.timeline.ui.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.logdate.ui.audio.MomentAudioCard
import app.logdate.ui.common.noteDragSource
import app.logdate.ui.provenance.CapturedOnWatchGlyph
import app.logdate.ui.provenance.showsWatchProvenance
import app.logdate.ui.theme.Spacing
import app.logdate.ui.timeline.AudioNoteUiState
import app.logdate.ui.timeline.MomentAudioUiState

/**
 * An audio note in a day's detail.
 *
 * The recording is rendered by the same component the timeline feed uses, so a note does not
 * change appearance when the user taps into the day. This wrapper adds only what the detail
 * view needs on top: watch provenance and drag support.
 */
@Composable
fun AudioNoteSnippet(
    uiState: AudioNoteUiState,
    modifier: Modifier = Modifier,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        modifier = modifier.padding(vertical = Spacing.xs).noteDragSource(uiState.noteId.toString()),
    ) {
        if (showsWatchProvenance(uiState.capturedOnWatch)) {
            CapturedOnWatchGlyph()
        }
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
    }
}
