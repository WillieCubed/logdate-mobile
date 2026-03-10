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
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import app.logdate.ui.theme.Spacing
import androidx.compose.ui.graphics.lerp as lerpColor
import androidx.compose.ui.util.lerp as lerpFloat

private val SIDE_PANEL_WIDTH = 280.dp

/**
 * A cross-platform immersive editor layout that provides a focused editing experience.
 *
 * Visual properties (background, side panel width, scrim alpha, content constraints)
 * vary continuously with available container width via a single [expandedFraction] driver,
 * eliminating the sudden layout jump that a binary breakpoint switch would cause.
 *
 * On narrow screens (< 600dp) and whenever an immersive block (camera, audio) is active,
 * the layout shows a full-screen dark scrim. As width grows toward 840dp the scrim fades,
 * a side panel slides in, and the background transitions to the app surface color — giving
 * a document-editor feel on tablets and desktop without any structural discontinuity.
 *
 * Uses [BoxWithConstraints] so that container width — not physical screen width — drives
 * all breakpoints. This is correct for split-screen, Stage Manager, and freeform windows.
 *
 * @param topBarContent Content for the top action bar area (back button/navigation)
 * @param editorContent The main editor content displayed in the central area
 * @param bottomContent Context content (journal selector); shown at the bottom on narrow
 *   screens and in the right-side panel on expanded screens
 * @param isImmersiveBlockActive Whether a full-screen block (camera/audio) is active;
 *   forces the narrow scrim layout regardless of container width
 * @param immersiveExitProgress Float in [0, 1] driving chrome visibility during immersive
 *   exit (0 = fully immersive, 1 = normal)
 * @param modifier Optional modifier for the root layout
 */
@Suppress("ktlint:standard:function-naming")
@Composable
fun ImmersiveEditorLayout(
    topBarContent: @Composable () -> Unit,
    editorContent: @Composable () -> Unit,
    bottomContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    isImmersiveBlockActive: Boolean = false,
    immersiveExitProgress: Float = if (isImmersiveBlockActive) 0f else 1f,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val containerWidth = maxWidth

        // 0.0 at ≤600dp, 1.0 at ≥840dp; forced to 0.0 when an immersive block is active.
        val expandedFraction =
            if (isImmersiveBlockActive) {
                0f
            } else {
                ((containerWidth.value - 600f) / (840f - 600f)).coerceIn(0f, 1f)
            }

        // Narrow max-width for centering editor content on small screens.
        val narrowMaxWidth =
            when {
                containerWidth < 600.dp -> containerWidth
                containerWidth < 900.dp -> 600.dp
                else -> 800.dp
            }

        val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

        // Content area is pushed down to clear the floating toolbar. On narrow screens
        // the offset animates with immersiveExitProgress; on wide screens it is fixed.
        val narrowTopOffset = lerp(0.dp, statusBarTop + 40.dp, immersiveExitProgress)
        val topOffset = lerp(narrowTopOffset, statusBarTop + 48.dp, expandedFraction)

        // Padding that fades in as chrome becomes visible and fades out as layout expands.
        val edgePadding = lerp(0.dp, Spacing.sm, immersiveExitProgress * (1f - expandedFraction))

        // Content width: constrained on narrow screens, relaxed on wide screens.
        val contentMaxWidth = lerp(narrowMaxWidth, 10_000.dp, expandedFraction)

        // Side panel grows from 0 to SIDE_PANEL_WIDTH as the layout expands.
        val sidePanelWidth = lerp(0.dp, SIDE_PANEL_WIDTH, expandedFraction)

        // Background lerps from dark scrim to surface color as the layout expands.
        val scrimAlpha = lerpFloat(0.72f, 0.50f, immersiveExitProgress)
        val backgroundColor =
            lerpColor(
                MaterialTheme.colorScheme.scrim.copy(alpha = scrimAlpha),
                MaterialTheme.colorScheme.surfaceContainerLowest,
                expandedFraction,
            )

        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(backgroundColor),
        ) {
            // Main content area — shifted down to clear the floating toolbar overlay.
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(top = topOffset)
                        .windowInsetsPadding(WindowInsets.ime),
            ) {
                Row(modifier = Modifier.fillMaxSize()) {
                    // Editor column — expands to fill remaining width.
                    Box(
                        modifier =
                            Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                        contentAlignment = Alignment.TopCenter,
                    ) {
                        Column(
                            modifier =
                                Modifier
                                    .fillMaxHeight()
                                    .widthIn(max = contentMaxWidth)
                                    .padding(horizontal = edgePadding)
                                    .padding(top = edgePadding),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Top,
                        ) {
                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .weight(1f)
                                        .then(
                                            if (!isImmersiveBlockActive) {
                                                Modifier.heightIn(min = 300.dp)
                                            } else {
                                                Modifier
                                            },
                                        ),
                            ) {
                                editorContent()
                            }

                            // Bottom bar — visible before the side panel takes over at 0.5.
                            AnimatedVisibility(
                                visible = expandedFraction < 0.5f && !isImmersiveBlockActive,
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

                    // Side panel — only rendered when it has non-zero width.
                    if (sidePanelWidth > 0.dp) {
                        VerticalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = expandedFraction),
                        )
                        Column(
                            modifier =
                                Modifier
                                    .width(sidePanelWidth)
                                    .fillMaxHeight()
                                    .windowInsetsPadding(WindowInsets.navigationBars)
                                    .padding(horizontal = Spacing.xl, vertical = Spacing.lg),
                            verticalArrangement = Arrangement.spacedBy(Spacing.md),
                        ) {
                            // Panel content fades in once the panel is wide enough to show it.
                            AnimatedVisibility(visible = expandedFraction >= 0.5f) {
                                bottomContent()
                            }
                        }
                    }
                }
            }

            // Floating toolbar overlay — drawn above content at the same z-order on all sizes.
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
}
