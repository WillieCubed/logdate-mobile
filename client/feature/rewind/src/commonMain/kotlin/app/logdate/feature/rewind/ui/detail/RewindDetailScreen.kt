@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.rewind.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.window.core.layout.WindowSizeClass.Companion.WIDTH_DP_MEDIUM_LOWER_BOUND
import app.logdate.feature.rewind.ui.RewindDetailUiState
import app.logdate.feature.rewind.ui.RewindDetailViewModel
import app.logdate.ui.common.AspectRatios
import org.koin.compose.viewmodel.koinViewModel
import kotlin.uuid.Uuid

/**
 * The main screen to interact with a Rewind in an immersive, full-screen Instagram Stories-like interface.
 *
 * This screen provides an engaging viewing experience for weekly rewind content, presenting
 * story panels that users can navigate through with familiar touch gestures and auto-advance timing.
 *
 * ## Features:
 * - **Full-screen immersive experience** with status bar overlay
 * - **Instagram Stories-like navigation** with tap and swipe gestures
 * - **Auto-advance functionality** with progress indicators
 * - **Flexible content types** supporting text, images, and statistics
 * - **Error handling** with graceful fallbacks
 *
 * ## State Management:
 * Handles three main UI states:
 * - **Loading**: Shows progress indicator while rewind data loads
 * - **Success**: Displays the story interface with rewind panels
 * - **Error**: Shows error message and allows user to exit
 *
 * @param rewindId Unique identifier of the rewind to display
 * @param onExitRewind Callback invoked when user exits the rewind view
 * @param viewModel ViewModel managing rewind data and state
 */
@Composable
fun RewindDetailScreen(
    rewindId: Uuid,
    onExitRewind: () -> Unit,
    viewModel: RewindDetailViewModel = koinViewModel(),
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(rewindId) {
        viewModel.loadRewind(rewindId)
    }

    RewindDetailScreenContent(
        uiState = uiState,
        onExitRewind = onExitRewind,
        modifier = modifier,
    )
}

@Composable
fun RewindDetailScreenContent(
    uiState: RewindDetailUiState,
    onExitRewind: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass
    val isWideScreen = windowSizeClass.isWidthAtLeastBreakpoint(WIDTH_DP_MEDIUM_LOWER_BOUND)

    when (val currentState = uiState) {
        is RewindDetailUiState.Loading -> {
            RewindLoadingScreen(modifier = modifier.fillMaxSize())
        }

        is RewindDetailUiState.Success -> {
            if (isWideScreen) {
                // On wide screens, center a 9:16 card with rounded corners
                Box(
                    modifier = modifier.fillMaxSize().background(Color.Black),
                    contentAlignment = Alignment.Center,
                ) {
                    RewindStoryView(
                        panels = currentState.panels,
                        onExit = onExitRewind,
                        content = { panel ->
                            RewindStoryContent(panel = panel)
                        },
                        modifier =
                            Modifier
                                .fillMaxHeight(0.9f)
                                .aspectRatio(AspectRatios.RATIO_9_16)
                                .clip(RoundedCornerShape(16.dp)),
                    )
                }
            } else {
                RewindStoryView(
                    panels = currentState.panels,
                    onExit = onExitRewind,
                    content = { panel ->
                        RewindStoryContent(panel = panel)
                    },
                    modifier = modifier.fillMaxSize(),
                )
            }
        }

        is RewindDetailUiState.Error -> {
            RewindErrorScreen(
                message = "Whoops, we couldn't catch the rewind. Try again later.",
                onExit = onExitRewind,
                modifier = modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * Loading screen displayed while rewind data is being fetched.
 *
 * Shows a centered progress indicator with a black background to match
 * the immersive story interface aesthetic.
 *
 * @param modifier Modifier for customizing the loading screen container
 */
@Composable
fun RewindLoadingScreen(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            color = Color.White,
        )
    }
}

/**
 * Error screen displayed when rewind data fails to load or an error occurs.
 *
 * Provides a user-friendly error message with an option to exit back to the
 * rewind overview. Uses consistent black background for visual continuity.
 *
 * @param message Error message to display to the user
 * @param onExit Callback invoked when user chooses to exit
 * @param modifier Modifier for customizing the error screen container
 */
@Composable
fun RewindErrorScreen(
    message: String,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White,
            textAlign = TextAlign.Center,
            modifier =
                Modifier
                    .background(Color.Black)
                    .clickable { onExit() },
        )
    }
}
