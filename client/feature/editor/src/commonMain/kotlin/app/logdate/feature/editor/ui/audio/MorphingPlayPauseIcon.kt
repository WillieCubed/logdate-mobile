package app.logdate.feature.editor.ui.audio

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Shared animation spec for morphing icons - defined as a constant to avoid
 * recreating the spec on each recomposition.
 */
private val MorphAnimationSpec: AnimationSpec<Float> = spring(
    dampingRatio = Spring.DampingRatioLowBouncy,
    stiffness = Spring.StiffnessMediumLow
)

/**
 * A play/pause icon that gracefully morphs between the two states.
 *
 * The play triangle smoothly transforms into two pause bars through
 * path interpolation, creating a fluid transition effect.
 *
 * Uses derivedStateOf to prevent unnecessary recompositions when the parent
 * recomposes but the derived target value hasn't changed.
 *
 * @param isPlaying Whether the icon should show pause (true) or play (false)
 * @param modifier Modifier for the icon
 * @param size Size of the icon
 * @param tint Color of the icon
 */
@Composable
fun MorphingPlayPauseIcon(
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    tint: Color = Color.White
) {
    // Wrap the parameter in a State so derivedStateOf can track it
    val isPlayingState = remember { mutableStateOf(isPlaying) }.apply { value = isPlaying }

    // derivedStateOf only emits when the derived value changes
    val targetValue by remember {
        derivedStateOf { if (isPlayingState.value) 1f else 0f }
    }

    // Animate progress from 0 (play) to 1 (pause)
    val morphProgress by animateFloatAsState(
        targetValue = targetValue,
        animationSpec = MorphAnimationSpec,
        label = "PlayPauseMorph"
    )

    Canvas(modifier = modifier.size(size)) {
        drawMorphedIcon(morphProgress, tint)
    }
}

/**
 * A stop icon that morphs from play or pause state.
 *
 * @param isActive Whether the icon should show stop (true) or play (false)
 * @param modifier Modifier for the icon
 * @param size Size of the icon
 * @param tint Color of the icon
 */
@Composable
fun MorphingPlayStopIcon(
    isActive: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    tint: Color = Color.White
) {
    // Wrap the parameter in a State so derivedStateOf can track it
    val isActiveState = remember { mutableStateOf(isActive) }.apply { value = isActive }

    // derivedStateOf only emits when the derived value changes
    val targetValue by remember {
        derivedStateOf { if (isActiveState.value) 1f else 0f }
    }

    val morphProgress by animateFloatAsState(
        targetValue = targetValue,
        animationSpec = MorphAnimationSpec,
        label = "PlayStopMorph"
    )

    Canvas(modifier = modifier.size(size)) {
        drawPlayToStopMorph(morphProgress, tint)
    }
}

