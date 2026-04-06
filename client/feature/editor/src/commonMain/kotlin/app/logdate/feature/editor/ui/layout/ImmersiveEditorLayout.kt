package app.logdate.feature.editor.ui.layout

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import app.logdate.ui.theme.Spacing

/**
 * Signals to editor components nested anywhere beneath [ImmersiveEditorLayout] that the
 * available vertical space is too small to render full-size controls. Components should
 * switch to compact variants (e.g. chip instead of card) when this is `true`.
 */
internal val LocalEditorIsCompact = compositionLocalOf { false }

/**
 * A cross-platform immersive editor layout that provides a focused editing experience.
 *
 * Renders a full-screen dark scrim with editor content centered in a width-constrained
 * column. Context content ([bottomContent]) is always shown below the editor and animates
 * away while an immersive block (camera, audio) is active.
 *
 * Uses [BoxWithConstraints] so breakpoints respond to the container's available width
 * rather than physical screen size, which is correct for split-screen and freeform windows.
 *
 * @param topBarContent Content for the top action bar area (back button/navigation)
 * @param editorContent The main editor content displayed in the central area
 * @param bottomContent Context content (journal selector) shown below the editor
 * @param isImmersiveBlockActive Whether a full-screen block (camera/audio) is active;
 *   hides [bottomContent] and collapses chrome
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
        // Landscape phones are typically 360–430dp tall; portrait phones start at 667dp.
        val isCompact = maxHeight < 500.dp
        val maxEditorWidth =
            when {
                containerWidth < 600.dp -> containerWidth
                containerWidth < 900.dp -> 600.dp
                else -> 800.dp
            }

        val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val topOffset = lerp(0.dp, statusBarTop + 40.dp, immersiveExitProgress)
        val horizontalPadding = lerp(0.dp, Spacing.sm, immersiveExitProgress)
        val innerTopPadding = lerp(0.dp, Spacing.sm, immersiveExitProgress)
        CompositionLocalProvider(LocalEditorIsCompact provides isCompact) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceDim),
            ) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
                            .padding(top = topOffset)
                            .windowInsetsPadding(WindowInsets.ime),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .widthIn(max = maxEditorWidth)
                                .padding(horizontal = horizontalPadding)
                                .padding(top = innerTopPadding),
                        horizontalAlignment = Alignment.CenterHorizontally,
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
        } // CompositionLocalProvider
    }
}
