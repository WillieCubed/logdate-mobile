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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
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

/**
 * A cross-platform immersive editor layout that provides a focused editing experience.
 * This component handles platform-specific screen dimensions and creates a responsive
 * layout optimized for content creation.
 *
 * Features:
 * - Dark overlay background for focused editing
 * - Responsive width constraints based on screen size
 * - IME (keyboard) insets handling
 * - Consistent aspect ratio for content
 * - Safe area insets support
 *
 * @param isEditorFocused Whether the editor currently has input focus
 * @param topBarContent Content for the top action bar area (usually back button/navigation)
 * @param editorContent The main editor content that will be displayed in the card
 * @param bottomContent Content for the bottom bar (usually controls or metadata)
 * @param modifier Optional modifier for the root layout
 *
 * Example usage:
 * ```
 * ImmersiveEditorLayout(
 *     onNavigateBack = { navController.popBackStack() },
 *     isEditorFocused = editorFocusState,
 *     topBarContent = {
 *         FilledTonalIconButton(onClick = { navController.popBackStack() }) {
 *             Icon(
 *                 imageVector = Icons.AutoMirrored.Filled.ArrowBack,
 *                 contentDescription = stringResource(Res.string.back)
 *             )
 *         }
 *     },
 *     editorContent = {
 *         TextEditor(
 *             text = viewModel.text,
 *             onTextChange = viewModel::updateText,
 *             modifier = Modifier.fillMaxSize()
 *         )
 *     },
 *     bottomContent = {
 *         EditorControls(
 *             onSave = viewModel::saveEntry,
 *             onAddImage = viewModel::addImage
 *         )
 *     }
 * )
 * ```
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
    // Get screen width using the cross-platform PlatformDimensions utility
    val screenWidth = PlatformDimensions.getScreenWidth()

    // Calculate responsive max width
    val maxWidth =
        remember(screenWidth) {
            when {
                screenWidth < 600.dp -> screenWidth
                screenWidth < 900.dp -> 600.dp
                else -> 800.dp
            }
        }

    // Live status bar height — read once per composition, KMP-safe.
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    // Interpolate directly from immersiveExitProgress (0 = fully immersive, 1 = normal).
    // The caller owns the animation — either gesture-scrubbed or time-animated — so this
    // composable is a pure function of the progress value with no internal animation state.
    val topOffset = lerp(0.dp, statusBarTop + 40.dp, immersiveExitProgress)
    val horizontalPadding = lerp(0.dp, Spacing.sm, immersiveExitProgress)
    val innerTopPadding = lerp(0.dp, Spacing.sm, immersiveExitProgress)
    val scrimAlpha = lerpFloat(0.72f, 0.50f, immersiveExitProgress)

    // Full-screen dark background — alpha tracks the same progress as the layout.
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = scrimAlpha)),
    ) {
        // Editor content — in immersive mode fills the full screen (behind status bar and toolbar).
        // In normal mode, slides down via topOffset to sit below the toolbar.
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
                        // widthIn is unconditional — on small screens maxWidth == screenWidth.
                        // The visual narrowing comes from the animated horizontal padding.
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

                // expandVertically/shrinkVertically animate the layout space so the surrounding
                // content doesn't snap when the bottom bar appears or disappears.
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

        // Top bar — always a floating overlay so the back button remains accessible.
        // In immersive mode this sits visually on top of the full-bleed content.
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
