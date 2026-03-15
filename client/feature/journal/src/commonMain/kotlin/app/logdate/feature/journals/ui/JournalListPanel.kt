@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.journals.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass.Companion.HEIGHT_DP_MEDIUM_LOWER_BOUND
import androidx.window.core.layout.WindowSizeClass.Companion.WIDTH_DP_EXPANDED_LOWER_BOUND
import app.logdate.ui.theme.Spacing

/**
 * Returns an animated [Shape] for the journal list panel surface.
 *
 * - **Portrait phone**: Rounded top corners, flat bottom (panel extends to screen edge).
 * - **Landscape phone**: All corners rounded (panel sits inside a padded two-pane layout).
 * - **Tablet / desktop**: All corners flat (panel fills the containing pane, which clips).
 */
@Composable
private fun adaptivePanelShape(
    fallbackWidth: androidx.compose.ui.unit.Dp,
    fallbackHeight: androidx.compose.ui.unit.Dp,
): Shape {
    val isInspectionMode = LocalInspectionMode.current
    val windowSizeClass =
        if (isInspectionMode) {
            null
        } else {
            currentWindowAdaptiveInfo().windowSizeClass
        }
    val isWide =
        windowSizeClass?.isWidthAtLeastBreakpoint(WIDTH_DP_EXPANDED_LOWER_BOUND)
            ?: (fallbackWidth >= WIDTH_DP_EXPANDED_LOWER_BOUND.dp)
    val isTall =
        windowSizeClass?.isHeightAtLeastBreakpoint(HEIGHT_DP_MEDIUM_LOWER_BOUND)
            ?: (fallbackHeight >= HEIGHT_DP_MEDIUM_LOWER_BOUND.dp)

    val topCorner by animateDpAsState(
        targetValue = if (isWide && isTall) 0.dp else Spacing.lg,
        animationSpec = tween(300),
        label = "PanelTopCornerRadius",
    )
    val bottomCorner by animateDpAsState(
        targetValue = if (isWide && !isTall) Spacing.lg else 0.dp,
        animationSpec = tween(300),
        label = "PanelBottomCornerRadius",
    )

    return RoundedCornerShape(
        topStart = topCorner,
        topEnd = topCorner,
        bottomStart = bottomCorner,
        bottomEnd = bottomCorner,
    )
}

/**
 * The main content surface for the journals overview.
 *
 * Uses `surface` color (not `surfaceContainer`) so the panel sits visually above the shell
 * background. The top corners animate between rounded (compact, to separate from the
 * background) and square (expanded, where the panel fills the containing pane).
 *
 * System navigation bar insets are handled by the inner content column's `navigationBarsPadding()`,
 * which covers bottom insets on phones. The parent shell layout handles side insets for tablets.
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
    BoxWithConstraints(modifier = modifier) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            shape = adaptivePanelShape(maxWidth, maxHeight),
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

    // Handles bottom nav bar inset on phones. The parent shell layout handles
    // side insets for tablets and landscape.
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
