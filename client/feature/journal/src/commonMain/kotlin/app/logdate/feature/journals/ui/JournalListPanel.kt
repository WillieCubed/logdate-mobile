@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.journals.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass.Companion.WIDTH_DP_EXPANDED_LOWER_BOUND

/**
 * Returns an animated [Shape] for the journal list panel surface.
 *
 * On compact screens the panel has rounded top corners to visually separate it
 * from the background. On expanded screens (tablet / desktop) the panel fills
 * the containing surface, so the corners animate down to zero.
 */
@Composable
private fun adaptivePanelShape(): Shape {
    val windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass
    val isExpanded = windowSizeClass.isWidthAtLeastBreakpoint(WIDTH_DP_EXPANDED_LOWER_BOUND)

    val cornerRadius by animateDpAsState(
        targetValue = if (isExpanded) 0.dp else 16.dp,
        animationSpec = tween(300),
        label = "PanelCornerRadius",
    )

    return RoundedCornerShape(topStart = cornerRadius, topEnd = cornerRadius)
}

/**
 * The main content surface for the journals overview.
 *
 * Uses `surface` color (not `surfaceContainer`) so the panel sits visually above the shell
 * background. The top corners animate between rounded (compact, to separate from the
 * background) and square (expanded, where the panel fills the containing pane).
 *
 * System navigation bar insets are handled at two levels: the outer Scaffold handles right-side
 * insets for tablets, while the inner content column handles bottom insets for phones.
 */
@Composable
fun JournalListPanel(
    journals: List<JournalListItemUiState>,
    layoutMode: JournalLayoutMode,
    sortOption: JournalSortOption,
    activeFilters: Set<JournalFilter>,
    onOpenJournal: JournalClickCallback,
    onBrowseJournals: () -> Unit,
    onCreateJournal: () -> Unit,
    onToggleLayoutMode: () -> Unit,
    onSortOptionSelected: (JournalSortOption) -> Unit,
    onToggleFilter: (JournalFilter) -> Unit,
    modifier: Modifier = Modifier,
    showLoading: Boolean = false,
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = adaptivePanelShape(),
    ) {
        JournalListPlaceholder(isVisible = showLoading)
        AnimatedVisibility(
            visible = showLoading.not(),
            enter = fadeIn(animationSpec = tween(200)),
            exit = fadeOut(animationSpec = tween(200)),
        ) {
            if (journals.isEmpty()) {
                NoJournalsScreen()
            } else {
                JournalListContent(
                    journals = journals,
                    layoutMode = layoutMode,
                    sortOption = sortOption,
                    activeFilters = activeFilters,
                    onOpenJournal = onOpenJournal,
                    onBrowseJournals = onBrowseJournals,
                    onCreateJournal = onCreateJournal,
                    onToggleLayoutMode = onToggleLayoutMode,
                    onSortOptionSelected = onSortOptionSelected,
                    onToggleFilter = onToggleFilter,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

/**
 * Main content column: filter bar pinned at top, then the active layout mode below.
 */
@Composable
private fun JournalListContent(
    journals: List<JournalListItemUiState>,
    layoutMode: JournalLayoutMode,
    sortOption: JournalSortOption,
    activeFilters: Set<JournalFilter>,
    onOpenJournal: JournalClickCallback,
    onBrowseJournals: () -> Unit,
    onCreateJournal: () -> Unit,
    onToggleLayoutMode: () -> Unit,
    onSortOptionSelected: (JournalSortOption) -> Unit,
    onToggleFilter: (JournalFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    val gridState = rememberLazyGridState()
    val isScrolled by remember {
        derivedStateOf {
            layoutMode == JournalLayoutMode.GRID &&
                (gridState.firstVisibleItemIndex > 0 || gridState.firstVisibleItemScrollOffset > 0)
        }
    }

    // Inner nav bar padding handles bottom inset on phones where the system
    // navigation bar sits below the content. The outer Scaffold already handles
    // the right-side inset for tablets.
    Column(modifier = modifier.navigationBarsPadding()) {
        JournalFilterBar(
            layoutMode = layoutMode,
            sortOption = sortOption,
            activeFilters = activeFilters,
            onToggleLayoutMode = onToggleLayoutMode,
            onSortOptionSelected = onSortOptionSelected,
            onToggleFilter = onToggleFilter,
            isScrolled = isScrolled,
            modifier = Modifier.fillMaxWidth(),
        )

        AnimatedContent(
            targetState = layoutMode,
            transitionSpec = {
                fadeIn(tween(200)) togetherWith fadeOut(tween(200))
            },
            modifier = Modifier.fillMaxSize(),
            label = "LayoutModeTransition",
        ) { mode ->
            when (mode) {
                JournalLayoutMode.CAROUSEL -> {
                    JournalCarouselContent(
                        journals = journals,
                        onOpenJournal = onOpenJournal,
                        onCreateJournal = onCreateJournal,
                        onBrowseJournals = onBrowseJournals,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                JournalLayoutMode.GRID -> {
                    JournalList(
                        journals = journals,
                        onOpenJournal = onOpenJournal,
                        onCreateJournal = onCreateJournal,
                        gridState = gridState,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}
