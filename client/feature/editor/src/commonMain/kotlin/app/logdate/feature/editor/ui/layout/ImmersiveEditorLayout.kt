package app.logdate.feature.editor.ui.layout

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
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
import app.logdate.ui.PlatformDimensions
import app.logdate.ui.theme.Spacing

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

    // Full-screen dark background
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.50f)),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.statusBars),
                contentAlignment = Alignment.CenterStart,
            ) {
                topBarContent()
            }

            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .weight(1f)
                        .windowInsetsPadding(WindowInsets.ime),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .then(
                                if (!isImmersiveBlockActive) {
                                    Modifier
                                        .widthIn(max = maxWidth)
                                        .padding(horizontal = Spacing.sm)
                                        .padding(top = Spacing.sm)
                                } else {
                                    Modifier
                                },
                            ),
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
                        enter = fadeIn(),
                        exit = fadeOut(),
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
        }
    }
}
