package app.logdate.feature.editor.ui.audio

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.logdate.feature.editor.ui.editor.PlaybackState
import app.logdate.feature.editor.ui.editor.RecordingState

/**
 * A button that animates between play/pause/record icons and state-appropriate shapes.
 * 
 * @param playbackState Current playback state (PLAYING, PAUSED, STOPPED)
 * @param recordingState Current recording state (RECORDING, INACTIVE, etc)
 * @param onClick Callback to be invoked when the button is clicked
 * @param modifier Modifier for the button
 * @param iconSize Size of the icon in dp
 * @param colors Button colors configuration, defaults based on current state
 */
@Composable
fun AnimatedPlayPauseButton(
    playbackState: PlaybackState,
    recordingState: RecordingState = RecordingState.INACTIVE,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconSize: Dp = 24.dp,
    colors: IconButtonColors = when {
        recordingState == RecordingState.RECORDING -> IconButtonDefaults.filledIconButtonColors(
            containerColor = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError
        )
        recordingState == RecordingState.PROCESSING -> IconButtonDefaults.filledIconButtonColors(
            containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
            contentColor = MaterialTheme.colorScheme.onError
        )
        playbackState == PlaybackState.PLAYING -> IconButtonDefaults.filledIconButtonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        )
        else -> IconButtonDefaults.filledIconButtonColors(
            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
            contentColor = MaterialTheme.colorScheme.onPrimary
        )
    }
) {
    // Determine if the button is in an active state (playing or recording)
    val isActive = playbackState == PlaybackState.PLAYING || 
                  recordingState == RecordingState.RECORDING
    
    // Animate corner radius based on active state
    // Circle when inactive, rounded square when active
    val cornerRadius by animateFloatAsState(
        targetValue = if (isActive) 12f else 50f, // 50% = circle, 12 = rounded square
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "ButtonCornerRadiusAnimation"
    )
    
    // Create the shape with animated corner radius
    val shape = RoundedCornerShape(cornerRadius.dp)
    
    // Add a pulsing animation when recording
    val infiniteTransition = rememberInfiniteTransition(label = "RecordingPulseAnimation")
    val scale = if (recordingState == RecordingState.RECORDING) {
        val pulseAnimation by infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 1.1f,
            animationSpec = infiniteRepeatable(
                animation = tween(800),
                repeatMode = RepeatMode.Reverse
            ),
            label = "PulseAnimation"
        )
        pulseAnimation
    } else 1f
    
    FilledIconButton(
        onClick = onClick,
        modifier = modifier.scale(scale),
        shape = shape,
        colors = colors
    ) {
        // Determine which icon to show based on recording and playback state
        val iconAndDescription = when {
            recordingState == RecordingState.RECORDING -> Icons.Rounded.Stop to "Stop Recording"
            recordingState == RecordingState.PAUSED -> Icons.Rounded.Mic to "Resume Recording"
            recordingState == RecordingState.INACTIVE && playbackState == PlaybackState.STOPPED -> 
                Icons.Rounded.Mic to "Start Recording"
            playbackState == PlaybackState.PLAYING -> Icons.Rounded.Pause to "Pause"
            else -> Icons.Rounded.PlayArrow to "Play"
        }
        
        AnimatedContent(
            targetState = iconAndDescription,
            transitionSpec = {
                // Use a spring-based animation for smooth transitions
                fadeIn(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                ) + scaleIn(
                    initialScale = 0.8f, 
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                ) togetherWith fadeOut(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                ) + scaleOut(
                    targetScale = 0.8f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                )
            },
            label = "AudioButtonStateAnimation"
        ) { (icon, description) ->
            Icon(
                imageVector = icon,
                contentDescription = description,
                modifier = Modifier.size(iconSize)
            )
        }
    }
}

/**
 * A button that animates between play and pause icons.
 * Features a shape animation that transitions between a circle (when paused)
 * and a rounded square (when playing).
 * 
 * This simpler version is maintained for backward compatibility.
 *
 * @param isPlaying Current playback state, true for playing (pause icon), false for paused (play icon)
 * @param onClick Callback to be invoked when the button is clicked
 * @param modifier Modifier for the button
 * @param iconSize Size of the icon in dp
 * @param colors Button colors configuration, defaults to filled button with primary color
 */
@Composable
fun AnimatedPlayPauseButton(
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconSize: Dp = 24.dp,
    colors: IconButtonColors = IconButtonDefaults.filledIconButtonColors(
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary
    )
) {
    // Animate corner radius based on playing state
    val cornerRadius by animateFloatAsState(
        targetValue = if (isPlaying) 12f else 50f, // 50% = circle, 12 = rounded square
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "ButtonCornerRadiusAnimation"
    )
    
    val shape = RoundedCornerShape(cornerRadius.dp)
    
    FilledIconButton(
        onClick = onClick,
        modifier = modifier,
        shape = shape,
        colors = colors
    ) {
        AnimatedContent(
            targetState = isPlaying,
            transitionSpec = {
                fadeIn(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                ) + scaleIn(
                    initialScale = 0.8f, 
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                ) togetherWith fadeOut(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                ) + scaleOut(
                    targetScale = 0.8f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                )
            },
            label = "PlayPauseAnimation"
        ) { playing ->
            Icon(
                imageVector = if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = if (playing) "Pause" else "Play",
                modifier = Modifier.size(iconSize)
            )
        }
    }
}