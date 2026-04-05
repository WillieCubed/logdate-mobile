@file:Suppress("ktlint:standard:function-naming")

package app.logdate.ui.audio

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
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
import app.logdate.ui.theme.Spacing

/**
 * A floating mini-player bar that appears when audio is playing.
 *
 * Shows contextual title, progress, play/pause, and close controls.
 * Tapping the bar navigates to the immersive audio screen.
 *
 * @param onOpenFullPlayer Callback when the mini-player is tapped to open the full player.
 */
@Composable
fun MiniAudioPlayer(
    onOpenFullPlayer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val playbackState = LocalAudioPlaybackState.current
    val isVisible = playbackState.currentlyPlayingId != null

    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically(initialOffsetY = { it }),
        exit = slideOutVertically(targetOffsetY = { it }),
        modifier = modifier,
    ) {
        MiniAudioPlayerContent(
            playbackState = playbackState,
            onClick = onOpenFullPlayer,
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
                .fillMaxWidth()
                .clickable(onClick = onClick),
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
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
                // Play/Pause button
                IconButton(
                    onClick = {
                        if (playbackState.isPlaying) {
                            playbackState.pause()
                        } else {
                            val id = playbackState.currentlyPlayingId ?: return@IconButton
                            playbackState.play(id, "", displayInfo)
                        }
                    },
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(
                        imageVector =
                            if (playbackState.isPlaying) {
                                Icons.Default.Pause
                            } else {
                                Icons.Default.PlayArrow
                            },
                        contentDescription = if (playbackState.isPlaying) "Pause" else "Play",
                        tint = accentColor,
                    )
                }

                // Title and subtitle
                Column(
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = displayInfo.title ?: "Audio Recording",
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

                // Close button
                IconButton(
                    onClick = { playbackState.stop() },
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Stop playback",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
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
