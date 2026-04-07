package app.logdate.feature.editor.ui.audio

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.logdate.ui.theme.Spacing
import logdate.client.feature.editor.generated.resources.Res
import logdate.client.feature.editor.generated.resources.convert_to_text
import logdate.client.feature.editor.generated.resources.error
import logdate.client.feature.editor.generated.resources.transcribing_audio
import logdate.client.feature.editor.generated.resources.transcription_queued
import logdate.client.feature.editor.generated.resources.we_couldnt_convert_this_recording_to_text
import logdate.client.ui.generated.resources.common_try_again
import org.jetbrains.compose.resources.stringResource
import logdate.client.ui.generated.resources.Res as UiRes

/**
 * Displays transcription UI for an audio note, handling various transcription states.
 * This is a stateless UI component that receives its state and callbacks from the parent.
 */
@Suppress("ktlint:standard:function-naming")
@Composable
fun AudioTranscriptionUi(
    transcriptionState: AudioUiState.TranscriptionState,
    onRequestTranscription: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Display appropriate UI based on transcription state
    AnimatedContent(
        targetState = transcriptionState,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        modifier = modifier.fillMaxWidth(),
    ) { state ->
        when (state) {
            is AudioUiState.TranscriptionState.NotRequested -> {
                TranscriptionNotAvailableUi(
                    onRequestTranscription = onRequestTranscription,
                    modifier = Modifier,
                )
            }
            is AudioUiState.TranscriptionState.Pending -> {
                TranscriptionPendingUi()
            }
            is AudioUiState.TranscriptionState.InProgress -> {
                TranscriptionInProgressUi()
            }
            is AudioUiState.TranscriptionState.Success -> {
                TranscriptionSuccessUi(
                    text = state.text,
                    isRefining = state.isRefining,
                )
            }
            is AudioUiState.TranscriptionState.Error -> {
                TranscriptionErrorUi(
                    errorMessage = state.message,
                    onRetry = onRequestTranscription,
                    modifier = Modifier,
                )
            }
        }
    }
}

/**
 * UI when no transcription is available yet.
 */
@Suppress("ktlint:standard:function-naming")
@Composable
private fun TranscriptionNotAvailableUi(
    onRequestTranscription: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(Spacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Button(
            onClick = onRequestTranscription,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        ) {
            Text(stringResource(Res.string.convert_to_text))
        }
    }
}

/**
 * UI when transcription is pending.
 */
@Suppress("ktlint:standard:function-naming")
@Composable
private fun TranscriptionPendingUi(modifier: Modifier = Modifier) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(Res.string.transcription_queued),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * UI when transcription is in progress.
 */
@Suppress("ktlint:standard:function-naming")
@Composable
private fun TranscriptionInProgressUi(modifier: Modifier = Modifier) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(16.dp),
            strokeWidth = 2.dp,
        )

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = stringResource(Res.string.transcribing_audio),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * UI when transcription is successful.
 *
 * When [isRefining] is true, a higher-accuracy refinement pass is still
 * rewriting parts of [text] in the background. We crossfade the text on every
 * update so each replacement feels like the words are correcting themselves
 * live in front of the user, instead of a loading state.
 */
@Suppress("ktlint:standard:function-naming")
@Composable
private fun TranscriptionSuccessUi(
    text: String,
    isRefining: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(Spacing.sm),
    ) {
        OutlinedCard(
            modifier = Modifier.fillMaxWidth(),
        ) {
            AnimatedContent(
                targetState = text,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                modifier = Modifier.padding(Spacing.md),
                label = "transcript-refinement",
            ) { current ->
                Text(
                    text = current,
                    style = MaterialTheme.typography.bodyMedium,
                    color =
                        if (isRefining) {
                            // Subtly soften the text while it's still being
                            // refined — telegraphs that the words are about
                            // to change without using a spinner or label.
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                )
            }
        }
    }
}

/**
 * UI when transcription fails.
 */
@Suppress("ktlint:standard:function-naming")
@Composable
private fun TranscriptionErrorUi(
    errorMessage: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(Spacing.sm),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.Error,
                contentDescription = stringResource(Res.string.error),
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(16.dp),
            )

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = stringResource(Res.string.we_couldnt_convert_this_recording_to_text),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )

            Spacer(modifier = Modifier.weight(1f))

            IconButton(
                onClick = onRetry,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = stringResource(UiRes.string.common_try_again),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }

        // Don't show technical error message to the user
        // Instead, we show the user-friendly error message above
    }
}
