@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.journals.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.logdate.client.domain.journals.JournalSuggestion
import app.logdate.client.repository.search.SearchResult
import app.logdate.ui.theme.Spacing
import kotlinx.datetime.LocalDate
import logdate.client.feature.journal.generated.resources.Res
import logdate.client.feature.journal.generated.resources.suggestion_create_journal
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * Journals overview screen. Wires [JournalsOverviewViewModel] state to the stateless content.
 */
@Composable
fun JournalsOverviewScreen(
    onOpenJournal: JournalClickCallback,
    onBrowseJournals: () -> Unit,
    onCreateJournal: () -> Unit,
    onNavigateToDay: (LocalDate) -> Unit = {},
    onNavigationClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: JournalsOverviewViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val entryResults by viewModel.entrySearchResults.collectAsStateWithLifecycle()
    val suggestions by viewModel.suggestions.collectAsStateWithLifecycle()

    JournalsOverviewScreenContent(
        journals = state.journals,
        layoutMode = state.layoutMode,
        sortOption = state.sortOption,
        activeFilters = state.activeFilters,
        searchQuery = state.searchQuery,
        isEntrySearchInProgress = state.isEntrySearchInProgress,
        entryResults = entryResults,
        suggestions = suggestions,
        onOpenJournal = onOpenJournal,
        onBrowseJournals = onBrowseJournals,
        onCreateJournal = onCreateJournal,
        onNavigateToDay = onNavigateToDay,
        onNavigationClick = onNavigationClick,
        onQueryChange = viewModel::updateSearchQuery,
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
    searchQuery: String,
    isEntrySearchInProgress: Boolean = false,
    entryResults: List<SearchResult>,
    suggestions: List<JournalSuggestion> = emptyList(),
    onOpenJournal: JournalClickCallback,
    onBrowseJournals: () -> Unit,
    onCreateJournal: () -> Unit,
    onNavigateToDay: (LocalDate) -> Unit,
    onQueryChange: (String) -> Unit,
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
                searchQuery = searchQuery,
                isEntrySearchInProgress = isEntrySearchInProgress,
                filteredJournals = journals,
                entryResults = entryResults,
                onQueryChange = onQueryChange,
                onOpenJournal = onOpenJournal,
                onNavigateToDay = onNavigateToDay,
                modifier = Modifier.fillMaxWidth().statusBarsPadding(),
            )
        },
    ) { paddingValues ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(top = Spacing.sm),
        ) {
            if (suggestions.isNotEmpty()) {
                SuggestionRow(
                    suggestions = suggestions,
                    onCreateJournal = onCreateJournal,
                )
            }

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
                modifier = Modifier.fillMaxSize().weight(1f),
            )
        }
    }
}

@Composable
private fun SuggestionRow(
    suggestions: List<JournalSuggestion>,
    onCreateJournal: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding =
            androidx.compose.foundation.layout
                .PaddingValues(horizontal = Spacing.lg),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        items(suggestions) { suggestion ->
            SuggestionCard(
                suggestion = suggestion,
                onAccept = onCreateJournal,
            )
        }
    }
}

@Composable
private fun SuggestionCard(
    suggestion: JournalSuggestion,
    onAccept: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
            ),
    ) {
        Column(
            modifier = Modifier.padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(
                text = suggestion.suggestedTitle,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Text(
                text = suggestion.reason,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
            )
            OutlinedButton(onClick = onAccept) {
                Text(stringResource(Res.string.suggestion_create_journal))
            }
        }
    }
}
