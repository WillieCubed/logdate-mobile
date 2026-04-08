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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.window.core.layout.WindowSizeClass.Companion.WIDTH_DP_MEDIUM_LOWER_BOUND
import app.logdate.feature.rewind.ui.ReflectionPromptRewindPanelUiState
import app.logdate.feature.rewind.ui.RewindDetailUiState
import app.logdate.feature.rewind.ui.RewindDetailViewModel
import app.logdate.feature.rewind.ui.RewindPanelUiState
import app.logdate.ui.common.AspectRatios
import app.logdate.util.toReadableDateShort
import kotlinx.coroutines.launch
import logdate.client.feature.rewind.generated.resources.Res
import logdate.client.feature.rewind.generated.resources.share_rewind_chooser_title
import logdate.client.feature.rewind.generated.resources.share_rewind_panel_caption
import logdate.client.feature.rewind.generated.resources.share_rewind_panel_text_template
import logdate.client.feature.rewind.generated.resources.share_rewind_stats_caption
import logdate.client.feature.rewind.generated.resources.share_rewind_stats_chooser_title
import logdate.client.feature.rewind.generated.resources.share_rewind_stats_heading_location
import logdate.client.feature.rewind.generated.resources.share_rewind_stats_heading_theme
import logdate.client.feature.rewind.generated.resources.share_rewind_stats_label_entries
import logdate.client.feature.rewind.generated.resources.share_rewind_stats_label_people
import logdate.client.feature.rewind.generated.resources.share_rewind_stats_label_photos
import logdate.client.feature.rewind.generated.resources.share_rewind_stats_subtitle_template
import logdate.client.feature.rewind.generated.resources.share_rewind_stats_text_template
import org.jetbrains.compose.resources.getString
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

    val coroutineScope = rememberCoroutineScope()
    val currentRewind by viewModel.currentRewind.collectAsStateWithLifecycle()
    val replySheetState by viewModel.replySheetState.collectAsStateWithLifecycle()

    val onSharePanel: (RewindPanelUiState) -> Unit = { panel ->
        val shareContent = panel.toShareContent()
        if (shareContent != null) {
            coroutineScope.launch {
                val request =
                    RewindShareRequest(
                        text = getString(Res.string.share_rewind_panel_text_template, shareContent.text),
                        title = getString(Res.string.share_rewind_panel_caption),
                        chooserTitle = getString(Res.string.share_rewind_chooser_title),
                        visual = shareContent.visual,
                    )
                viewModel.sharePanel(request)
            }
        }
    }

    val onShareRewindStats: (() -> Unit)? =
        currentRewind?.let { rewind ->
            {
                coroutineScope.launch {
                    val request =
                        RewindStatsShareRequest(
                            text = getString(Res.string.share_rewind_stats_text_template, rewind.title),
                            title = getString(Res.string.share_rewind_stats_caption),
                            chooserTitle = getString(Res.string.share_rewind_stats_chooser_title),
                            subtitle =
                                getString(
                                    Res.string.share_rewind_stats_subtitle_template,
                                    rewind.startDate.toReadableDateShort(),
                                    rewind.endDate.toReadableDateShort(),
                                ),
                            entriesLabel = getString(Res.string.share_rewind_stats_label_entries),
                            photosLabel = getString(Res.string.share_rewind_stats_label_photos),
                            peopleLabel = getString(Res.string.share_rewind_stats_label_people),
                            themeHeadingLabel = getString(Res.string.share_rewind_stats_heading_theme),
                            locationHeadingLabel = getString(Res.string.share_rewind_stats_heading_location),
                        )
                    viewModel.shareRewindStats(request)
                }
            }
        }

    RewindDetailScreenContent(
        uiState = uiState,
        onExitRewind = onExitRewind,
        onSharePanel = onSharePanel,
        onShareRewindStats = onShareRewindStats,
        onReplyToPrompt = viewModel::onReplyRequested,
        externalPause = replySheetState is ReflectionReplySheetState.Open,
        modifier = modifier,
    )

    val openSheet = replySheetState as? ReflectionReplySheetState.Open
    if (openSheet != null) {
        ReflectionPromptReplySheet(
            state = openSheet,
            onSave = viewModel::onReplySubmitted,
            onDismiss = viewModel::onReplyDismissed,
        )
    }
}

@Composable
fun RewindDetailScreenContent(
    uiState: RewindDetailUiState,
    onExitRewind: () -> Unit,
    modifier: Modifier = Modifier,
    onSharePanel: ((panel: RewindPanelUiState) -> Unit)? = null,
    onShareRewindStats: (() -> Unit)? = null,
    onReplyToPrompt: ((panel: ReflectionPromptRewindPanelUiState) -> Unit)? = null,
    externalPause: Boolean = false,
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
                        onSharePanel = onSharePanel,
                        onShareRewindStats = onShareRewindStats,
                        onReplyToPrompt = onReplyToPrompt,
                        externalPause = externalPause,
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
                    onSharePanel = onSharePanel,
                    onShareRewindStats = onShareRewindStats,
                    onReplyToPrompt = onReplyToPrompt,
                    externalPause = externalPause,
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
