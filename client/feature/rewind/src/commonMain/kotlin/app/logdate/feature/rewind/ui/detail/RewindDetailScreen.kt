@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.rewind.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import logdate.client.feature.rewind.generated.resources.delete_rewind_dialog_cancel
import logdate.client.feature.rewind.generated.resources.delete_rewind_dialog_confirm
import logdate.client.feature.rewind.generated.resources.delete_rewind_dialog_message
import logdate.client.feature.rewind.generated.resources.delete_rewind_dialog_title
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
import org.jetbrains.compose.resources.stringResource
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
    val deletePromptVisible by viewModel.deletePromptVisible.collectAsStateWithLifecycle()
    val isFirstView by viewModel.isFirstView.collectAsStateWithLifecycle()

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
        modifier = modifier,
        isFirstView = isFirstView,
        onFirstViewConsumed = viewModel::onFirstViewConsumed,
        externalPause = replySheetState is ReflectionReplySheetState.Open || deletePromptVisible,
        accentColor = currentRewind?.accentColor() ?: Color.White,
        onSharePanel = onSharePanel,
        onShareRewindStats = onShareRewindStats,
        onReplyToPrompt = viewModel::onReplyRequested,
        onDeleteRewind = viewModel::onDeleteRequested,
    )

    val openSheet = replySheetState as? ReflectionReplySheetState.Open
    if (openSheet != null) {
        ReflectionPromptReplySheet(
            state = openSheet,
            onSave = viewModel::onReplySubmitted,
            onDismiss = viewModel::onReplyDismissed,
        )
    }

    if (deletePromptVisible) {
        AlertDialog(
            onDismissRequest = viewModel::onDeleteCancelled,
            title = { Text(stringResource(Res.string.delete_rewind_dialog_title)) },
            text = { Text(stringResource(Res.string.delete_rewind_dialog_message)) },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.onDeleteConfirmed(onDeleted = onExitRewind) },
                ) {
                    Text(stringResource(Res.string.delete_rewind_dialog_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::onDeleteCancelled) {
                    Text(stringResource(Res.string.delete_rewind_dialog_cancel))
                }
            },
        )
    }
}

/**
 * Stateless body of the detail screen. Dispatches between loading, success, and
 * error presentations, and owns the post-viewing sheet that appears after the
 * user finishes a story.
 *
 * @param uiState The current state of the detail screen
 * @param onExitRewind Invoked when the user closes the rewind (via chrome, dismiss
 *   of the post-viewing sheet, or an error state's back button)
 * @param modifier Root modifier
 * @param isFirstView True when this rewind has never been opened. Captured from the
 *   ViewModel's snapshot so it reflects the pre-navigation state, not a live DB read.
 * @param onFirstViewConsumed Invoked after the story has played the first-view
 *   entrance so the ViewModel can clear the one-shot flag; preventing replays on
 *   configuration change or re-composition.
 * @param externalPause Pauses the story when chrome outside this composable (reply
 *   sheet, delete dialog) is visible
 * @param accentColor Foreground tint applied to the story chrome
 * @param onSharePanel Invoked when the user shares an individual panel; null hides
 *   the share affordance
 * @param onShareRewindStats Invoked when the user shares the overall rewind summary
 * @param onReplyToPrompt Invoked when the user replies to a reflection prompt panel
 * @param onDeleteRewind Invoked when the user confirms deletion from the chrome
 */
@Composable
fun RewindDetailScreenContent(
    uiState: RewindDetailUiState,
    onExitRewind: () -> Unit,
    modifier: Modifier = Modifier,
    isFirstView: Boolean = false,
    onFirstViewConsumed: (() -> Unit)? = null,
    externalPause: Boolean = false,
    accentColor: Color = Color.White,
    onSharePanel: ((panel: RewindPanelUiState) -> Unit)? = null,
    onShareRewindStats: (() -> Unit)? = null,
    onReplyToPrompt: ((panel: ReflectionPromptRewindPanelUiState) -> Unit)? = null,
    onDeleteRewind: (() -> Unit)? = null,
) {
    val windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass
    val isWideScreen = windowSizeClass.isWidthAtLeastBreakpoint(WIDTH_DP_MEDIUM_LOWER_BOUND)

    var showPostViewingSheet by rememberSaveable { mutableStateOf(false) }
    var restartKey by rememberSaveable { mutableStateOf(0) }

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
                        modifier =
                            Modifier
                                .fillMaxHeight(0.9f)
                                .aspectRatio(AspectRatios.RATIO_9_16)
                                .clip(RoundedCornerShape(16.dp)),
                        isFirstView = isFirstView,
                        onFirstViewConsumed = onFirstViewConsumed,
                        restartKey = restartKey,
                        externalPause = externalPause || showPostViewingSheet,
                        accentColor = accentColor,
                        onComplete = { showPostViewingSheet = true },
                        onSharePanel = onSharePanel,
                        onShareRewindStats = onShareRewindStats,
                        onReplyToPrompt = onReplyToPrompt,
                        onDeleteRewind = onDeleteRewind,
                        content = { panel ->
                            RewindStoryContent(panel = panel)
                        },
                    )
                }
            } else {
                RewindStoryView(
                    panels = currentState.panels,
                    onExit = onExitRewind,
                    modifier = modifier.fillMaxSize(),
                    isFirstView = isFirstView,
                    onFirstViewConsumed = onFirstViewConsumed,
                    restartKey = restartKey,
                    externalPause = externalPause || showPostViewingSheet,
                    onComplete = { showPostViewingSheet = true },
                    onSharePanel = onSharePanel,
                    onShareRewindStats = onShareRewindStats,
                    onReplyToPrompt = onReplyToPrompt,
                    content = { panel ->
                        RewindStoryContent(panel = panel)
                    },
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

    if (showPostViewingSheet) {
        RewindPostViewingSheet(
            onShare = {
                showPostViewingSheet = false
                onShareRewindStats?.invoke()
            },
            onWatchAgain = {
                showPostViewingSheet = false
                restartKey++
            },
            onDismiss = {
                showPostViewingSheet = false
                onExitRewind()
            },
        )
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
