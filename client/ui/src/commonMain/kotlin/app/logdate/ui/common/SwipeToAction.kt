package app.logdate.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * A composable that allows swiping its content to reveal an action button adjacent to it.
 * Typically used for swipe-to-delete functionality in lists.
 *
 * Features:
 * - Left-to-right swipe reveals action button that appears adjacent to content
 * - Action button grows as swipe progresses
 * - Threshold-based behavior (snap back or stay open)
 * - Animated transitions
 * - Customizable action button and content
 * - Auto-dismiss after action
 *
 * @param enabled Whether swipe actions are enabled
 * @param onAction Callback when the action button is clicked
 * @param actionLabel Composable content for the action button
 * @param content Main content that can be swiped
 * @param modifier Modifier for the container
 * @param actionBackgroundColor Background color for the action button
 * @param contentShape Shape for the main content surface
 * @param actionShape Shape for the action button
 * @param swipeThreshold Dp value that determines when the action is revealed
 * @param maxSwipe Maximum distance the content can be swiped
 * @param spacing Spacing between content and action button when revealed
 * @param animationSpec Animation specification for transitions
 */
@Composable
fun SwipeToAction(
    enabled: Boolean = true,
    onAction: () -> Unit,
    actionLabel: @Composable () -> Unit,
    content: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    actionBackgroundColor: Color = MaterialTheme.colorScheme.error,
    contentShape: Shape = MaterialTheme.shapes.extraLarge,
    actionShape: Shape = MaterialTheme.shapes.extraLarge,
    swipeThreshold: Dp = 80.dp,
    maxSwipe: Dp = 120.dp,
    spacing: Dp = 8.dp,
    animationSpec: androidx.compose.animation.core.AnimationSpec<Float> = tween(300)
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    
    // Convert dp values to pixels
    val thresholdPx = with(density) { swipeThreshold.toPx() }
    val maxSwipePx = with(density) { maxSwipe.toPx() }
    
    // Keep track of container height for equal sizing
    var containerHeightPx by remember { mutableStateOf(0) }
    val containerHeight = with(density) { containerHeightPx.toDp() }
    
    // Swipe state
    var offsetX by remember { mutableFloatStateOf(0f) }
    val animatedOffsetX = remember { Animatable(0f) }
    
    // Animation state
    var isBeingRemoved by remember { mutableStateOf(false) }
    val removeAnimation = remember { Animatable(1f) }
    
    // Calculate action button width based on swipe progress
    val actionButtonWidth by remember {
        derivedStateOf {
            val minWidth = 64.dp
            val maxWidth = 100.dp
            
            // Calculate progress for action button width
            val progress = if (abs(offsetX) > thresholdPx) {
                1f // Full width once threshold is passed
            } else {
                (abs(offsetX) / thresholdPx).coerceIn(0f, 1f) // Proportional to swipe
            }
            
            minWidth + (maxWidth - minWidth) * progress
        }
    }
    
    // Sync animated offset with current offset
    LaunchedEffect(offsetX) {
        if (!isBeingRemoved) {
            animatedOffsetX.animateTo(offsetX)
        }
    }
    
    // Handle item removal animation
    LaunchedEffect(isBeingRemoved) {
        if (isBeingRemoved) {
            // Animate content away first
            animatedOffsetX.animateTo(-maxSwipePx * 2, animationSpec)
            // Then animate container height to 0
            removeAnimation.animateTo(0f, animationSpec)
            // Finally trigger the action
            onAction()
        }
    }
    
    Box(
        modifier = modifier
            .onSizeChanged { containerHeightPx = it.height }
            // Apply height animation when removing
            .height(containerHeight * removeAnimation.value)
    ) {
        // The main row container that holds both content and action
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Main content surface - takes all available width minus action button width if visible
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .offset { IntOffset(animatedOffsetX.value.roundToInt(), 0) }
                    .pointerInput(enabled) {
                        if (enabled) {
                            detectHorizontalDragGestures(
                                onDragEnd = {
                                    scope.launch {
                                        // Snap behavior
                                        offsetX = if (abs(offsetX) < thresholdPx) {
                                            // Snap back to original position
                                            0f
                                        } else {
                                            // Stay at threshold position to show delete button
                                            -thresholdPx
                                        }
                                    }
                                }
                            ) { _, dragAmount ->
                                // Only allow left swipe and limit the distance
                                val newOffset = (offsetX + dragAmount).coerceIn(-maxSwipePx, 0f)
                                offsetX = newOffset
                            }
                        }
                    },
                shape = contentShape,
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = if (offsetX < 0) 4.dp else 0.dp
            ) {
                // Content
                content()
            }
            
            // Only show spacing and action button if we've swiped at all
            if (offsetX < 0) {
                // Spacing between content and action
                Spacer(modifier = Modifier.width(spacing))
                
                // Action button - fixed height matching the container
                Surface(
                    onClick = {
                        if (enabled) {
                            scope.launch {
                                isBeingRemoved = true
                            }
                        }
                    },
                    modifier = Modifier
                        .width(actionButtonWidth)
                        .fillMaxHeight(),
                    shape = actionShape,
                    color = actionBackgroundColor
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        actionLabel()
                    }
                }
            }
        }
    }
}