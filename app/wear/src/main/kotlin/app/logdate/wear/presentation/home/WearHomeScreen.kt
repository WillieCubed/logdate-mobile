package app.logdate.wear.presentation.home

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Mood
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.CompactButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconButton
import androidx.wear.compose.material3.IconButtonDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TimeText
import app.logdate.wear.R
import app.logdate.wear.presentation.audio.components.AudioWaveform
import app.logdate.wear.presentation.common.SaveFeedback
import app.logdate.wear.presentation.recording.RecordingError
import app.logdate.wear.presentation.recording.RecordingPhase
import app.logdate.wear.presentation.recording.RecordingUiState
import app.logdate.wear.presentation.recording.WearRecordingViewModel
import app.logdate.wear.presentation.recording.formatDuration
import org.koin.compose.viewmodel.koinViewModel

private val STATUS_SLOT_HEIGHT = 40.dp
private val FOLLOW_UP_SLOT_HEIGHT = 34.dp
private val STATUS_HORIZONTAL_PADDING = 20.dp
private val SIDE_CONTROL_SIZE = 36.dp
private val RECORD_SURFACE_SIZE = 72.dp

@Composable
fun WearHomeScreen(
    onNavigateToMoodCheckIn: () -> Unit,
    onNavigateToMemories: () -> Unit,
    onNavigateToMore: () -> Unit,
    homeViewModel: WearHomeViewModel = koinViewModel(),
    recordingViewModel: WearRecordingViewModel = koinViewModel(),
) {
    val homeState by homeViewModel.uiState.collectAsState()
    val recordingState by recordingViewModel.uiState.collectAsState()
    val microphonePermission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) recordingViewModel.onPress()
        }

    WearHomeContent(
        homeState = homeState,
        recordingState = recordingState,
        onNavigateToMoodCheckIn = onNavigateToMoodCheckIn,
        onNavigateToMemories = onNavigateToMemories,
        onNavigateToMore = onNavigateToMore,
        onPress = recordingViewModel::onPress,
        onRelease = recordingViewModel::onRelease,
        onPauseToggle = recordingViewModel::onPauseToggle,
        onDiscard = recordingViewModel::onDiscard,
        onUndo = recordingViewModel::onUndo,
        onAllowMicrophone = { microphonePermission.launch(Manifest.permission.RECORD_AUDIO) },
    )
}

@Composable
fun WearHomeContent(
    homeState: WearHomeUiState,
    modifier: Modifier = Modifier,
    onNavigateToMoodCheckIn: () -> Unit = {},
    onNavigateToMemories: () -> Unit = {},
    onNavigateToMore: () -> Unit = {},
    onPress: () -> Unit = {},
    onRelease: () -> Unit = {},
    onPauseToggle: () -> Unit = {},
    onDiscard: () -> Unit = {},
    onUndo: () -> Unit = {},
    onAllowMicrophone: () -> Unit = {},
    recordingState: RecordingUiState = RecordingUiState(),
) {
    ScreenScaffold(
        timeText = { TimeText() },
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            RecorderStatus(homeState = homeState, recordingState = recordingState)
            RecorderControls(
                recordingState = recordingState,
                onPress = onPress,
                onRelease = onRelease,
                onPauseToggle = onPauseToggle,
                onDiscard = onDiscard,
                onNavigateToMemories = onNavigateToMemories,
                onNavigateToMoodCheckIn = onNavigateToMoodCheckIn,
            )
            RecorderFollowUp(
                recordingState = recordingState,
                onUndo = onUndo,
                onAllowMicrophone = onAllowMicrophone,
                onNavigateToMore = onNavigateToMore,
            )
        }
    }
}

/** The text above the record surface: what the recorder is doing or what to do next. */
@Composable
private fun RecorderStatus(
    homeState: WearHomeUiState,
    recordingState: RecordingUiState,
) {
    Box(
        modifier = Modifier.height(STATUS_SLOT_HEIGHT).fillMaxWidth(),
        contentAlignment = Alignment.BottomCenter,
    ) {
        RecorderStatusContent(homeState, recordingState)
    }
}

