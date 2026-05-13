@file:Suppress("ktlint:standard:function-naming", "ktlint:standard:no-wildcard-imports")

package app.logdate.feature.rewind.ui.detail

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import app.logdate.feature.rewind.ui.ReflectionPromptRewindPanelUiState
import app.logdate.feature.rewind.ui.RewindPanelUiState
import app.logdate.ui.platform.PlatformIcons
import app.logdate.ui.platform.rememberSystemReduceMotion
import kotlinx.coroutines.delay
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
 * @param onExit Callback invoked when the user exits the story view (e.g., close
 *   button, or completion when [onComplete] is not provided)
 * @param modifier Modifier for customizing layout
 * @param isFirstView True when the user has never opened this rewind before. Enables
 *   a scale-up spring entrance and a single haptic pulse on mount.
 * @param onFirstViewConsumed Invoked after the entrance animation finishes so the
 *   host can clear its one-shot first-view flag. Without this, rotating the device
 *   mid-story would replay the celebration.
 * @param restartKey Bumping this value rewinds the story back to the first panel —
 *   used by the "Watch again" action in the post-viewing sheet.
 * @param externalPause Pauses auto-advance and the progress animation. Used while the
 *   reply sheet, delete dialog, or post-viewing sheet is visible.
 * @param autoAdvanceDelayMs Time in milliseconds before auto-advancing to next panel
 * @param accentColor Foreground tint applied to story chrome (progress bars, icons)
 * @param onComplete Invoked when the last panel finishes instead of [onExit]. When
 *   null, completion falls back to [onExit]. The host uses this to show a post-viewing
 *   sheet without popping the screen.
 * @param onSharePanel Callback invoked when the user taps share for the current panel
 * @param onShareRewindStats Callback invoked when the user taps share for the overall
 *   rewind summary card
 * @param onReplyToPrompt Callback invoked when the user taps reply on a reflection
 *   prompt panel
 * @param onDeleteRewind Callback invoked when the user requests deletion from the
 *   story chrome
 * @param content Composable content renderer for each panel
 */
@Composable
fun RewindStoryView(
    panels: List<RewindPanelUiState>,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
    isFirstView: Boolean = false,
    onFirstViewConsumed: (() -> Unit)? = null,
    restartKey: Int = 0,
    externalPause: Boolean = false,
    autoAdvanceDelayMs: Long = 5000L,
    accentColor: Color = Color.White,
    onComplete: (() -> Unit)? = null,
    onSharePanel: ((panel: RewindPanelUiState) -> Unit)? = null,
    onShareRewindStats: (() -> Unit)? = null,
    onReplyToPrompt: ((panel: ReflectionPromptRewindPanelUiState) -> Unit)? = null,
    onDeleteRewind: (() -> Unit)? = null,
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

    // First-view entrance animation: scale up from 0.92 with a spring, then settle.
    // Only plays once when the story first mounts, and only for unviewed rewinds.
    val entranceScale = remember { Animatable(if (isFirstView) 0.92f else 1f) }
    val hapticFeedback = LocalHapticFeedback.current

    LaunchedEffect(isFirstView) {
        if (isFirstView) {
            // Brief pause so the user registers the screen before the spring fires
            delay(150)
            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
            entranceScale.animateTo(
                targetValue = 1f,
                animationSpec =
                    spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow,
                    ),
            )
            onFirstViewConsumed?.invoke()
        }
    }

    // Auto-advance progress for current panel
    var autoAdvanceProgress by remember { mutableFloatStateOf(0f) }
    val progressAnimatable = remember { Animatable(0f) }

    // Watch-again: reset to the first panel when the host bumps restartKey.
    // Skip the initial composition so we don't fight the entrance animation.
    LaunchedEffect(restartKey) {
        if (restartKey > 0) {
            currentPanelIndex = 0
            navigatingForward = true
            progressAnimatable.snapTo(0f)
        }
    }

    // Auto-advance logic that respects pause state
    LaunchedEffect(currentPanelIndex) {
        progressAnimatable.snapTo(0f)
        autoAdvanceProgress = 0f
    }

    // Respect the OS "reduce motion" setting — auto-advancing a story panel
    // every few seconds feels like the screen is moving without consent for
    // users with vestibular sensitivities or who simply prefer manual paging.
    // The story still renders normally; the user advances by tapping.
    val reduceMotion by rememberSystemReduceMotion()

    // The story stays paused whenever the user is interacting with chrome that lives outside
    // this composable (the share sheet, the reply sheet) so its contents don't tick away
    // while attention is elsewhere.
    val effectivelyPaused = isPaused || externalPause || reduceMotion

    LaunchedEffect(currentPanelIndex, effectivelyPaused) {
        if (effectivelyPaused) {
            progressAnimatable.stop()
            return@LaunchedEffect
        }

        // Give the entrance animation time to play on the first panel of a first view
        if (isFirstView && currentPanelIndex == 0) {
            delay(500)
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
        } else if (onComplete != null) {
            onComplete()
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
                .graphicsLayer {
                    scaleX = entranceScale.value
                    scaleY = entranceScale.value
                }.background(Color.Black)
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
                                        if (onComplete != null) onComplete() else onExit()
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
                color = accentColor,
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

                val activePanel = panels[currentPanelIndex]
                if (
                    onReplyToPrompt != null &&
                    activePanel is ReflectionPromptRewindPanelUiState &&
                    activePanel.repliesAllowed
                ) {
                    IconButton(
                        onClick = {
                            isPaused = true
                            onReplyToPrompt(activePanel)
                        },
                    ) {
                        Icon(
                            painter = PlatformIcons.reply(),
                            contentDescription =
                                stringResource(
                                    if (activePanel.existingResponse != null) {
                                        Res.string.reflection_prompt_reply_edit
                                    } else {
                                        Res.string.reflection_prompt_reply
                                    },
                                ),
                            tint = Color.White,
                        )
                    }
                }

                if (onShareRewindStats != null) {
                    IconButton(
                        onClick = {
                            isPaused = true
                            onShareRewindStats()
                        },
                    ) {
                        Icon(
                            painter = PlatformIcons.photoLibrary(),
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
                            painter = PlatformIcons.share(),
                            contentDescription = stringResource(Res.string.share_rewind_panel),
                            tint = Color.White,
                        )
                    }
                }

                if (onDeleteRewind != null) {
                    IconButton(
                        onClick = {
                            isPaused = true
                            onDeleteRewind()
                        },
                    ) {
                        Icon(
                            painter = PlatformIcons.delete(),
                            contentDescription = stringResource(Res.string.delete_rewind),
                            tint = Color.White,
                        )
                    }
                }

                IconButton(onClick = onExit) {
                    Icon(
                        painter = PlatformIcons.close(),
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
                                            if (onComplete != null) onComplete() else onExit()
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
    color: Color,
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
                color = color,
                trackColor = color.copy(alpha = 0.3f),
            )
        }
    }
}
