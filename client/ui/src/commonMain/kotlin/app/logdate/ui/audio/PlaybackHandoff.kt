@file:Suppress("ktlint:standard:function-naming")

package app.logdate.ui.audio

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import app.logdate.ui.theme.Spacing
import logdate.client.ui.generated.resources.Res
import logdate.client.ui.generated.resources.playback_handoff_accepted
import logdate.client.ui.generated.resources.playback_handoff_continue
import logdate.client.ui.generated.resources.playback_handoff_failed
import logdate.client.ui.generated.resources.playback_handoff_not_on_watch
import logdate.client.ui.generated.resources.playback_handoff_not_opened
import logdate.client.ui.generated.resources.playback_handoff_opening
import logdate.client.ui.generated.resources.playback_handoff_phone_changed
import logdate.client.ui.generated.resources.playback_handoff_retry
import logdate.client.ui.generated.resources.playback_handoff_setup
import logdate.client.ui.generated.resources.playback_handoff_starting
import logdate.client.ui.generated.resources.playback_handoff_unreachable
import logdate.client.ui.generated.resources.playback_handoff_watch_playback
import org.jetbrains.compose.resources.stringResource

enum class PlaybackHandoffFailure { WATCH_STARTING, WATCH_UNREACHABLE, WATCH_NOT_OPENED, WATCH_SETUP, WATCH_PLAYBACK, PHONE_CHANGED }

/** Continues the current audio in the watch app, separate from the phone's audio route. */
data class PlaybackHandoffState(
    val available: Boolean = false,
    val busy: Boolean = false,
    val failed: Boolean = false,
    val completed: Boolean = false,
    val noteNotOnWatch: Boolean = false,
    val failure: PlaybackHandoffFailure? = null,
    val onContinue: () -> Unit = {},
)

val LocalPlaybackHandoff = compositionLocalOf { PlaybackHandoffState() }

@Composable
fun PlaybackHandoffAction(
    state: PlaybackHandoffState,
    modifier: Modifier = Modifier,
) {
    if (!state.available && !state.failed && !state.completed) return
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        if (!state.completed) {
            AssistChip(
                onClick = state.onContinue,
                enabled = state.available && !state.busy,
                label = {
                    Text(
                        if (state.busy) {
                            stringResource(Res.string.playback_handoff_opening)
                        } else if (state.failed) {
                            stringResource(Res.string.playback_handoff_retry)
                        } else {
                            stringResource(Res.string.playback_handoff_continue)
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
            )
        }
        if (state.failed || state.completed) {
            Text(
                stringResource(
                    if (state.noteNotOnWatch) {
                        Res.string.playback_handoff_not_on_watch
                    } else if (state.failed) {
                        when (state.failure) {
                            PlaybackHandoffFailure.WATCH_STARTING -> Res.string.playback_handoff_starting
                            PlaybackHandoffFailure.WATCH_UNREACHABLE -> Res.string.playback_handoff_unreachable
                            PlaybackHandoffFailure.WATCH_NOT_OPENED -> Res.string.playback_handoff_not_opened
                            PlaybackHandoffFailure.WATCH_SETUP -> Res.string.playback_handoff_setup
                            PlaybackHandoffFailure.WATCH_PLAYBACK -> Res.string.playback_handoff_watch_playback
                            PlaybackHandoffFailure.PHONE_CHANGED -> Res.string.playback_handoff_phone_changed
                            null -> Res.string.playback_handoff_failed
                        }
                    } else {
                        Res.string.playback_handoff_accepted
                    },
                ),
                style = MaterialTheme.typography.bodySmall,
                color = if (state.failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}
