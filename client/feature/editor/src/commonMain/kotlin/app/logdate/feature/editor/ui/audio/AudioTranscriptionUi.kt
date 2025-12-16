package app.logdate.feature.editor.ui.audio

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.rounded.Error
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

/**
 * Displays transcription UI for an audio note, handling various transcription states.
 * This is a stateless UI component that receives its state and callbacks from the parent.
 */
@Composable
fun AudioTranscriptionUi(
    transcriptionState: AudioUiState.TranscriptionState,
    onRequestTranscription: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Display appropriate UI based on transcription state
    AnimatedContent(
        targetState = transcriptionState,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        modifier = modifier.fillMaxWidth()
    ) { state ->
        when (state) {
            is AudioUiState.TranscriptionState.NotRequested -> {
                TranscriptionNotAvailableUi(
                    onRequestTranscription = onRequestTranscription,
                    modifier = Modifier
                )
            }
            is AudioUiState.TranscriptionState.Pending -> {
                TranscriptionPendingUi()
            }
            is AudioUiState.TranscriptionState.InProgress -> {
                TranscriptionInProgressUi()
            }
            is AudioUiState.TranscriptionState.Success -> {
                TranscriptionSuccessUi(state.text)
            }
            is AudioUiState.TranscriptionState.Error -> {
                TranscriptionErrorUi(
                    errorMessage = state.message,
                    onRetry = onRequestTranscription,
                    modifier = Modifier
                )
            }
        }
    }
}

/**
 * UI when no transcription is available yet.
 */
@Composable
private fun TranscriptionNotAvailableUi(
    onRequestTranscription: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(Spacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Button(
            onClick = onRequestTranscription,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Text("Convert to Text")
        }
    }
}

/**
 * UI when transcription is pending.
 */
@Composable
private fun TranscriptionPendingUi(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Transcription queued...",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * UI when transcription is in progress.
 */
@Composable
private fun TranscriptionInProgressUi(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(16.dp),
            strokeWidth = 2.dp
        )
        
        Spacer(modifier = Modifier.width(8.dp))
        
        Text(
            text = "Transcribing audio...",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * UI when transcription is successful.
 */
@Composable
private fun TranscriptionSuccessUi(
    text: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(Spacing.sm)
    ) {
        OutlinedCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(Spacing.md)
            )
        }
    }
}

/**
 * UI when transcription fails.
 */
@Composable
private fun TranscriptionErrorUi(
    errorMessage: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(Spacing.sm)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.Error,
                contentDescription = "Error",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(16.dp)
            )
            
            Spacer(modifier = Modifier.width(8.dp))
            
            Text(
                text = "We couldn't convert this recording to text",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
            
            Spacer(modifier = Modifier.weight(1f))
            
            IconButton(
                onClick = onRetry,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Try again",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
        
        // Don't show technical error message to the user
        // Instead, we show the user-friendly error message above
    }
}