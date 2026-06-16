package app.logdate.wear.presentation.timeline

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
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
import app.logdate.client.media.device.AudioRouteRepository
import app.logdate.client.media.device.MediaDeviceCategory
import app.logdate.client.media.device.MediaDeviceKind
import app.logdate.client.media.device.MediaDeviceSelectionUiState
import app.logdate.client.media.device.MediaDeviceUiState
import app.logdate.client.media.device.systemControlledSelection
import app.logdate.client.repository.journals.JournalNote
import app.logdate.wear.R
import app.logdate.wear.playback.AudioOutputState
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.koin.compose.koinInject

internal object WearDayDetailTags {
    const val OUTPUT_SUMMARY = "wear_day_detail_output_summary"
    const val OUTPUT_PICKER = "wear_day_detail_output_picker"
    const val BLUETOOTH_SETTINGS = "wear_day_detail_bluetooth_settings"

    fun outputDevice(deviceId: String): String = "wear_day_detail_output_device_$deviceId"

    fun audioNote(noteId: kotlin.uuid.Uuid): String = "wear_day_detail_audio_note_$noteId"
}

@Composable
fun WearDayDetailScreen(
    date: LocalDate,
    viewModel: WearTimelineViewModel,
) {
    val audioRouteRepository: AudioRouteRepository = koinInject()
    LaunchedEffect(date) {
        viewModel.selectDay(date)
    }
    val detail by viewModel.selectedDayState.collectAsState()
    val playbackState by viewModel.playbackState.collectAsState()
    val outputState by viewModel.audioOutputState.collectAsState()
    val outputSelection by audioRouteRepository.outputDevices.collectAsState()
    var isOutputPickerVisible by remember { mutableStateOf(false) }
    val dayDetail = detail ?: return

    WearDayDetailContent(
        detail = dayDetail,
        playbackState = playbackState,
        audioOutputState = outputState,
        outputSelection = outputSelection,
        isOutputPickerVisible = isOutputPickerVisible,
        onToggleOutputPicker = {
            isOutputPickerVisible = !isOutputPickerVisible
        },
        onSelectOutputDevice = { deviceId ->
            audioRouteRepository.selectOutputDevice(deviceId)
            isOutputPickerVisible = false
        },
        onToggleNote = viewModel::toggleNote,
        onOpenBluetoothSettings = viewModel::openBluetoothSettings,
    )
}

