package app.logdate.feature.editor.ui.layout

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBars
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
 *                 contentDescription = "Back"
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
@Composable
fun ImmersiveEditorLayout(
    isEditorFocused: Boolean,
    topBarContent: @Composable () -> Unit,
    editorContent: @Composable () -> Unit,
    bottomContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Get screen width using the cross-platform PlatformDimensions utility
    val screenWidth = PlatformDimensions.getScreenWidth()

    // Calculate responsive max width
    val maxWidth = remember(screenWidth) {
        when {
            screenWidth < 600.dp -> screenWidth
            screenWidth < 900.dp -> 600.dp
            else -> 800.dp
        }
    }

    // Full-screen dark background
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.50f))
//            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Transparent app bar region - fixed position
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars),
                contentAlignment = Alignment.CenterStart
            ) {
                topBarContent()
            }

            // Editor container with responsive layout - this responds to IME
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.ime), // Only this area adjusts for IME
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = maxWidth)
                        .padding(horizontal = Spacing.sm)
                        .padding(top = Spacing.sm),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Spacing.lg)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f) // Use weight to fill available space
                            .heightIn(min = 300.dp), // Keep minimum height as fallback
                    ) {
                        editorContent()
                    }

                    // Bottom content with proper system insets and spacing from IME
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .windowInsetsPadding(WindowInsets.navigationBars) // Use navigation bar insets for proper system UI avoidance
                            .padding(bottom = Spacing.md), // Add extra spacing above the system insets to prevent butting against IME
                    ) {
                        bottomContent()
                    }
                }
            }
        }
    }
}