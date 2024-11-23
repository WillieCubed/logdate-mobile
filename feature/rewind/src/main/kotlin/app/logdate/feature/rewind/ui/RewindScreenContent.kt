package app.logdate.feature.rewind.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.SupportingPaneScaffold
import androidx.compose.material3.adaptive.layout.SupportingPaneScaffoldRole
import androidx.compose.material3.adaptive.layout.ThreePaneScaffoldScope
import androidx.compose.material3.adaptive.navigation.rememberSupportingPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import app.logdate.feature.rewind.ui.overview.FocusRewindUiState
import app.logdate.feature.rewind.ui.overview.RewindHistoryList
import app.logdate.feature.rewind.ui.overview.RewindHistoryUiState
import app.logdate.feature.rewind.ui.overview.RewindOverviewScreenUiState
import kotlinx.coroutines.launch
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalMaterial3AdaptiveApi::class, ExperimentalUuidApi::class)
@Composable
fun RewindScreenContent(
    state: RewindOverviewScreenUiState,
    onOpenRewind: RewindOpenCallback,
) {
    val navigator = rememberSupportingPaneScaffoldNavigator()
    val screenScope = rememberCoroutineScope()

    fun openSupportingPane() {
        screenScope.launch {
            navigator.navigateTo(SupportingPaneScaffoldRole.Supporting)
        }
    }

    BackHandler(navigator.canNavigateBack()) {
        screenScope.launch {
            navigator.navigateBack()
        }
    }

    SupportingPaneScaffold(
        directive = navigator.scaffoldDirective,
        value = navigator.scaffoldValue,
        mainPane = {
            AnimatedPane(modifier = Modifier.safeContentPadding()) {
                // Main pane content
//                if (navigator.scaffoldValue[SupportingPaneScaffoldRole.Supporting] == PaneAdaptedValue.Hidden) {
                if (state is RewindOverviewScreenUiState.Loaded) {
                    RewindOverviewMainPanel(
                        onShowPastCountdowns = ::openSupportingPane,
                        onOpenFocusedRewind = { },
                        uiState = state.mostRecentRewind,
                        modifier = Modifier
                    )
                } else {
                    Text("Supporting pane is hidden")
                }
//                } else {
//                    Text("Supporting pane is shown")
//                }
            }
        },
        supportingPane = {
            AnimatedPane(modifier = Modifier.safeContentPadding()) {
                RewindOverviewSupportingPanel(
                    history = SupportingPaneUiState(
                        rewinds = if (state is RewindOverviewScreenUiState.Loaded) {
                            state.pastRewinds
                        } else {
                            emptyList()
                        }
                    ),
                    onOpenRewind = onOpenRewind,
                )
            }
        },
    )
}

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
internal fun ThreePaneScaffoldScope.RewindOverviewMainPanel(
    uiState: FocusRewindUiState,
    onShowPastCountdowns: () -> Unit,
    onOpenFocusedRewind: () -> Unit,
    modifier: Modifier = Modifier,
) {
    RewindOverviewPanel(
        onOpenRewind = onOpenFocusedRewind,
        onOpenActivity = {},
    )
}

/**
 * A
 */
@Composable
fun RewindOverviewPanel(
    onOpenRewind: () -> Unit,
    onOpenActivity: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        onClick = onOpenRewind
    ) {

    }
}

data class SupportingPaneUiState(
    val rewinds: List<RewindHistoryUiState>,
)

internal fun getRewindFlavorText(ready: Boolean = false): String {
    // TODO: Generate flavor text dynamically depending on the state and content of the rewind
    return if (ready) {
        "Quite the adventurous one, aren't you?"
    } else {
        "Still working on the Rewind. Go touch some grass in the meantime."
    }
}

@OptIn(ExperimentalMaterial3AdaptiveApi::class, ExperimentalUuidApi::class)
@Composable
internal fun ThreePaneScaffoldScope.RewindOverviewSupportingPanel(
    history: SupportingPaneUiState,
    onOpenRewind: RewindOpenCallback,
    modifier: Modifier = Modifier,
) {
    RewindHistoryList(
        rewinds = history.rewinds,
        onOpenRewind = onOpenRewind,
        modifier = modifier,
    )
}

@OptIn(ExperimentalUuidApi::class)
@Composable
@Preview
private fun RewindScreenPreview() {
    val lastId = Uuid.random()
    RewindScreenContent(
        state = RewindOverviewScreenUiState.Loaded(
            pastRewinds = listOf(
                RewindHistoryUiState(Uuid.random(), "Rewind 1"),
                RewindHistoryUiState(Uuid.random(), "Rewind 2"),
                RewindHistoryUiState(lastId, "Rewind 3"),
            ),
            mostRecentRewind = FocusRewindUiState(
                message = "Five Cities in a Week",
                rewindId = lastId,
            ),
        ),
        onOpenRewind = {},
    )
}