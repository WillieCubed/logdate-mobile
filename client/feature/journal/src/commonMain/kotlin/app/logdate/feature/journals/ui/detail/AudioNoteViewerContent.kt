@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.journals.ui.detail

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.logdate.client.text.asString
import app.logdate.feature.editor.ui.audio.expansion.ImmersiveAudioScreen
import app.logdate.ui.audio.LocalAudioPlaybackState
import app.logdate.ui.common.BannerContent
import app.logdate.ui.common.MessageBanner
import app.logdate.ui.theme.Spacing
import logdate.client.feature.journal.generated.resources.Res
import logdate.client.feature.journal.generated.resources.recording_playback_failed
import org.jetbrains.compose.resources.stringResource

// region Audio viewer content

@Composable
fun AudioNoteViewerContent(
    uiState: AudioNoteViewerUiState,
    onGoBack: () -> Unit,
    onPlayPause: () -> Unit = {},
    onSeek: (Float) -> Unit = {},
    onSkipBack: () -> Unit = {},
    onSkipForward: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val audioPlaybackState = LocalAudioPlaybackState.current

    when (uiState) {
        AudioNoteViewerUiState.Loading -> {
            NoteViewerLoadingContent(modifier = modifier)
        }

        is AudioNoteViewerUiState.Error -> {
            NoteViewerErrorContent(
                message = uiState.message.asString(),
                modifier = modifier,
            )
        }

        is AudioNoteViewerUiState.Ready -> {
            // Only the player itself carries the caller's modifier (which may include a
            // shared-element transition anchor) — the wrapping Box is a plain overlay host so the
            // error banner doesn't get pulled into that transition's resize/clip animation.
            Box(modifier = Modifier.fillMaxSize()) {
                ImmersiveAudioScreen(
                    amplitudes = uiState.context.amplitudes,
                    progress = uiState.playbackState.progress,
                    isPlaying = uiState.playbackState.isPlaying,
                    palette = uiState.context.palette,
                    daylightPeriod = uiState.context.daylightPeriod,
                    durationMs = uiState.durationMs,
                    createdAt = uiState.createdAt,
                    segments = uiState.context.segments,
                    detectedSounds = uiState.detectedSounds,
                    onPlayPause = onPlayPause,
                    onSeek = onSeek,
                    onSkipBack = onSkipBack,
                    onSkipForward = onSkipForward,
                    onClose = onGoBack,
                    outputSelection = audioPlaybackState.outputSelection,
                    onOutputDeviceSelected = audioPlaybackState.selectOutputDevice,
                    modifier = modifier.fillMaxSize(),
                )
                AudioPlaybackErrorBanner(
                    visible = uiState.playbackState.errorMessage != null,
                    modifier =
                        Modifier
                            .align(Alignment.TopCenter)
                            .statusBarsPadding()
                            .padding(Spacing.lg),
                )
            }
        }
    }
}

/**
 * Transient banner shown over the immersive player when the current recording fails to play
 * (e.g. the underlying file is missing or unreadable), so a failed tap gives real feedback
 * instead of silently doing nothing.
 */
@Composable
private fun AudioPlaybackErrorBanner(
    visible: Boolean,
    modifier: Modifier = Modifier,
) {
    MessageBanner(
        content =
            if (visible) {
                BannerContent(
                    message = stringResource(Res.string.recording_playback_failed),
                    icon = Icons.Filled.Error,
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                )
            } else {
                null
            },
        modifier = modifier,
    )
}

// endregion
