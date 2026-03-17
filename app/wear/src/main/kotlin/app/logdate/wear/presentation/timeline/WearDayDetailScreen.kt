package app.logdate.wear.presentation.timeline

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconButton
import androidx.wear.compose.material3.IconButtonDefaults
import androidx.wear.compose.material3.LinearProgressIndicator
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TimeText
import app.logdate.client.repository.journals.JournalNote
import app.logdate.wear.R
import app.logdate.wear.playback.AudioOutputState
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@Composable
fun WearDayDetailScreen(
    viewModel: WearTimelineViewModel,
) {
    val detail by viewModel.selectedDayState.collectAsState()
    val playbackState by viewModel.playbackState.collectAsState()
    val outputState by viewModel.audioOutputState.collectAsState()
    val dayDetail = detail ?: return

    WearDayDetailContent(
        detail = dayDetail,
        playbackState = playbackState,
        audioOutputState = outputState,
        onToggleNote = viewModel::toggleNote,
        onOpenBluetoothSettings = viewModel::openBluetoothSettings,
    )
}

@Composable
internal fun WearDayDetailContent(
    detail: WearDayDetailUiState,
    playbackState: WearPlaybackUiState,
    audioOutputState: AudioOutputState,
    onToggleNote: (JournalNote.Audio) -> Unit,
    onOpenBluetoothSettings: () -> Unit,
) {
    val listState = rememberScalingLazyListState()

    ScreenScaffold(
        timeText = { TimeText() },
        scrollState = listState,
    ) {
        ScalingLazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth(),
        ) {
            item(key = "header") {
                Text(
                    text = formatDetailHeader(detail.date),
                    style = MaterialTheme.typography.titleSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp),
                )
            }

            if (detail.entries.isEmpty()) {
                item(key = "empty") {
                    Text(
                        text = stringResource(R.string.wear_day_detail_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            } else {
                items(
                    items = detail.entries,
                    key = { it.uid.toString() },
                ) { note ->
                    NoteEntryCard(
                        note = note,
                        playbackState = playbackState,
                        audioOutputState = audioOutputState,
                        onToggleNote = onToggleNote,
                        onOpenBluetoothSettings = onOpenBluetoothSettings,
                    )
                }
            }
        }
    }
}

@Composable
private fun NoteEntryCard(
    note: JournalNote,
    playbackState: WearPlaybackUiState,
    audioOutputState: AudioOutputState,
    onToggleNote: (JournalNote.Audio) -> Unit,
    onOpenBluetoothSettings: () -> Unit,
) {
    val timezone = TimeZone.currentSystemDefault()
    val time = note.creationTimestamp.toLocalDateTime(timezone)
    val timeLabel = "%d:%02d %s".format(
        if (time.hour % 12 == 0) 12 else time.hour % 12,
        time.minute,
        if (time.hour < 12) "am" else "pm",
    )

    when (note) {
        is JournalNote.Audio -> {
            AudioNoteCard(
                note = note,
                timeLabel = timeLabel,
                playbackState = playbackState,
                audioOutputState = audioOutputState,
                onToggle = { onToggleNote(note) },
                onOpenBluetoothSettings = onOpenBluetoothSettings,
            )
        }

        is JournalNote.Text -> {
            Card(
                onClick = { },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    Icons.Default.TextFields,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = note.content.take(60),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = timeLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        is JournalNote.Image -> {
            Card(
                onClick = { },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(R.string.wear_day_detail_photo),
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    text = timeLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        is JournalNote.Video -> {
            Card(
                onClick = { },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(R.string.wear_day_detail_video),
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    text = timeLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Resolved display state for an audio note card.
 */
private sealed interface AudioCardState {
    data object NoOutput : AudioCardState
    data class Playing(val progress: Float, val durationMs: Long) : AudioCardState
    data object Idle : AudioCardState
}

private fun resolveAudioCardState(
    noteId: kotlin.uuid.Uuid,
    playbackState: WearPlaybackUiState,
    audioOutputState: AudioOutputState,
): AudioCardState {
    if (audioOutputState is AudioOutputState.Unavailable) return AudioCardState.NoOutput
    return when (playbackState) {
        is WearPlaybackUiState.Active -> {
            if (playbackState.noteId == noteId) {
                AudioCardState.Playing(
                    progress = playbackState.progress,
                    durationMs = playbackState.durationMs,
                )
            } else {
                AudioCardState.Idle
            }
        }
        is WearPlaybackUiState.Idle -> AudioCardState.Idle
    }
}

@Composable
private fun AudioNoteCard(
    note: JournalNote.Audio,
    timeLabel: String,
    playbackState: WearPlaybackUiState,
    audioOutputState: AudioOutputState,
    onToggle: () -> Unit,
    onOpenBluetoothSettings: () -> Unit,
) {
    val cardState = resolveAudioCardState(note.uid, playbackState, audioOutputState)

    Card(
        onClick = {
            when (cardState) {
                is AudioCardState.NoOutput -> onOpenBluetoothSettings()
                is AudioCardState.Playing -> onToggle()
                is AudioCardState.Idle -> onToggle()
            }
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            when (cardState) {
                is AudioCardState.NoOutput -> {
                    Icon(
                        Icons.AutoMirrored.Filled.BluetoothSearching,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = stringResource(R.string.wear_playback_connect_headphones),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                is AudioCardState.Playing -> {
                    IconButton(
                        onClick = onToggle,
                        modifier = Modifier.size(24.dp),
                        colors = IconButtonDefaults.iconButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                    ) {
                        Icon(
                            Icons.Default.Stop,
                            contentDescription = stringResource(R.string.wear_playback_stop),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    val elapsed = (cardState.progress * cardState.durationMs / 1000).toLong()
                    Text(
                        text = "%d:%02d".format(elapsed / 60, elapsed % 60),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                }

                is AudioCardState.Idle -> {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = stringResource(R.string.wear_playback_play),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                    val seconds = note.durationMs / 1000
                    val durationFormatted = "%d:%02d".format(seconds / 60, seconds % 60)
                    Text(
                        text = stringResource(R.string.wear_day_detail_voice_note, durationFormatted),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        when (cardState) {
            is AudioCardState.Playing -> {
                LinearProgressIndicator(
                    progress = { cardState.progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                )
            }
            is AudioCardState.NoOutput,
            is AudioCardState.Idle -> {
                Text(
                    text = timeLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun formatDetailHeader(date: LocalDate): String {
    val monthName = date.month.name.lowercase().replaceFirstChar { it.uppercase() }
    return stringResource(R.string.wear_day_detail_header, monthName, date.day, date.year)
}
