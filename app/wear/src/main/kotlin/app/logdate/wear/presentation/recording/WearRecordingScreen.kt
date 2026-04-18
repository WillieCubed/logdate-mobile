package app.logdate.wear.presentation.recording

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import app.logdate.wear.R
import app.logdate.wear.presentation.audio.components.AudioWaveform
import app.logdate.wear.presentation.audio.components.RecordingTimer
import app.logdate.wear.presentation.common.SaveFeedback
import kotlinx.coroutines.flow.collectLatest
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun WearRecordingScreen(
    onNavigateBack: () -> Unit,
    viewModel: WearRecordingViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            when (event) {
                RecordingScreenEvent.NavigateBack -> onNavigateBack()
            }
        }
    }

    val backgroundColor by animateColorAsState(
        targetValue =
            when (uiState.phase) {
                RecordingPhase.RECORDING -> Color(0xFF8B1A1A)
                RecordingPhase.SAVED -> Color(0xFF1B5E20)
                else -> MaterialTheme.colorScheme.background
            },
        animationSpec = tween(200),
        label = "bg",
    )

    // Full-screen touch handler is only active during READY and RECORDING.
    // In PAUSED state, buttons handle interaction directly.
    val useTouchHandler =
        uiState.phase == RecordingPhase.READY ||
            uiState.phase == RecordingPhase.RECORDING

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(backgroundColor)
                .then(
                    if (useTouchHandler) {
                        Modifier.pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    when (event.type) {
                                        PointerEventType.Press -> viewModel.onTouchDown()
                                        PointerEventType.Release -> viewModel.onTouchUp()
                                    }
                                }
                            }
                        }
                    } else {
                        Modifier
                    },
                ),
        contentAlignment = Alignment.Center,
    ) {
        when (uiState.phase) {
            RecordingPhase.READY -> ReadyContent()
            RecordingPhase.RECORDING ->
                ActiveRecordingContent(
                    durationMs = uiState.recordingDurationMs,
                    audioLevels = uiState.audioLevels,
                )
            RecordingPhase.PAUSED ->
                PausedContent(
                    durationMs = uiState.recordingDurationMs,
                    onResume = viewModel::onTouchDown,
                    onSave = viewModel::save,
                    onDiscard = viewModel::discard,
                )
            RecordingPhase.SAVING -> SavingContent()
            RecordingPhase.SAVED ->
                SavedContent(
                    durationMs = uiState.savedDurationMs,
                    saveFeedback = uiState.saveFeedback,
                )
            RecordingPhase.TOO_SHORT -> TooShortContent()
            RecordingPhase.ERROR -> RecordingErrorContent(message = uiState.errorMessage)
        }
    }
}

@Composable
internal fun ReadyContent() {
    val transition = rememberInfiniteTransition(label = "pulse")
    val alpha by transition.animateFloat(
        initialValue = 0.6f,
        targetValue = 0.85f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(1500),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "textAlpha",
    )

    Text(
        text = stringResource(R.string.wear_recording_hold_to_record),
        style = MaterialTheme.typography.titleSmall,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
    )
}

@Composable
internal fun ActiveRecordingContent(
    durationMs: Long,
    audioLevels: List<Float>,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        AudioWaveform(
            audioLevels = audioLevels,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        RecordingTimer(
            durationMs = durationMs,
            isRecording = true,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            text = stringResource(R.string.wear_recording_active),
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.7f),
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
internal fun PausedContent(
    durationMs: Long,
    onResume: () -> Unit,
    onSave: () -> Unit,
    onDiscard: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.wear_recording_paused),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = formatDuration(durationMs),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp, bottom = 8.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            Button(
                onClick = onResume,
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = stringResource(R.string.wear_recording_resume),
                )
            }
            Button(
                onClick = onSave,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                    ),
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Save,
                    contentDescription = stringResource(R.string.wear_recording_save),
                )
            }
            Button(
                onClick = onDiscard,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                    ),
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.cancel),
                )
            }
        }
    }
}

@Composable
internal fun SavingContent() {
    Text(
        text = stringResource(R.string.wear_recording_saving),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
internal fun SavedContent(
    durationMs: Long,
    saveFeedback: SaveFeedback? = null,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Default.Check,
            contentDescription = stringResource(R.string.wear_recording_saved),
            tint = Color.White,
            modifier = Modifier.size(48.dp),
        )
        val feedbackText =
            when (saveFeedback) {
                SaveFeedback.SYNCING_TO_PHONE -> stringResource(R.string.wear_saved_syncing_to_phone)
                SaveFeedback.SAVED_LOCALLY -> stringResource(R.string.wear_saved_on_watch)
                null -> stringResource(R.string.wear_recording_saved)
            }
        Text(
            text = feedbackText,
            style = MaterialTheme.typography.titleSmall,
            color = Color.White,
            modifier = Modifier.padding(top = 4.dp),
        )
        Text(
            text = formatDuration(durationMs),
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.7f),
        )
    }
}

@Composable
internal fun TooShortContent() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.wear_recording_too_short),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(R.string.wear_recording_hold_longer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

@Composable
internal fun RecordingErrorContent(message: String?) {
    Text(
        text = message ?: stringResource(R.string.wear_recording_error),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(horizontal = 24.dp),
    )
}

internal fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
