package app.logdate.feature.editor.ui.layout

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import app.logdate.ui.PlatformDimensions
import app.logdate.ui.theme.Spacing
import androidx.compose.ui.util.lerp as lerpFloat

private val EXPANDED_BREAKPOINT = 840.dp
private val SIDE_PANEL_WIDTH = 280.dp

/**
 * A cross-platform immersive editor layout that provides a focused editing experience.
 *
 * On compact and medium screens (< 840dp), or whenever an immersive block (camera,
 * audio) is active, this renders a full-screen dark scrim with the editor content
 * centered in a width-constrained column.
 *
 * On expanded screens (≥ 840dp) with no active immersive block, this switches to a
 * surface-based two-column layout: a wide main editing area on the left and a
 * context panel (journal selector, metadata) on the right. The dark scrim is removed
 * so the editor feels like a native document editor rather than a modal dialog.
 *
 * @param isEditorFocused Whether the editor currently has input focus
 * @param topBarContent Content for the top action bar area (back button/navigation)
 * @param editorContent The main editor content displayed in the central area
 * @param bottomContent Context content (journal selector); shown at the bottom on narrow
 *   screens and in the right-side panel on expanded screens
 * @param isImmersiveBlockActive Whether a full-screen block (camera/audio) is active;
 *   forces the dark scrim layout regardless of screen width
 * @param immersiveExitProgress Float in [0, 1] driving chrome visibility on narrow layouts
 *   (0 = fully immersive, 1 = normal). Unused in the expanded layout.
 * @param modifier Optional modifier for the root layout
 */
@Suppress("ktlint:standard:function-naming")
@Composable
fun ImmersiveEditorLayout(
    isEditorFocused: Boolean,
    topBarContent: @Composable () -> Unit,
    editorContent: @Composable () -> Unit,
    bottomContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    isImmersiveBlockActive: Boolean = false,
    immersiveExitProgress: Float = if (isImmersiveBlockActive) 0f else 1f,
) {
    val screenWidth = PlatformDimensions.getScreenWidth()
    val useExpandedLayout = screenWidth >= EXPANDED_BREAKPOINT && !isImmersiveBlockActive

    if (useExpandedLayout) {
        ExpandedEditorLayout(
            modifier = modifier,
            topBarContent = topBarContent,
            editorContent = editorContent,
            sideContent = bottomContent,
        )
    } else {
        NarrowEditorLayout(
            modifier = modifier,
            isEditorFocused = isEditorFocused,
            topBarContent = topBarContent,
            editorContent = editorContent,
            bottomContent = bottomContent,
            isImmersiveBlockActive = isImmersiveBlockActive,
            immersiveExitProgress = immersiveExitProgress,
            screenWidth = screenWidth,
        )
    }
}

/**
 * Expanded (tablet/desktop) editor layout.
 *
 * Uses the app surface background instead of a dark scrim. The writing area fills
 * the available width minus the [SIDE_PANEL_WIDTH] context panel. The toolbar spans
 * the full width above both panes.
 */
@Suppress("ktlint:standard:function-naming")
@Composable
private fun ExpandedEditorLayout(
    topBarContent: @Composable () -> Unit,
    editorContent: @Composable () -> Unit,
    sideContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        // Full-width toolbar row
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.CenterStart,
        ) {
            topBarContent()
        }

        // Two-pane body
        Row(
            modifier = Modifier.fillMaxSize(),
        ) {
            // Main editor pane — grows to fill remaining horizontal space
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .windowInsetsPadding(WindowInsets.ime),
            ) {
                editorContent()
            }

            // Vertical divider between editor and panel
            HorizontalDivider(
                modifier =
                    Modifier
                        .width(1.dp)
                        .fillMaxHeight(),
                color = MaterialTheme.colorScheme.outlineVariant,
            )

            // Context side panel
            Column(
                modifier =
                    Modifier
                        .width(SIDE_PANEL_WIDTH)
                        .fillMaxHeight()
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(horizontal = Spacing.xl, vertical = Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                sideContent()
            }
        }
    }
}

/**
 * Narrow (phone/medium, or any screen with an active immersive block) editor layout.
 *
 * Renders a full-screen dark scrim with editor content in a width-constrained
 * centered column. All chrome (toolbar, bottom bar) animates via [immersiveExitProgress].
 */
@Suppress("ktlint:standard:function-naming")
@Composable
private fun NarrowEditorLayout(
    isEditorFocused: Boolean,
    topBarContent: @Composable () -> Unit,
    editorContent: @Composable () -> Unit,
    bottomContent: @Composable () -> Unit,
    isImmersiveBlockActive: Boolean,
    immersiveExitProgress: Float,
    screenWidth: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
) {
    val maxWidth =
        remember(screenWidth) {
            when {
                screenWidth < 600.dp -> screenWidth
                screenWidth < 900.dp -> 600.dp
                else -> 800.dp
            }
        }

    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    val topOffset = lerp(0.dp, statusBarTop + 40.dp, immersiveExitProgress)
    val horizontalPadding = lerp(0.dp, Spacing.sm, immersiveExitProgress)
    val innerTopPadding = lerp(0.dp, Spacing.sm, immersiveExitProgress)
    val scrimAlpha = lerpFloat(0.72f, 0.50f, immersiveExitProgress)

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = scrimAlpha)),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(top = topOffset)
                    .windowInsetsPadding(WindowInsets.ime),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .widthIn(max = maxWidth)
                        .padding(horizontal = horizontalPadding)
                        .padding(top = innerTopPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Top,
            ) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .then(
                                if (!isImmersiveBlockActive) Modifier.heightIn(min = 300.dp) else Modifier,
                            ),
                ) {
                    editorContent()
                }

                AnimatedVisibility(
                    visible = !isImmersiveBlockActive,
                    enter = fadeIn(tween(200)) + expandVertically(tween(300, easing = FastOutSlowInEasing)),
                    exit = fadeOut(tween(150)) + shrinkVertically(tween(300, easing = FastOutSlowInEasing)),
                ) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .windowInsetsPadding(WindowInsets.navigationBars)
                                .padding(top = Spacing.sm, bottom = Spacing.md),
                    ) {
                        bottomContent()
                    }
                }
            }
        }

        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopStart)
                    .windowInsetsPadding(WindowInsets.statusBars),
            contentAlignment = Alignment.CenterStart,
        ) {
            topBarContent()
        }
    }
}