@Composable
internal fun WearDayDetailContent(
    detail: WearDayDetailUiState,
    playbackState: WearPlaybackUiState,
    audioOutputState: AudioOutputState,
    outputSelection: MediaDeviceSelectionUiState = defaultWearOutputSelection(),
    isOutputPickerVisible: Boolean = false,
    onToggleOutputPicker: () -> Unit = {},
    onSelectOutputDevice: (String) -> Unit = {},
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
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = formatDetailHeader(detail.date),
                        style = MaterialTheme.typography.titleSmall,
                        textAlign = TextAlign.Center,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(bottom = 4.dp),
                    )
                    Text(
                        text = formatOutputHeaderText(outputSelection, audioOutputState),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutputRouteSummaryCard(
                        outputSelection = outputSelection,
                        audioOutputState = audioOutputState,
                        onClick = onToggleOutputPicker,
                    )
                }
            }

            if (isOutputPickerVisible) {
                item(key = "output-picker") {
                    WearAudioOutputPicker(
                        outputSelection = outputSelection,
                        audioOutputState = audioOutputState,
                        onSelectDevice = onSelectOutputDevice,
                        onOpenBluetoothSettings = onOpenBluetoothSettings,
                    )
                }
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
private fun OutputRouteSummaryCard(
    outputSelection: MediaDeviceSelectionUiState,
    audioOutputState: AudioOutputState,
    onClick: () -> Unit,
) {
    val selectedDevice = outputSelection.selectedDevice
    val summary =
        when {
            audioOutputState is AudioOutputState.Unavailable -> {
                stringResource(R.string.wear_playback_connect_headphones)
            }
            outputSelection.isSelectionControllable -> "Tap to choose output"
            else -> "Tap for output options"
        }

    Card(
        onClick = onClick,
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag(WearDayDetailTags.OUTPUT_SUMMARY)
                .padding(top = 4.dp),
    ) {
        Text(
            text = selectedDevice?.label ?: "System output",
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = summary,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun WearAudioOutputPicker(
    outputSelection: MediaDeviceSelectionUiState,
    audioOutputState: AudioOutputState,
    onSelectDevice: (String) -> Unit,
    onOpenBluetoothSettings: () -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag(WearDayDetailTags.OUTPUT_PICKER),
    ) {
        Text(
            text = "Audio output",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        outputSelection.devices.forEach { device ->
            OutputRouteDeviceCard(
                device = device,
                isSelected = device.id == outputSelection.selectedDeviceId,
                isSelectionControllable = outputSelection.isSelectionControllable,
                onSelectDevice = onSelectDevice,
                onOpenBluetoothSettings = onOpenBluetoothSettings,
            )
        }

        if (outputSelection.devices.isEmpty() || audioOutputState is AudioOutputState.Unavailable) {
            Text(
                text = stringResource(R.string.wear_playback_connect_headphones),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        outputSelection.routeControlMessage?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Card(
            onClick = onOpenBluetoothSettings,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .testTag(WearDayDetailTags.BLUETOOTH_SETTINGS),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.BluetoothSearching,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = "Bluetooth settings",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun OutputRouteDeviceCard(
    device: MediaDeviceUiState,
    isSelected: Boolean,
    isSelectionControllable: Boolean,
    onSelectDevice: (String) -> Unit,
    onOpenBluetoothSettings: () -> Unit,
) {
    Card(
        onClick = {
            if (isSelectionControllable && device.isAvailable) {
                onSelectDevice(device.id)
            } else {
                onOpenBluetoothSettings()
            }
        },
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag(WearDayDetailTags.outputDevice(device.id)),
    ) {
        Text(
            text = device.label,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = outputRouteStatusText(device, isSelected, isSelectionControllable),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun formatOutputHeaderText(
    outputSelection: MediaDeviceSelectionUiState,
    audioOutputState: AudioOutputState,
): String {
    val selectedLabel = outputSelection.selectedDevice?.label ?: "System output"
    return if (audioOutputState is AudioOutputState.Unavailable) {
        "Output unavailable"
    } else {
        "Output: $selectedLabel"
    }
}

private fun outputRouteStatusText(
    device: MediaDeviceUiState,
    isSelected: Boolean,
    isSelectionControllable: Boolean,
): String {
    if (!device.isAvailable) return "Unavailable"
    return when {
        isSelected -> "Current"
        !isSelectionControllable -> "Managed by Wear OS"
        device.category == MediaDeviceCategory.BLUETOOTH -> "Bluetooth"
        device.category == MediaDeviceCategory.WIRED -> "Wired"
        device.isExternal -> "External"
        else -> "Available"
    }
}

private fun defaultWearOutputSelection(): MediaDeviceSelectionUiState =
    systemControlledSelection(MediaDeviceKind.AUDIO_OUTPUT)

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
    val timeLabel =
        "%d:%02d %s".format(
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

    data object Preparing : AudioCardState

    data class Playing(
        val progress: Float,
        val durationMs: Long,
    ) : AudioCardState

    data object Error : AudioCardState

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
        is WearPlaybackUiState.BlockedOutput -> {
            if (playbackState.noteId == noteId) AudioCardState.NoOutput else AudioCardState.Idle
        }
        is WearPlaybackUiState.Error -> {
            if (playbackState.noteId == noteId) AudioCardState.Error else AudioCardState.Idle
        }
        is WearPlaybackUiState.Idle -> AudioCardState.Idle
        is WearPlaybackUiState.Preparing -> {
            if (playbackState.noteId == noteId) AudioCardState.Preparing else AudioCardState.Idle
        }
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
                is AudioCardState.Preparing -> onToggle()
                is AudioCardState.Playing -> onToggle()
                is AudioCardState.Error -> onToggle()
                is AudioCardState.Idle -> onToggle()
            }
        },
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag(WearDayDetailTags.audioNote(note.uid)),
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

                is AudioCardState.Preparing -> {
                    Icon(
                        Icons.Default.Mic,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = stringResource(R.string.wear_playback_preparing_audio),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                }

                is AudioCardState.Playing -> {
                    IconButton(
                        onClick = onToggle,
                        modifier = Modifier.size(24.dp),
                        colors =
                            IconButtonDefaults.iconButtonColors(
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

                is AudioCardState.Error -> {
                    Icon(
                        Icons.Default.Mic,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = stringResource(R.string.wear_playback_retry_download),
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
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                )
            }
            is AudioCardState.NoOutput,
            is AudioCardState.Preparing,
            is AudioCardState.Error,
            is AudioCardState.Idle,
            -> {
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
    val monthName =
        date.month.name
            .lowercase()
            .replaceFirstChar { it.uppercase() }
    return stringResource(R.string.wear_day_detail_header, monthName, date.day, date.year)
}
