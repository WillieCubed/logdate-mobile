@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.library.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.window.core.layout.WindowSizeClass.Companion.WIDTH_DP_EXPANDED_LOWER_BOUND
import androidx.window.core.layout.WindowSizeClass.Companion.WIDTH_DP_MEDIUM_LOWER_BOUND
import app.logdate.ui.theme.Spacing
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
    onOpenPostcards: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: LibraryViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass
    val columnCount =
        when {
            windowSizeClass.isWidthAtLeastBreakpoint(WIDTH_DP_EXPANDED_LOWER_BOUND) -> 5
            windowSizeClass.isWidthAtLeastBreakpoint(WIDTH_DP_MEDIUM_LOWER_BOUND) -> 4
            else -> 3
        }

    LibraryScreenContent(
        state = state,
        columnCount = columnCount,
        onItemClick = onOpenMediaDetail,
        onOpenSearch = onOpenSearch,
        onOpenPostcards = onOpenPostcards,
        modifier = modifier,
    )
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
    onOpenPostcards: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
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
                            Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                        )
                    },
                )
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
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
