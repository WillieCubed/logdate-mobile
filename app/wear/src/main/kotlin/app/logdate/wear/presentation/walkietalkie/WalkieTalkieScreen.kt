package app.logdate.wear.presentation.walkietalkie

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import app.logdate.wear.presentation.audio.components.AudioWaveform
import app.logdate.wear.presentation.audio.components.RecordingTimer
import kotlinx.coroutines.flow.collectLatest
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun WalkieTalkieScreen(
    onNavigateBack: () -> Unit,
    viewModel: WalkieTalkieViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            when (event) {
                WalkieTalkieEvent.NavigateBack -> onNavigateBack()
            }
        }
    }

    val backgroundColor by animateColorAsState(
        targetValue = when (uiState.phase) {
            WalkieTalkiePhase.RECORDING -> Color(0xFF8B1A1A)
            WalkieTalkiePhase.SAVED -> Color(0xFF1B5E20)
            else -> MaterialTheme.colorScheme.background
        },
        animationSpec = tween(200),
        label = "bg",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor),
        contentAlignment = Alignment.Center,
    ) {
        when (uiState.phase) {
            WalkieTalkiePhase.READY -> ReadyContent(
                onTouchDown = viewModel::onTouchDown,
                onTouchUp = viewModel::onTouchUp,
            )
            WalkieTalkiePhase.RECORDING -> RecordingContent(
                durationMs = uiState.recordingDurationMs,
                audioLevels = uiState.audioLevels,
                onTouchUp = viewModel::onTouchUp,
            )
            WalkieTalkiePhase.SAVING -> SavingContent()
            WalkieTalkiePhase.SAVED -> SavedContent(durationMs = uiState.savedDurationMs)
            WalkieTalkiePhase.TOO_SHORT -> TooShortContent()
            WalkieTalkiePhase.ERROR -> ErrorContent(message = uiState.errorMessage)
        }
    }
}

@Composable
private fun ReadyContent(
    onTouchDown: () -> Unit,
    onTouchUp: () -> Unit,
) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val alpha by transition.animateFloat(
        initialValue = 0.6f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "textAlpha",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        onTouchDown()
                        tryAwaitRelease()
                        onTouchUp()
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "HOLD TO\nRECORD",
            style = MaterialTheme.typography.titleSmall,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
        )
    }
}

@Composable
private fun RecordingContent(
    durationMs: Long,
    audioLevels: List<Float>,
    onTouchUp: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        tryAwaitRelease()
                        onTouchUp()
                    },
                )
            },
        contentAlignment = Alignment.Center,
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
                text = "RECORDING",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun SavingContent() {
    Text(
        text = "Saving...",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun SavedContent(durationMs: Long) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Default.Check,
            contentDescription = "Saved",
            tint = Color.White,
            modifier = Modifier.size(48.dp),
        )
        Text(
            text = "Saved",
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
private fun TooShortContent() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Too short",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "Hold longer",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

@Composable
private fun ErrorContent(message: String?) {
    Text(
        text = message ?: "Something went wrong",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(horizontal = 24.dp),
    )
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
