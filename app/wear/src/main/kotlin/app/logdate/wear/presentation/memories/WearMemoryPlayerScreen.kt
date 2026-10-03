package app.logdate.wear.presentation.memories

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconButton
import androidx.wear.compose.material3.IconButtonDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TimeText
import app.logdate.wear.R
import app.logdate.wear.playback.AudioOutputState
import app.logdate.wear.presentation.recording.formatDuration
import app.logdate.wear.presentation.timeline.WearPlaybackUiState
import app.logdate.wear.presentation.timeline.formatDayLabel
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.koin.compose.viewmodel.koinViewModel
import kotlin.uuid.Uuid

@Composable
fun WearMemoryPlayerScreen(
    noteId: Uuid,
    viewModel: WearMemoryPlayerViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    LaunchedEffect(noteId) { viewModel.open(noteId) }
    DisposableEffect(viewModel) { onDispose { viewModel.close() } }

    WearMemoryPlayerContent(
        state = state,
        onPlayPause = viewModel::onPlayPause,
        onSkipBack = viewModel::onSkipBack,
        onSkipForward = viewModel::onSkipForward,
        onOpenBluetoothSettings = viewModel::onOpenBluetoothSettings,
    )
}

@Composable
internal fun WearMemoryPlayerContent(
    state: MemoryPlayerUiState,
    onPlayPause: () -> Unit = {},
    onSkipBack: () -> Unit = {},
    onSkipForward: () -> Unit = {},
    onOpenBluetoothSettings: () -> Unit = {},
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
) {
    ScreenScaffold(timeText = { TimeText() }) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            val memory = state.memory
            when {
                !state.isLoaded -> Unit
                memory == null -> PlayerMessage(stringResource(R.string.wear_memory_not_found))
                else -> PlayerBody(state, memory, timeZone, onPlayPause, onSkipBack, onSkipForward, onOpenBluetoothSettings)
            }
        }
    }
}

@Composable
private fun PlayerBody(
    state: MemoryPlayerUiState,
    memory: VoiceMemoryItem,
    timeZone: TimeZone,
    onPlayPause: () -> Unit,
    onSkipBack: () -> Unit,
    onSkipForward: () -> Unit,
    onOpenBluetoothSettings: () -> Unit,
) {
    val playback = state.playback
    val active = playback as? WearPlaybackUiState.Active
    val blocked = playback is WearPlaybackUiState.BlockedOutput || state.output is AudioOutputState.Unavailable
    val controlsEnabled = active != null

    Box(modifier = Modifier.height(TITLE_SLOT_HEIGHT).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
        Text(
            text = memoryTitle(memory, timeZone),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
    Row(
        modifier = Modifier.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PlayerSideButton(Icons.Default.Replay10, R.string.wear_player_skip_back, controlsEnabled, onSkipBack)
        PlayButton(playback = playback, blocked = blocked, onPlayPause = onPlayPause, onOpenBluetoothSettings = onOpenBluetoothSettings)
        PlayerSideButton(Icons.Default.Forward10, R.string.wear_player_skip_forward, controlsEnabled, onSkipForward)
    }
    Box(modifier = Modifier.height(STATUS_SLOT_HEIGHT).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        PlayerStatus(playback = playback, blocked = blocked, memory = memory)
    }
}

@Composable
private fun PlayButton(
    playback: WearPlaybackUiState,
    blocked: Boolean,
    onPlayPause: () -> Unit,
    onOpenBluetoothSettings: () -> Unit,
) {
    val active = playback as? WearPlaybackUiState.Active
    val (icon, description) =
        when {
            blocked -> Icons.AutoMirrored.Filled.BluetoothSearching to R.string.wear_playback_connect_headphones
            active != null && !active.isPaused -> Icons.Default.Pause to R.string.wear_player_pause
            else -> Icons.Default.PlayArrow to R.string.wear_player_play
        }
    IconButton(
        onClick = if (blocked) onOpenBluetoothSettings else onPlayPause,
        enabled = blocked || playback !is WearPlaybackUiState.Preparing,
        modifier = Modifier.size(PLAY_BUTTON_SIZE),
        colors =
            IconButtonDefaults.iconButtonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
    ) {
        Icon(imageVector = icon, contentDescription = stringResource(description), modifier = Modifier.size(32.dp))
    }
}

@Composable
private fun PlayerSideButton(
    icon: ImageVector,
    description: Int,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(SIDE_BUTTON_SIZE),
        colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Icon(imageVector = icon, contentDescription = stringResource(description), modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun PlayerStatus(
    playback: WearPlaybackUiState,
    blocked: Boolean,
    memory: VoiceMemoryItem,
) {
    when {
        blocked -> PlayerMessage(stringResource(R.string.wear_playback_connect_headphones))
        playback is WearPlaybackUiState.Preparing -> PlayerMessage(stringResource(R.string.wear_playback_preparing_audio))
        playback is WearPlaybackUiState.Error -> PlayerMessage(stringResource(R.string.wear_playback_retry_download))
        playback is WearPlaybackUiState.Active -> {
            val position = (memory.durationMs * playback.progress).toLong()
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                ProgressBar(progress = playback.progress)
                Text(
                    text = stringResource(R.string.wear_player_position, formatDuration(position), formatDuration(memory.durationMs)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        else -> PlayerMessage(formatDuration(memory.durationMs))
    }
}

@Composable
private fun ProgressBar(progress: Float) {
    Box(
        modifier =
            Modifier
                .width(PROGRESS_BAR_WIDTH)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
        )
    }
}

@Composable
private fun PlayerMessage(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        maxLines = 2,
        modifier = Modifier.padding(horizontal = 24.dp),
    )
}

@Composable
private fun memoryTitle(
    memory: VoiceMemoryItem,
    timeZone: TimeZone,
): String {
    val day = memory.createdAt.toLocalDateTime(timeZone).date
    return "${formatDayLabel(day)}, ${formatMemoryTime(memory, timeZone)}"
}

private val TITLE_SLOT_HEIGHT = 34.dp
private val STATUS_SLOT_HEIGHT = 34.dp
private val PLAY_BUTTON_SIZE = 72.dp
private val SIDE_BUTTON_SIZE = 40.dp
private val PROGRESS_BAR_WIDTH = 96.dp
