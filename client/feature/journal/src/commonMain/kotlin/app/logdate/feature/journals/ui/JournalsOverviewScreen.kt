@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.journals.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.logdate.ui.theme.Spacing
import org.koin.compose.viewmodel.koinViewModel

/**
 * Journals overview screen. Wires [JournalsOverviewViewModel] state to the stateless content.
 */
@Composable
fun JournalsOverviewScreen(
    onOpenJournal: JournalClickCallback,
    onBrowseJournals: () -> Unit,
    onCreateJournal: () -> Unit,
    onNavigationClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: JournalsOverviewViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    JournalsOverviewScreenContent(
        journals = state.journals,
        layoutMode = state.layoutMode,
        sortOption = state.sortOption,
        activeFilters = state.activeFilters,
        onOpenJournal = onOpenJournal,
        onBrowseJournals = onBrowseJournals,
        onCreateJournal = onCreateJournal,
        onNavigationClick = onNavigationClick,
        onToggleLayoutMode = viewModel::toggleLayoutMode,
        onSortOptionSelected = viewModel::setSortOption,
        onToggleFilter = viewModel::toggleFilter,
        modifier = modifier,
    )
}

/**
 * Stateless journals overview layout.
 *
 * Uses a transparent Scaffold so the shell's `surfaceContainer` background shows through
 * between the search toolbar and the [JournalListPanel] surface below.
 */
@Composable
fun JournalsOverviewScreenContent(
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
    onNavigationClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            JournalSearchToolbar(
                onNavigationClick = onNavigationClick,
                modifier = Modifier.fillMaxWidth(),
            )
        },
    ) { paddingValues ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(top = Spacing.sm),
            contentAlignment = Alignment.Center,
        ) {
            JournalListPanel(
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
                showLoading = false,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
