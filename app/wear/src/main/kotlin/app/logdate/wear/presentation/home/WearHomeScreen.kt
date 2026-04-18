package app.logdate.wear.presentation.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Mood
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.ViewTimeline
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
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
import app.logdate.wear.presentation.common.SaveFeedback
import app.logdate.wear.presentation.recording.RecordingPhase
import app.logdate.wear.presentation.recording.RecordingUiState
import app.logdate.wear.presentation.recording.WearRecordingViewModel
import app.logdate.wear.presentation.recording.formatDuration
import kotlinx.coroutines.flow.collectLatest
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun WearHomeScreen(
    onNavigateToMoodCheckIn: () -> Unit,
    onNavigateToQuickText: () -> Unit,
    onNavigateToTimeline: () -> Unit,
    onNavigateToSettings: () -> Unit,
    homeViewModel: WearHomeViewModel = koinViewModel(),
    recordingViewModel: WearRecordingViewModel = koinViewModel(),
) {
    val homeState by homeViewModel.uiState.collectAsState()
    val recordingState by recordingViewModel.uiState.collectAsState()

    // Auto-save when recording pauses (hold-to-record-and-save)
    LaunchedEffect(recordingState.phase) {
        if (recordingState.phase == RecordingPhase.PAUSED) {
            recordingViewModel.save()
        }
    }

    // Reset after saved display completes (instead of navigating back)
    LaunchedEffect(Unit) {
        recordingViewModel.events.collectLatest {
            recordingViewModel.onNavigatedBack()
        }
    }

    WearHomeContent(
        homeState = homeState,
        recordingState = recordingState,
        onNavigateToMoodCheckIn = onNavigateToMoodCheckIn,
        onNavigateToQuickText = onNavigateToQuickText,
        onNavigateToTimeline = onNavigateToTimeline,
        onNavigateToSettings = onNavigateToSettings,
        onTouchDown = recordingViewModel::onTouchDown,
        onTouchUp = recordingViewModel::onTouchUp,
    )
}

@Composable
fun WearHomeContent(
    homeState: WearHomeUiState,
    modifier: Modifier = Modifier,
    onNavigateToMoodCheckIn: () -> Unit = {},
    onNavigateToQuickText: () -> Unit = {},
    onNavigateToTimeline: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onTouchDown: () -> Unit = {},
    onTouchUp: () -> Unit = {},
    recordingState: RecordingUiState = RecordingUiState(),
) {
    val isIdle = recordingState.phase == RecordingPhase.READY

    ScreenScaffold(
        timeText = { TimeText() },
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                // Top text — greeting or recording status
                when (recordingState.phase) {
                    RecordingPhase.READY -> {
                        Text(
                            text = homeState.greeting,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                        if (homeState.syncBadge != SyncBadge.NONE) {
                            val (badgeText, badgeColor) =
                                when (homeState.syncBadge) {
                                    SyncBadge.SYNCING ->
                                        stringResource(R.string.wear_home_syncing) to
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                    SyncBadge.ERROR ->
                                        stringResource(R.string.wear_home_sync_issue) to
                                            MaterialTheme.colorScheme.error
                                    SyncBadge.NONE -> "" to MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            Text(
                                text = badgeText,
                                style = MaterialTheme.typography.labelSmall,
                                color = badgeColor,
                            )
                        }
                    }

                    RecordingPhase.RECORDING -> {
                        Text(
                            text = formatDuration(recordingState.recordingDurationMs),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                        )
                    }

                    RecordingPhase.SAVING, RecordingPhase.PAUSED -> {
                        Text(
                            text = stringResource(R.string.wear_recording_saving),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }

                    RecordingPhase.SAVED -> {
                        val feedbackText =
                            when (recordingState.saveFeedback) {
                                SaveFeedback.SYNCING_TO_PHONE -> stringResource(R.string.wear_saved_syncing_to_phone)
                                SaveFeedback.SAVED_LOCALLY -> stringResource(R.string.wear_saved_on_watch)
                                null -> stringResource(R.string.wear_recording_saved)
                            }
                        Text(
                            text = feedbackText,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center,
                        )
                    }

                    RecordingPhase.TOO_SHORT -> {
                        Text(
                            text = stringResource(R.string.wear_recording_hold_longer),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }

                    RecordingPhase.ERROR -> {
                        Text(
                            text =
                                recordingState.errorMessage
                                    ?: stringResource(R.string.wear_recording_error),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                        )
                    }
                }

                // Center surface — the recorder
                RecordSurface(
                    phase = recordingState.phase,
                    onTouchDown = onTouchDown,
                    onTouchUp = onTouchUp,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }

            // Bottom action row — hidden during recording
            val bottomAlpha by animateFloatAsState(
                targetValue = if (isIdle) 1f else 0f,
                animationSpec = tween(150),
                label = "bottomAlpha",
            )
            if (bottomAlpha > 0f) {
                Row(
                    modifier =
                        Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 16.dp)
                            .alpha(bottomAlpha),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    IconButton(
                        onClick = onNavigateToMoodCheckIn,
                        modifier = Modifier.size(36.dp),
                        colors =
                            IconButtonDefaults.iconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                            ),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mood,
                            contentDescription = stringResource(R.string.wear_home_mood_checkin),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    IconButton(
                        onClick = onNavigateToQuickText,
                        modifier = Modifier.size(36.dp),
                        colors =
                            IconButtonDefaults.iconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                            ),
                    ) {
                        Icon(
                            imageVector = Icons.Default.TextFields,
                            contentDescription = stringResource(R.string.wear_home_quick_text),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    IconButton(
                        onClick = onNavigateToTimeline,
                        modifier = Modifier.size(36.dp),
                        colors =
                            IconButtonDefaults.iconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                            ),
                    ) {
                        Icon(
                            imageVector = Icons.Default.ViewTimeline,
                            contentDescription = stringResource(R.string.wear_home_timeline),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    IconButton(
                        onClick = onNavigateToSettings,
                        modifier = Modifier.size(36.dp),
                        colors =
                            IconButtonDefaults.iconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                            ),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = stringResource(R.string.wear_home_settings),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RecordSurface(
    phase: RecordingPhase,
    onTouchDown: () -> Unit,
    onTouchUp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isRecording = phase == RecordingPhase.RECORDING
    val scale by animateFloatAsState(
        targetValue = if (isRecording) 1.15f else 1f,
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

    Box(
        modifier =
            modifier
                .size(80.dp)
                .scale(scale)
                .clip(CircleShape)
                .background(color)
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            when (event.type) {
                                PointerEventType.Press -> onTouchDown()
                                PointerEventType.Release -> onTouchUp()
                            }
                        }
                    }
                },
        contentAlignment = Alignment.Center,
    ) {
        when (phase) {
            RecordingPhase.SAVED -> {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }

            else -> {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = stringResource(R.string.wear_home_record_audio),
                    modifier = Modifier.size(32.dp),
                    tint =
                        if (isRecording) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                )
            }
        }
    }
}