@Composable
private fun RecorderStatusContent(
    homeState: WearHomeUiState,
    recordingState: RecordingUiState,
) {
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant
    when (recordingState.phase) {
        RecordingPhase.READY -> ReadyStatus(homeState, recordingState.showGestureHint)
        RecordingPhase.STARTING -> StatusText(stringResource(R.string.wear_recorder_starting), secondary)
        RecordingPhase.RECORDING -> RecordingStatus(recordingState)
        RecordingPhase.PAUSED -> {
            val message =
                if (recordingState.pausedByInterruption) {
                    R.string.wear_recorder_paused_interrupted
                } else {
                    R.string.wear_recorder_paused
                }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = formatDuration(recordingState.recordingDurationMs),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(message),
                    style = MaterialTheme.typography.labelSmall,
                    color = secondary,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
        }
        RecordingPhase.SAVING -> StatusText(stringResource(R.string.wear_recording_saving), secondary)
        RecordingPhase.SAVED -> SavedStatus(recordingState.saveFeedback)
        RecordingPhase.TOO_SHORT -> StatusText(stringResource(R.string.wear_recording_too_short), secondary)
        RecordingPhase.ERROR -> StatusText(errorText(recordingState.error), MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun ReadyStatus(
    homeState: WearHomeUiState,
    showGestureHint: Boolean,
) {
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant
    if (showGestureHint) {
        StatusText(stringResource(R.string.wear_recorder_hint), secondary)
        return
    }
    StatusText(homeState.greeting, secondary)
    if (homeState.syncBadge == SyncBadge.NONE) return
    val (text, color) =
        when (homeState.syncBadge) {
            SyncBadge.SYNCING -> stringResource(R.string.wear_home_syncing) to secondary
            SyncBadge.ERROR -> stringResource(R.string.wear_home_sync_issue) to MaterialTheme.colorScheme.error
            SyncBadge.NONE -> "" to secondary
        }
    Text(text = text, style = MaterialTheme.typography.labelSmall, color = color)
}

@Composable
private fun RecordingStatus(recordingState: RecordingUiState) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = formatDuration(recordingState.recordingDurationMs),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        AudioWaveform(
            audioLevels = recordingState.audioLevels,
            modifier = Modifier.width(96.dp),
        )
    }
}

@Composable
private fun StatusText(
    text: String,
    color: androidx.compose.ui.graphics.Color,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = color,
        textAlign = TextAlign.Center,
        maxLines = 2,
        modifier = Modifier.padding(horizontal = STATUS_HORIZONTAL_PADDING),
    )
}

