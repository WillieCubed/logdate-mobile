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
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import app.logdate.ui.foldable.FoldableSplitLayout
import app.logdate.ui.foldable.calculateFoldableSplitLayout
import app.logdate.ui.foldable.relativeTo
import app.logdate.ui.foldable.rememberFoldableLayoutInfo
import app.logdate.ui.foldable.rememberWindowOrigin
import app.logdate.ui.platform.rememberScreenCornerRadius
import app.logdate.ui.theme.Spacing

/**
 * Signals to editor components nested anywhere beneath [ImmersiveEditorLayout] that the
 * available vertical space is too small to render full-size controls. Components should
 * switch to compact variants (e.g. chip instead of card) when this is `true`.
 */
internal val LocalEditorIsCompact = compositionLocalOf { false }

internal val LocalEditorRecordingActive = compositionLocalOf { false }

internal val LocalEditorContextControlsVisible = compositionLocalOf { true }

/**
 * A cross-platform immersive editor layout that provides a focused editing experience.
 *
 * Renders a full-screen dark scrim with editor content centered in a width-constrained
 * column. Context content ([bottomContent]) is shown below the editor and animates
 * away for full-screen camera capture and compact audio recording. Audio keeps the toolbar visible.
 *
 * Uses [BoxWithConstraints] so breakpoints respond to the container's available width
 * rather than physical screen size, which is correct for split-screen and freeform windows.
 *
 * @param topBarContent Content for the top action bar area (back button/navigation)
 * @param editorContent The main editor content displayed in the central area
 * @param bottomContent Context content (journal selector) shown below the editor
 * @param isImmersiveBlockActive Whether full-screen camera capture is active;
 *   hides [bottomContent] and collapses chrome
 * @param immersiveExitProgress Float in [0, 1] driving chrome visibility during immersive
 *   exit (0 = fully immersive, 1 = normal)
 * @param isAudioRecordingActive Whether audio is recording or finalizing; compact layouts hide context controls
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
    bottomContentTopPadding: Dp = Spacing.lg,
    isAudioRecordingActive: Boolean = false,
    screenCornerRadius: Dp = rememberScreenCornerRadius(),
) {
    val keyboardVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val (windowOrigin, trackWindowOrigin) = rememberWindowOrigin()
    BoxWithConstraints(modifier = modifier.fillMaxSize().then(trackWindowOrigin)) {
        val containerWidth = maxWidth
        val foldableLayoutInfo = rememberFoldableLayoutInfo()
        val bookLayout =
            calculateFoldableSplitLayout(
                containerWidth = containerWidth,
                containerHeight = maxHeight,
                layoutInfo = foldableLayoutInfo.relativeTo(windowOrigin),
                minPaneWidth = 320.dp,
            ) as? FoldableSplitLayout.Vertical
        val contextControlsVisible = !isAudioRecordingActive || (bookLayout?.leftPane?.width ?: containerWidth) >= 600.dp
        val hasSeparatingHinge = foldableLayoutInfo.hinge?.isSeparating == true
        val maxEditorWidth =
            when {
                hasSeparatingHinge -> containerWidth
                else -> 1200.dp
            }

        val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val topOffset = lerp(0.dp, statusBarTop + 64.dp, immersiveExitProgress)
        val horizontalPadding = lerp(0.dp, Spacing.sm, immersiveExitProgress)
        val bottomPadding = lerp(0.dp, Spacing.lg, immersiveExitProgress)
        val bottomInsets = if (isImmersiveBlockActive) WindowInsets.ime else WindowInsets.ime.union(WindowInsets.navigationBars)
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceDim),
        ) {
            BoxWithConstraints(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
                        .padding(top = topOffset)
                        .windowInsetsPadding(bottomInsets),
                contentAlignment = Alignment.Center,
            ) {
                CompositionLocalProvider(
                    LocalEditorIsCompact provides (maxHeight < 500.dp),
                    LocalEditorContextControlsVisible provides contextControlsVisible,
                    LocalEditorRecordingActive provides isAudioRecordingActive,
                    LocalEditorCorners provides
                        if (containerWidth < 600.dp && !hasSeparatingHinge) {
                            editorCornerGeometry(screenCornerRadius)
                        } else {
                            EditorCornerGeometry(24.dp, 8.dp)
                        },
                ) {
                    Column(
                        modifier =
                            Modifier
                                .widthIn(max = maxEditorWidth)
                                .fillMaxSize()
                                .padding(horizontal = horizontalPadding)
                                .padding(bottom = bottomPadding),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                        ) {
                            editorContent()
                        }

                        AnimatedVisibility(
                            visible = !isImmersiveBlockActive && !keyboardVisible && contextControlsVisible,
                            enter = fadeIn(tween(200)) + expandVertically(tween(300, easing = FastOutSlowInEasing)),
                            exit = fadeOut(tween(150)) + shrinkVertically(tween(300, easing = FastOutSlowInEasing)),
                        ) {
                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(top = bottomContentTopPadding),
                            ) {
                                Box(
                                    modifier =
                                        (
                                            if (bookLayout != null) {
                                                Modifier.width((bookLayout.leftPane.width - horizontalPadding).coerceAtLeast(0.dp))
                                            } else {
                                                Modifier.fillMaxWidth()
                                            }
                                        ).align(if (bookLayout != null) Alignment.CenterStart else Alignment.Center),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Box(
                                        modifier =
                                            Modifier
                                                .widthIn(max = EditorColumnMaxWidth)
                                                .fillMaxWidth()
                                                .padding(horizontal = EditorSurfaceInset),
                                    ) {
                                        bottomContent()
                                    }
                                }
                            }
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
}
