@file:Suppress("ktlint:standard:function-naming")

package app.logdate.ui.audio

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.logdate.ui.media.MediaDeviceSelector
import app.logdate.ui.theme.Spacing
import kotlin.uuid.Uuid

/**
 * A floating mini-player bar that appears when audio is playing.
 *
 * Shows contextual title, progress, play/pause, and close controls.
 * Tapping the bar navigates to the immersive audio screen.
 *
 * @param onOpenFullPlayer Callback invoked with the playing note's ID when the user taps the bar.
 */
@Composable
fun MiniAudioPlayer(
    onOpenFullPlayer: (Uuid) -> Unit,
    modifier: Modifier = Modifier,
) {
    val playbackState = LocalAudioPlaybackState.current
    val currentId = playbackState.currentlyPlayingId

    AnimatedVisibility(
        visible = currentId != null,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier,
    ) {
        MiniAudioPlayerContent(
            playbackState = playbackState,
            onClick = { currentId?.let(onOpenFullPlayer) },
        )
    }
}

@Composable
private fun MiniAudioPlayerContent(
    playbackState: AudioPlaybackState,
    onClick: () -> Unit,
) {
    val displayInfo = playbackState.displayInfo
    val accentColor =
        displayInfo.accentColor?.let { Color(it) }
            ?: MaterialTheme.colorScheme.primary

    val animatedProgress by animateFloatAsState(
        targetValue = playbackState.progress,
        animationSpec = tween(durationMillis = 100),
        label = "MiniPlayerProgress",
    )

    Card(
        shape = RoundedCornerShape(16.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        modifier =
            Modifier
                .widthIn(max = 480.dp)
                .fillMaxWidth()
                .clickable(onClick = onClick),
    ) {
        Column {
            BoxWithConstraints(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(
                            start = Spacing.md,
                            top = Spacing.sm,
                            bottom = Spacing.xs,
                            end = Spacing.xs,
                        ),
            ) {
                val stackRouteControls = maxWidth < 360.dp
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        IconButton(
                            onClick = {
                                if (playbackState.isPlaying) {
                                    playbackState.pause()
                                } else {
                                    val id = playbackState.currentlyPlayingId ?: return@IconButton
                                    val uri = playbackState.currentUri ?: return@IconButton
                                    playbackState.play(id, uri, displayInfo)
                                }
                            },
                            modifier = Modifier.size(40.dp),
                        ) {
                            Icon(
                                imageVector =
                                    if (playbackState.isPlaying) {
                                        Icons.Rounded.Pause
                                    } else {
                                        Icons.Rounded.PlayArrow
                                    },
                                contentDescription = if (playbackState.isPlaying) "Pause" else "Play",
                                tint = accentColor,
                            )
                        }

                        Column(
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(
                                text = displayInfo.title ?: displayInfo.subtitle ?: "",
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (displayInfo.subtitle != null) {
                                Text(
                                    text = displayInfo.subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }

                        if (!stackRouteControls) {
                            MediaDeviceSelector(
                                selection = playbackState.outputSelection,
                                onDeviceSelected = playbackState.selectOutputDevice,
                                label = "Audio output",
                                modifier = Modifier.widthIn(max = 180.dp),
                            )
                        }

                        IconButton(
                            onClick = { playbackState.stop() },
                            modifier = Modifier.size(36.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Stop playback",
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    if (stackRouteControls) {
                        MediaDeviceSelector(
                            selection = playbackState.outputSelection,
                            onDeviceSelected = playbackState.selectOutputDevice,
                            label = "Audio output",
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            // Progress bar along the bottom
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.md)
                        .padding(bottom = Spacing.sm),
            ) {
                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .clip(RoundedCornerShape(1.5.dp)),
                    color = accentColor,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    strokeCap = StrokeCap.Round,
                )
            }
        }
    }
}
