@file:Suppress("ktlint:standard:function-naming", "ktlint:standard:no-wildcard-imports")

package app.logdate.feature.rewind.ui.detail

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import app.logdate.feature.rewind.ui.RewindPanelUiState
import kotlinx.coroutines.launch
import logdate.client.feature.rewind.generated.resources.*
import logdate.client.feature.rewind.generated.resources.Res
import org.jetbrains.compose.resources.stringResource
import kotlin.math.abs

/**
 * A full-screen Instagram Stories-like interface for viewing rewind content.
 *
 * This composable provides an immersive viewing experience with:
 * - Full-screen content panels that users can swipe through
 * - Progress indicators showing position in the story sequence
 * - Tap-to-advance navigation (left/right sides of screen)
 * - Horizontal swipe gestures for panel navigation
 * - Auto-advance functionality with configurable timing
 *
 * ## UX Design:
 * - **Immersive**: Uses full screen with status bar overlay
 * - **Intuitive Navigation**: Familiar Instagram Stories interaction patterns
 * - **Progress Feedback**: Clear visual indicators of story position
 * - **Flexible Content**: Supports any composable content via slot pattern
 *
 * ## Interaction Model:
 * - **Tap Left**: Go to previous panel
 * - **Tap Right**: Go to next panel
 * - **Swipe Left**: Go to next panel
 * - **Swipe Right**: Go to previous panel
 * - **Auto-advance**: Automatically moves to next panel after delay
 *
 * @param panels List of story panels to display
 * @param onExit Callback invoked when user exits the story view
 * @param autoAdvanceDelayMs Time in milliseconds before auto-advancing to next panel
 * @param content Composable content renderer for each panel
 */