private fun DrawScope.drawMorphedIcon(progress: Float, tint: Color) {
    val width = size.width
    val height = size.height

    // Padding from edges (20% on each side)
    val padding = width * 0.15f
    val innerWidth = width - (padding * 2)
    val innerHeight = height - (padding * 2)

    // Gap between pause bars (relative to inner width)
    val pauseGap = innerWidth * 0.2f
    val barWidth = (innerWidth - pauseGap) / 2

    // Play triangle points (centered, pointing right)
    // Left point (apex of play button base)
    val playLeftX = padding
    val playTopY = padding
    val playBottomY = height - padding
    val playRightX = width - padding
    val playMidY = height / 2

    // Pause bar positions
    val leftBarLeft = padding
    val leftBarRight = padding + barWidth
    val rightBarLeft = padding + barWidth + pauseGap
    val rightBarRight = width - padding

    // Draw left shape (morphs from left half of triangle to left pause bar)
    val leftPath = Path().apply {
        // Top-left corner
        val topLeftX = lerp(playLeftX, leftBarLeft, progress)
        val topLeftY = lerp(playTopY, padding, progress)
        moveTo(topLeftX, topLeftY)

        // Top-right corner
        val topRightX = lerp(playRightX, leftBarRight, progress)
        val topRightY = lerp(playMidY, padding, progress)
        lineTo(topRightX, topRightY)

        // Bottom-right corner
        val bottomRightX = lerp(playRightX, leftBarRight, progress)
        val bottomRightY = lerp(playMidY, height - padding, progress)
        lineTo(bottomRightX, bottomRightY)

        // Bottom-left corner
        val bottomLeftX = lerp(playLeftX, leftBarLeft, progress)
        val bottomLeftY = lerp(playBottomY, height - padding, progress)
        lineTo(bottomLeftX, bottomLeftY)

        close()
    }

    // Draw right shape (morphs from collapsed point to right pause bar)
    // At progress 0, this is a thin sliver at the right point of the triangle
    // At progress 1, this is the full right pause bar
    val rightPath = Path().apply {
        // Calculate the squeeze factor - right shape emerges from the right point
        val squeezeProgress = progress

        // Top-left of right bar (starts at play triangle tip, ends at bar position)
        val topLeftX = lerp(playRightX - (barWidth * 0.1f), rightBarLeft, squeezeProgress)
        val topLeftY = lerp(playMidY - (innerHeight * 0.05f), padding, squeezeProgress)
        moveTo(topLeftX, topLeftY)

        // Top-right of right bar
        val topRightX = lerp(playRightX, rightBarRight, squeezeProgress)
        val topRightY = lerp(playMidY - (innerHeight * 0.05f), padding, squeezeProgress)
        lineTo(topRightX, topRightY)

        // Bottom-right of right bar
        val bottomRightX = lerp(playRightX, rightBarRight, squeezeProgress)
        val bottomRightY = lerp(playMidY + (innerHeight * 0.05f), height - padding, squeezeProgress)
        lineTo(bottomRightX, bottomRightY)

        // Bottom-left of right bar
        val bottomLeftX = lerp(playRightX - (barWidth * 0.1f), rightBarLeft, squeezeProgress)
        val bottomLeftY = lerp(playMidY + (innerHeight * 0.05f), height - padding, squeezeProgress)
        lineTo(bottomLeftX, bottomLeftY)

        close()
    }

    // Draw both shapes
    drawPath(leftPath, tint)

    // Only draw right shape when it has meaningful size
    if (progress > 0.05f) {
        drawPath(rightPath, tint)
    }
}

private fun DrawScope.drawPlayToStopMorph(progress: Float, tint: Color) {
    val width = size.width
    val height = size.height

    val padding = width * 0.15f

    // Play triangle points
    val playLeftX = padding
    val playTopY = padding
    val playBottomY = height - padding
    val playRightX = width - padding
    val playMidY = height / 2

    // Stop square corners
    val stopLeft = padding
    val stopRight = width - padding
    val stopTop = padding
    val stopBottom = height - padding

    val path = Path().apply {
        // Top-left (for play: top of left edge, for stop: top-left corner)
        val topLeftX = lerp(playLeftX, stopLeft, progress)
        val topLeftY = lerp(playTopY, stopTop, progress)
        moveTo(topLeftX, topLeftY)

        // Top-right (for play: tip of triangle, for stop: top-right corner)
        val topRightX = lerp(playRightX, stopRight, progress)
        val topRightY = lerp(playMidY, stopTop, progress)
        lineTo(topRightX, topRightY)

        // Bottom-right (for play: same as top-right (tip), for stop: bottom-right corner)
        val bottomRightX = lerp(playRightX, stopRight, progress)
        val bottomRightY = lerp(playMidY, stopBottom, progress)
        lineTo(bottomRightX, bottomRightY)

        // Bottom-left (for play: bottom of left edge, for stop: bottom-left corner)
        val bottomLeftX = lerp(playLeftX, stopLeft, progress)
        val bottomLeftY = lerp(playBottomY, stopBottom, progress)
        lineTo(bottomLeftX, bottomLeftY)

        close()
    }

    drawPath(path, tint)
}

/**
 * Linear interpolation between two float values.
 */
private fun lerp(start: Float, end: Float, fraction: Float): Float {
    return start + (end - start) * fraction
}
