@file:Suppress("ktlint:standard:function-naming")
@file:OptIn(ExperimentalMaterial3Api::class)

package app.logdate.feature.search.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExpandedFullScreenSearchBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.logdate.client.repository.search.SearchContentType
import app.logdate.client.repository.search.SearchResult
import app.logdate.ui.search.UniversalSearchResultItem
import kotlinx.coroutines.flow.collectLatest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import logdate.client.feature.search.generated.resources.Res
import logdate.client.feature.search.generated.resources.clear_search
import logdate.client.feature.search.generated.resources.go_back
import logdate.client.feature.search.generated.resources.search
import logdate.client.feature.search.generated.resources.search_entries
import logdate.client.feature.search.generated.resources.search_for_entries
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import kotlin.uuid.Uuid

/**
 * Universal search screen.
 *
 * Searches across all indexed content types (notes, journals, places, rewinds,
 * stickers, postcards) via a single FTS5 query. Shows recent searches when idle.
 */
@Composable
fun SearchScreen(
    onNavigateToDay: (LocalDate) -> Unit,
    onNavigateToJournal: (Uuid) -> Unit = {},
    onGoBack: () -> Unit,
    initialQuery: String = "",
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = koinViewModel(),
) {
    val searchState by viewModel.searchState.collectAsState()

    SearchScreenContent(
        searchState = searchState,
        initialQuery = initialQuery,
        onQueryChange = viewModel::updateQuery,
        onCommitSearch = viewModel::commitSearch,
        onNavigateToDay = onNavigateToDay,
        onNavigateToJournal = onNavigateToJournal,
        onGoBack = onGoBack,
        modifier = modifier,
    )
}

/**
 * Stateless content for the universal search screen.
 *
 * Renders an MD3 SearchBar that auto-expands on entry. Shows recent searches
 * when idle, a "no results" message when empty, or a ranked list of
 * [UniversalSearchResultItem]s when results are available.
 */
@Composable
fun SearchScreenContent(
    searchState: SearchScreenState,
    onQueryChange: (String) -> Unit,
    onCommitSearch: () -> Unit,
    onNavigateToDay: (LocalDate) -> Unit,
    onNavigateToJournal: (Uuid) -> Unit,
    onGoBack: () -> Unit,
    initialQuery: String = "",
    modifier: Modifier = Modifier,
) {
    val searchBarState = rememberSearchBarState()
    val textFieldState =
        rememberSaveable(saver = TextFieldState.Saver) {
            TextFieldState(initialText = initialQuery)
        }

    LaunchedEffect(Unit) {
        searchBarState.animateToExpanded()
    }

    LaunchedEffect(textFieldState) {
        snapshotFlow { textFieldState.text.toString() }
            .collectLatest { onQueryChange(it) }
    }

    Box(modifier = modifier) {
        SearchBar(
            state = searchBarState,
            inputField = {
                SearchBarDefaults.InputField(
                    searchBarState = searchBarState,
                    textFieldState = textFieldState,
                    onSearch = { onCommitSearch() },
                    placeholder = { Text(stringResource(Res.string.search_entries)) },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = stringResource(Res.string.search),
                        )
                    },
                )
            },
            modifier = Modifier.fillMaxWidth(),
        )

        ExpandedFullScreenSearchBar(
            state = searchBarState,
            inputField = {
                SearchBarDefaults.InputField(
                    searchBarState = searchBarState,
                    textFieldState = textFieldState,
                    onSearch = { onCommitSearch() },
                    placeholder = { Text(stringResource(Res.string.search_entries)) },
                    leadingIcon = {
                        IconButton(onClick = onGoBack) {
                            Icon(
                                Icons.AutoMirrored.Default.ArrowBack,
                                contentDescription = stringResource(Res.string.go_back),
                            )
                        }
                    },
                    trailingIcon = {
                        if (textFieldState.text.isNotEmpty()) {
                            IconButton(onClick = { textFieldState.clearText() }) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = stringResource(Res.string.clear_search),
                                )
                            }
                        }
                    },
                )
            },
        ) {
            when (searchState) {
                is SearchScreenState.Idle -> {
                    RecentSearchesList(
                        recentSearches = searchState.recentSearches,
                        onSelectRecent = { query -> onQueryChange(query) },
                    )
                }

                is SearchScreenState.Empty -> {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        Text(
                            text = "No results for \"${searchState.query}\"",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                is SearchScreenState.Results -> {
                    LazyColumn(modifier = Modifier.fillMaxWidth()) {
                        items(
                            items = searchState.results,
                            key = { "${it.contentType.ftsValue}_${it.uid}" },
                        ) { result ->
                            UniversalSearchResultItem(
                                result = result,
                                onClick = {
                                    onCommitSearch()
                                    navigateToResult(
                                        result = result,
                                        onNavigateToDay = onNavigateToDay,
                                        onNavigateToJournal = onNavigateToJournal,
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentSearchesList(
    recentSearches: List<String>,
    onSelectRecent: (String) -> Unit,
) {
    if (recentSearches.isEmpty()) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize(),
        ) {
            Text(
                text = stringResource(Res.string.search_for_entries),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } else {
        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            items(recentSearches) { query ->
                ListItem(
                    headlineContent = { Text(query) },
                    leadingContent = {
                        Icon(Icons.Default.History, contentDescription = null)
                    },
                    modifier = Modifier.clickable { onSelectRecent(query) },
                )
            }
        }
    }
}

private fun navigateToResult(
    result: SearchResult,
    onNavigateToDay: (LocalDate) -> Unit,
    onNavigateToJournal: (Uuid) -> Unit,
) {
    when (result.contentType) {
        SearchContentType.JOURNAL -> onNavigateToJournal(result.uid)
        else -> {
            val date =
                result.created
                    .toLocalDateTime(TimeZone.currentSystemDefault())
                    .date
            onNavigateToDay(date)
        }
    }
}
