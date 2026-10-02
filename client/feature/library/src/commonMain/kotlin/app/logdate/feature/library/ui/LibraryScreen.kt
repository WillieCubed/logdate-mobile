@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.library.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.window.core.layout.WindowSizeClass.Companion.WIDTH_DP_EXPANDED_LOWER_BOUND
import androidx.window.core.layout.WindowSizeClass.Companion.WIDTH_DP_MEDIUM_LOWER_BOUND
import app.logdate.ui.platform.PlatformIcons
import app.logdate.ui.theme.Spacing
import app.logdate.ui.workspace.LocalWorkspaceEnabled
import app.logdate.ui.workspace.PanelContainment
import app.logdate.ui.workspace.WorkspacePanel
import org.koin.compose.viewmodel.koinViewModel
import kotlin.uuid.Uuid

/**
 * Stateful Library screen that injects the ViewModel and adapts the grid column count
 * based on the current window size.
 */
@Composable
fun LibraryScreen(
    onOpenMediaDetail: (Uuid) -> Unit,
    onOpenSearch: () -> Unit = {},
    onOpenPostcards: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    viewModel: LibraryViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    if (LocalWorkspaceEnabled.current) {
        BoxWithConstraints(modifier) {
            LibraryScreenContent(
                state,
                (maxWidth / 140.dp).toInt().coerceAtLeast(2),
                onOpenMediaDetail,
                onOpenSearch,
                onOpenPostcards,
                viewModel::retry,
                Modifier.fillMaxSize(),
            )
        }
        return
    }
    val windowSizeClass = currentWindowAdaptiveInfoV2().windowSizeClass
    val columnCount =
        when {
            windowSizeClass.isWidthAtLeastBreakpoint(WIDTH_DP_EXPANDED_LOWER_BOUND) -> 5
            windowSizeClass.isWidthAtLeastBreakpoint(WIDTH_DP_MEDIUM_LOWER_BOUND) -> 4
            else -> 3
        }
    LibraryScreenContent(state, columnCount, onOpenMediaDetail, onOpenSearch, onOpenPostcards, viewModel::retry, modifier)
}

/**
 * Stateless library overview layout.
 *
 * Uses a transparent Scaffold so the shell's `surfaceContainer` background shows through
 * between the top bar and the [LibraryPanel] surface below. Follows the same layout
 * strategy as the journals overview screen.
 */
@Composable
fun LibraryScreenContent(
    state: LibraryUiState,
    columnCount: Int,
    onItemClick: (Uuid) -> Unit,
    onOpenSearch: () -> Unit = {},
    onOpenPostcards: (() -> Unit)? = null,
    onRetry: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    if (LocalWorkspaceEnabled.current) {
        WorkspacePanel(modifier, containment = PanelContainment.Collection) {
            Column(Modifier.fillMaxSize()) {
                if (onOpenPostcards != null) {
                    AssistChip(
                        onClick = onOpenPostcards,
                        label = { Text("Postcards") },
                        modifier = Modifier.padding(horizontal = Spacing.lg),
                    )
                }
                LibraryPanel(state, columnCount, onItemClick, onRetry, Modifier.weight(1f).fillMaxWidth())
            }
        }
        return
    }
    Scaffold(
        modifier = modifier,
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            LibraryTopBar(
                onOpenSearch = onOpenSearch,
                modifier = Modifier.fillMaxWidth().statusBarsPadding(),
            )
        },
    ) { paddingValues ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
        ) {
            if (onOpenPostcards != null) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    AssistChip(
                        onClick = onOpenPostcards,
                        label = { Text("Postcards") },
                        trailingIcon = {
                            Icon(
                                painter = PlatformIcons.forward(),
                                contentDescription = null,
                            )
                        },
                    )
                }
            }
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(top = Spacing.sm),
                contentAlignment = Alignment.Center,
            ) {
                LibraryPanel(
                    state = state,
                    columnCount = columnCount,
                    onItemClick = onItemClick,
                    onRetry = onRetry,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