@Composable
private fun SavedStatus(feedback: SaveFeedback?) {
    val detail =
        when (feedback) {
            SaveFeedback.SYNCING_TO_PHONE -> R.string.wear_saved_detail_syncing
            SaveFeedback.SAVED_LOCALLY -> R.string.wear_saved_detail_watch_only
            null -> null
        }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = stringResource(R.string.wear_recording_saved),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        if (detail != null) {
            Text(
                text = stringResource(detail),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun errorText(error: RecordingError?): String =
    stringResource(
        when (error) {
            RecordingError.MICROPHONE_PERMISSION_DENIED -> R.string.wear_recorder_error_microphone
            RecordingError.NOT_ENOUGH_STORAGE -> R.string.wear_recorder_error_storage
            RecordingError.RECORDER_UNAVAILABLE -> R.string.wear_recorder_error_unavailable
            RecordingError.SAVE_FAILED -> R.string.wear_recorder_error_save
            null -> R.string.wear_recording_error
        },
    )

/** The record surface, with pause and discard on either side while a tap-started recording runs. */
@Composable
private fun RecorderControls(
    recordingState: RecordingUiState,
    onPress: () -> Unit,
    onRelease: () -> Unit,
    onPauseToggle: () -> Unit,
    onDiscard: () -> Unit,
    onNavigateToMemories: () -> Unit,
    onNavigateToMoodCheckIn: () -> Unit,
) {
    val phase = recordingState.phase
    val isIdle = phase == RecordingPhase.READY
    val showRecordingControls =
        recordingState.isLatched && (phase == RecordingPhase.RECORDING || phase == RecordingPhase.PAUSED)
    Row(
        modifier = Modifier.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when {
            showRecordingControls ->
                SideControl(
                    icon = if (phase == RecordingPhase.PAUSED) Icons.Default.PlayArrow else Icons.Default.Pause,
                    description =
                        stringResource(
                            if (phase == RecordingPhase.PAUSED) R.string.wear_recording_resume else R.string.wear_recording_pause,
                        ),
                    onClick = onPauseToggle,
                )
            isIdle ->
                SideControl(Icons.Default.Headphones, stringResource(R.string.wear_home_memories), onNavigateToMemories)
            else -> Spacer(Modifier.size(SIDE_CONTROL_SIZE))
        }
        RecordSurface(phase = phase, isLatched = recordingState.isLatched, onPress = onPress, onRelease = onRelease)
        when {
            showRecordingControls ->
                SideControl(Icons.Default.Close, stringResource(R.string.wear_recorder_discard), onDiscard)
            isIdle ->
                SideControl(Icons.Default.Mood, stringResource(R.string.wear_home_mood_checkin), onNavigateToMoodCheckIn)
            else -> Spacer(Modifier.size(SIDE_CONTROL_SIZE))
        }
    }
}

@Composable
private fun SideControl(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(SIDE_CONTROL_SIZE),
        colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Icon(imageVector = icon, contentDescription = description, modifier = Modifier.size(18.dp))
    }
}

/** What sits under the record surface: More when idle, Undo for a saved note, or the fix for an error. */
@Composable
private fun RecorderFollowUp(
    recordingState: RecordingUiState,
    onUndo: () -> Unit,
    onAllowMicrophone: () -> Unit,
    onNavigateToMore: () -> Unit,
) {
    Box(
        modifier = Modifier.height(FOLLOW_UP_SLOT_HEIGHT).fillMaxWidth(),
        contentAlignment = Alignment.TopCenter,
    ) {
        when {
            recordingState.phase == RecordingPhase.READY ->
                CompactButton(onClick = onNavigateToMore, label = { Text(stringResource(R.string.wear_home_more)) })
            recordingState.phase == RecordingPhase.SAVED && recordingState.undoableNoteId != null ->
                CompactButton(onClick = onUndo, label = { Text(stringResource(R.string.wear_recorder_undo)) })
            recordingState.phase == RecordingPhase.ERROR && recordingState.error == RecordingError.MICROPHONE_PERMISSION_DENIED ->
                CompactButton(onClick = onAllowMicrophone, label = { Text(stringResource(R.string.wear_onboarding_permissions_allow)) })
        }
    }
}

/**
 * The circular record control. Touch starts a recording; a quick lift latches it, a long hold saves
 * on release. Assistive services get one action that behaves like a tap.
 */
@Composable
fun RecordSurface(
    phase: RecordingPhase,
    onPress: () -> Unit,
    onRelease: () -> Unit,
    modifier: Modifier = Modifier,
    isLatched: Boolean = false,
) {
    val isRecording = phase == RecordingPhase.RECORDING
    val scale by animateFloatAsState(
        targetValue = if (isRecording) 1.1f else 1f,
        animationSpec = tween(200),
        label = "recordScale",
    )
    val color by animateColorAsState(
        targetValue =
            when (phase) {
                RecordingPhase.RECORDING -> MaterialTheme.colorScheme.primary
                RecordingPhase.SAVED -> MaterialTheme.colorScheme.primaryContainer
                RecordingPhase.ERROR -> MaterialTheme.colorScheme.errorContainer
                else -> MaterialTheme.colorScheme.surfaceContainer
            },
        animationSpec = tween(200),
        label = "recordColor",
    )
    val currentOnPress by rememberUpdatedState(onPress)
    val currentOnRelease by rememberUpdatedState(onRelease)
    val description = stringResource(recordSurfaceDescription(phase, isLatched))

    Box(
        modifier =
            modifier
                .size(RECORD_SURFACE_SIZE)
                .scale(scale)
                .clip(CircleShape)
                .background(color)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            currentOnPress()
                            tryAwaitRelease()
                            currentOnRelease()
                        },
                    )
                }.semantics(mergeDescendants = true) {
                    contentDescription = description
                    role = Role.Button
                    onClick {
                        currentOnPress()
                        currentOnRelease()
                        true
                    }
                },
        contentAlignment = Alignment.Center,
    ) {
        RecordSurfaceIcon(phase = phase, isLatched = isLatched)
    }
}

@Composable
private fun RecordSurfaceIcon(
    phase: RecordingPhase,
    isLatched: Boolean,
) {
    val isRecording = phase == RecordingPhase.RECORDING
    val (icon, tint) =
        when {
            phase == RecordingPhase.SAVED -> Icons.Default.Check to MaterialTheme.colorScheme.onPrimaryContainer
            isRecording && isLatched -> Icons.Default.Stop to MaterialTheme.colorScheme.onPrimary
            isRecording -> Icons.Default.Mic to MaterialTheme.colorScheme.onPrimary
            phase == RecordingPhase.PAUSED -> Icons.Default.Stop to MaterialTheme.colorScheme.onSurface
            else -> Icons.Default.Mic to MaterialTheme.colorScheme.onSurface
        }
    Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(32.dp), tint = tint)
}

private fun recordSurfaceDescription(
    phase: RecordingPhase,
    isLatched: Boolean,
): Int =
    when {
        phase == RecordingPhase.RECORDING && isLatched -> R.string.wear_recording_stop
        phase == RecordingPhase.PAUSED -> R.string.wear_recording_stop
        else -> R.string.wear_home_record_audio
    }