@Composable
fun RewindStoryView(
    panels: List<RewindPanelUiState>,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
    onSharePanel: ((panel: RewindPanelUiState) -> Unit)? = null,
    onShareRewindStats: (() -> Unit)? = null,
    autoAdvanceDelayMs: Long = 5000L,
    content: @Composable (panel: RewindPanelUiState) -> Unit,
) {
    if (panels.isEmpty()) {
        LaunchedEffect(Unit) {
            onExit()
        }
        return
    }

    var currentPanelIndex by remember { mutableIntStateOf(0) }
    var isPaused by remember { mutableStateOf(false) }
    // Tracks navigation direction for animation: true = forward, false = backward
    var navigatingForward by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    // Auto-advance progress for current panel
    var autoAdvanceProgress by remember { mutableFloatStateOf(0f) }
    val progressAnimatable = remember { Animatable(0f) }

    // Auto-advance logic that respects pause state
    LaunchedEffect(currentPanelIndex) {
        progressAnimatable.snapTo(0f)
        autoAdvanceProgress = 0f
    }

    LaunchedEffect(currentPanelIndex, isPaused) {
        if (isPaused) {
            progressAnimatable.stop()
            return@LaunchedEffect
        }

        // Resume or start from current progress
        val currentProgress = progressAnimatable.value
        val remainingFraction = 1f - currentProgress
        if (remainingFraction <= 0f) return@LaunchedEffect

        progressAnimatable.animateTo(
            targetValue = 1f,
            animationSpec =
                tween(
                    durationMillis = (autoAdvanceDelayMs * remainingFraction).toInt(),
                    easing = LinearEasing,
                ),
        )

        // Auto-advance to next panel
        if (currentPanelIndex < panels.size - 1) {
            navigatingForward = true
            currentPanelIndex++
        } else {
            onExit()
        }
    }

    // Update progress state
    LaunchedEffect(progressAnimatable.value) {
        autoAdvanceProgress = progressAnimatable.value
    }

    Box(
        modifier =
            modifier
                .background(Color.Black)
                .statusBarsPadding()
                // Swipe gesture with accumulated drag distance
                .pointerInput(Unit) {
                    var accumulatedDrag = 0f
                    var swipeHandled = false

                    detectHorizontalDragGestures(
                        onDragStart = {
                            accumulatedDrag = 0f
                            swipeHandled = false
                        },
                        onDragEnd = {
                            accumulatedDrag = 0f
                            swipeHandled = false
                        },
                        onDragCancel = {
                            accumulatedDrag = 0f
                            swipeHandled = false
                        },
                        onHorizontalDrag = { _, dragAmount ->
                            accumulatedDrag += dragAmount
                            val swipeThreshold = with(density) { 50.dp.toPx() }

                            if (!swipeHandled && abs(accumulatedDrag) > swipeThreshold) {
                                swipeHandled = true
                                scope.launch {
                                    progressAnimatable.stop()

                                    if (accumulatedDrag > 0 && currentPanelIndex > 0) {
                                        navigatingForward = false
                                        currentPanelIndex--
                                    } else if (accumulatedDrag < 0 && currentPanelIndex < panels.size - 1) {
                                        navigatingForward = true
                                        currentPanelIndex++
                                    } else if (accumulatedDrag < 0 && currentPanelIndex == panels.size - 1) {
                                        onExit()
                                    }
                                }
                            }
                        },
                    )
                },
    ) {
        // Main content area with animated transitions
        AnimatedContent(
            targetState = currentPanelIndex,
            transitionSpec = {
                if (navigatingForward) {
                    (slideInHorizontally { width -> width / 4 } + fadeIn(tween(300)))
                        .togetherWith(slideOutHorizontally { width -> -width / 4 } + fadeOut(tween(300)))
                } else {
                    (slideInHorizontally { width -> -width / 4 } + fadeIn(tween(300)))
                        .togetherWith(slideOutHorizontally { width -> width / 4 } + fadeOut(tween(300)))
                }
            },
            label = "PanelTransition",
            modifier = Modifier.fillMaxSize(),
        ) { panelIndex ->
            content(panels[panelIndex])
        }

        // Top overlay with progress indicators and controls
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            StoryProgressIndicators(
                totalPanels = panels.size,
                currentPanelIndex = currentPanelIndex,
                currentPanelProgress = autoAdvanceProgress,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.weight(1f))

                if (onShareRewindStats != null) {
                    IconButton(
                        onClick = {
                            isPaused = true
                            onShareRewindStats()
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhotoLibrary,
                            contentDescription = stringResource(Res.string.share_rewind_stats),
                            tint = Color.White,
                        )
                    }
                }

                if (onSharePanel != null) {
                    IconButton(
                        onClick = {
                            isPaused = true
                            onSharePanel(panels[currentPanelIndex])
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Default.IosShare,
                            contentDescription = stringResource(Res.string.share_rewind_panel),
                            tint = Color.White,
                        )
                    }
                }

                IconButton(onClick = onExit) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(Res.string.close_rewind),
                        tint = Color.White,
                    )
                }
            }
        }

        // Tap and long-press overlay for navigation and pause
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                // Long press detection: pause while pressed
                                isPaused = true
                                try {
                                    awaitRelease()
                                } finally {
                                    isPaused = false
                                }
                            },
                            onTap = { offset ->
                                scope.launch {
                                    progressAnimatable.stop()
                                    if (offset.x < size.width / 2) {
                                        // Left half: previous
                                        if (currentPanelIndex > 0) {
                                            navigatingForward = false
                                            currentPanelIndex--
                                        }
                                    } else {
                                        // Right half: next
                                        if (currentPanelIndex < panels.size - 1) {
                                            navigatingForward = true
                                            currentPanelIndex++
                                        } else {
                                            onExit()
                                        }
                                    }
                                }
                            },
                        )
                    },
        )
    }
}

/**
 * Progress indicators showing the current position in the story sequence.
 *
 * Displays a row of progress bars, one for each panel in the story. The current
 * panel shows an animated progress bar, completed panels are filled, and future
 * panels remain empty.
 *
 * ## Visual Design:
 * - **Completed panels**: Fully filled white progress bars
 * - **Current panel**: Animated progress bar showing auto-advance timing
 * - **Future panels**: Empty/unfilled progress bars
 * - **Spacing**: 2dp gaps between progress bars for clarity
 *
 * @param totalPanels Total number of panels in the story
 * @param currentPanelIndex Zero-based index of the currently displayed panel
 * @param currentPanelProgress Progress of current panel (0.0 to 1.0)
 * @param modifier Modifier for customizing the progress indicators container
 */
@Composable
private fun StoryProgressIndicators(
    totalPanels: Int,
    currentPanelIndex: Int,
    currentPanelProgress: Float,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        repeat(totalPanels) { index ->
            val progress =
                when {
                    index < currentPanelIndex -> 1f
                    index == currentPanelIndex -> currentPanelProgress
                    else -> 0f
                }

            LinearProgressIndicator(
                progress = { progress },
                modifier =
                    Modifier
                        .weight(1f)
                        .height(3.dp)
                        .clip(RoundedCornerShape(1.5.dp)),
                color = Color.White,
                trackColor = Color.White.copy(alpha = 0.3f),
            )
        }
    }
}
