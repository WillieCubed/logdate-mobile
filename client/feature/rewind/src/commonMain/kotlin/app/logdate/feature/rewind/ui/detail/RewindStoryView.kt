@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.rewind.ui.detail

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import app.logdate.feature.rewind.ui.ReflectionPromptRewindPanelUiState
import app.logdate.feature.rewind.ui.RewindPanelUiState
import app.logdate.ui.platform.PlatformPredictiveBackHandler
import app.logdate.ui.platform.rememberScreenCornerRadius
import app.logdate.ui.platform.rememberSystemReduceMotion
import app.logdate.ui.workspace.WorkspacePlaybackLayout
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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

    var currentPanelIndex by androidx.compose.runtime.saveable
        .rememberSaveable { mutableIntStateOf(0) }
    var isPaused by remember { mutableStateOf(false) }
    var actionMenuOpen by remember { mutableStateOf(false) }
    // Tracks navigation direction for animation: true = forward, false = backward
    var navigatingForward by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val reduceMotion by rememberSystemReduceMotion()

    // Predictive back drives this screen's dismissal: [dismissProgress] tracks the live
    // gesture (0f = fully open, 1f = fully dismissed) and only the story panel itself
    // translates off the bottom of the screen — the backdrop stays put and just dims,
    // so the card reads as physically leaving rather than the whole screen sliding away.
    var storyContainerHeight by remember { mutableIntStateOf(1) }
    val dismissProgress = remember { Animatable(0f) }

    // Disabled while external chrome (reply sheet, delete dialog) is open so back presses
    // dismiss that chrome first instead of starting to drag this screen out from under it.
    PlatformPredictiveBackHandler(
        enabled = !externalPause,
        onProgress = { fraction -> scope.launch { dismissProgress.snapTo(fraction) } },
        onBack = {
            scope.launch { dismissProgress.snapTo(1f) }
            onExit()
        },
        onCancel = {
            scope.launch {
                if (reduceMotion) {
                    dismissProgress.snapTo(0f)
                } else {
                    dismissProgress.animateTo(0f, tween(300, easing = FastOutSlowInEasing))
                }
            }
        },
    )

    // Panel corners are concentric with the physical display: the same center point as the
    // screen's own rounded corners, so the small inset reads as reveal rather than an
    // arbitrarily-chosen radius sitting inside a squared-off screen.
    val screenCornerRadius = rememberScreenCornerRadius()
    val storyPanelShape =
        remember(screenCornerRadius) {
            RoundedCornerShape((screenCornerRadius - rewindStoryPanelInset).coerceAtLeast(0.dp))
        }

    // First-view entrance animation: scale up from 0.92 with a spring, then settle.
    // Only plays once when the story first mounts, and only for unviewed rewinds.
    val entranceScale = remember { Animatable(if (isFirstView && !reduceMotion) 0.92f else 1f) }

    LaunchedEffect(isFirstView, reduceMotion) {
        if (isFirstView) {
            if (reduceMotion) {
                entranceScale.snapTo(1f)
                onFirstViewConsumed?.invoke()
                return@LaunchedEffect
            }
            // Brief pause so the user registers the screen before the spring fires
            delay(150)
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
    // The story stays paused whenever the user is interacting with chrome that lives outside
    // this composable (the share sheet, the reply sheet) so its contents don't tick away
    // while attention is elsewhere.
    val effectivelyPaused = isPaused || actionMenuOpen || externalPause || reduceMotion

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

    fun goToPreviousPanel() {
        scope.launch {
            progressAnimatable.stop()
            if (currentPanelIndex > 0) {
                navigatingForward = false
                currentPanelIndex--
            }
        }
    }

    fun goToNextPanel() {
        scope.launch {
            progressAnimatable.stop()
            if (currentPanelIndex < panels.size - 1) {
                navigatingForward = true
                currentPanelIndex++
            } else {
                if (onComplete != null) onComplete() else onExit()
            }
        }
    }

    @Composable
    fun StoryPanel(modifier: Modifier = Modifier) {
        AnimatedContent(
            targetState = currentPanelIndex,
            transitionSpec = {
                if (reduceMotion) {
                    fadeIn(tween(150)).togetherWith(fadeOut(tween(150)))
                } else if (navigatingForward) {
                    (slideInHorizontally { width -> width / 4 } + fadeIn(tween(300)))
                        .togetherWith(slideOutHorizontally { width -> -width / 4 } + fadeOut(tween(300)))
                } else {
                    (slideInHorizontally { width -> -width / 4 } + fadeIn(tween(300)))
                        .togetherWith(slideOutHorizontally { width -> width / 4 } + fadeOut(tween(300)))
                }
            },
            label = "PanelTransition",
            modifier =
                modifier
                    .graphicsLayer {
                        // A little scale-down and fade alongside the translation, the way
                        // iOS's own interactive dismiss transitions read — not just a flat
                        // slide.
                        val eased = easeDismiss(dismissProgress.value)
                        translationY = if (reduceMotion) 0f else eased * storyContainerHeight
                        val scale = if (reduceMotion) 1f else 1f - eased * DISMISS_CARD_MAX_SCALE_DOWN
                        scaleX = scale
                        scaleY = scale
                        alpha = 1f - eased * DISMISS_CARD_MAX_FADE
                    }.padding(rewindStoryPanelInset)
                    .clip(storyPanelShape),
        ) { panelIndex ->
            content(panels[panelIndex])
        }
    }

    @Composable
    fun TapNavigationLayer(
        modifier: Modifier = Modifier,
        content: @Composable () -> Unit,
    ) {
        Box(
            modifier =
                modifier.pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            isPaused = true
                            try {
                                awaitRelease()
                            } finally {
                                isPaused = false
                            }
                        },
                        onTap = { offset ->
                            if (offset.x < size.width / 2) {
                                goToPreviousPanel()
                            } else {
                                goToNextPanel()
                            }
                        },
                    )
                },
            content = { content() },
        )
    }

    Box(
        modifier =
            modifier
                .onSizeChanged { storyContainerHeight = it.height.coerceAtLeast(1) }
                .graphicsLayer {
                    // Only the entrance animation scales this container. Dismiss never
                    // translates or scales it — the backdrop stays fixed and just dims,
                    // while the story panel above carries all of the dismiss motion.
                    scaleX = entranceScale.value
                    scaleY = entranceScale.value
                    alpha = 1f - easeDismiss(dismissProgress.value)
                }.background(Color.Black)
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
        WorkspacePlaybackLayout(
            focus = {
                TapNavigationLayer(Modifier.fillMaxSize()) {
                    StoryPanel(Modifier.fillMaxSize())
                }
            },
            controls = {
                RewindStoryChrome(
                    activePanel = panels[currentPanelIndex],
                    totalPanels = panels.size,
                    currentPanelIndex = currentPanelIndex,
                    currentPanelProgress = autoAdvanceProgress,
                    accentColor = accentColor,
                    onPause = { isPaused = true },
                    onExit = onExit,
                    onMenuPauseChange = { actionMenuOpen = it },
                    onSharePanel = onSharePanel,
                    onShareRewindStats = onShareRewindStats,
                    onReplyToPrompt = onReplyToPrompt,
                    onDeleteRewind = onDeleteRewind,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .graphicsLayer { alpha = 1f - easeDismiss(dismissProgress.value) }
                            .windowInsetsPadding(WindowInsets.safeDrawing)
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                )
            },
        )
    }
}

// Small enough to keep the story feeling edge-to-edge and immersive; just enough for the
// concentric-corner reveal at the panel's edges to register.
private val rewindStoryPanelInset = 4.dp

// A mild ease-in: the gesture reads as slightly softened rather than raw 1:1 finger
// tracking, without the long dead zone a stronger curve gives at the very start.
private val dismissEasing = CubicBezierEasing(0.2f, 0f, 1f, 1f)
private const val DISMISS_CARD_MAX_SCALE_DOWN = 0.14f
private const val DISMISS_CARD_MAX_FADE = 0.15f

private fun easeDismiss(progress: Float): Float = dismissEasing.transform(progress.coerceIn(0f, 1f))
