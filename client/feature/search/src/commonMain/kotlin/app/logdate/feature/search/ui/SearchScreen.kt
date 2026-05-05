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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import app.logdate.client.repository.search.SearchContentType
import app.logdate.client.repository.search.SearchResult
import app.logdate.ui.search.UniversalSearchResultItem
import app.logdate.ui.search.UniversalSearchResultUiState
import app.logdate.ui.search.rememberSearchHighlightStyle
import app.logdate.ui.search.toUniversalSearchResultUiState
import kotlinx.coroutines.flow.collectLatest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import logdate.client.feature.search.generated.resources.Res
import logdate.client.feature.search.generated.resources.clear_search
import logdate.client.feature.search.generated.resources.search
import logdate.client.feature.search.generated.resources.search_entries
import logdate.client.feature.search.generated.resources.search_for_entries
import logdate.client.feature.search.generated.resources.search_no_results
import logdate.client.feature.search.generated.resources.searching_entries
import logdate.client.ui.generated.resources.common_go_back
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import kotlin.uuid.Uuid
import logdate.client.ui.generated.resources.Res as UiRes

const val SEARCH_SCREEN_INPUT_ACCESSIBILITY_TAG = "search_screen_input"

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
    onNavigateToPerson: (Uuid) -> Unit = {},
    onNavigateToNote: (Uuid) -> Unit = {},
    onNavigateToPostcard: (Uuid) -> Unit = {},
    onNavigateToRewind: (Uuid) -> Unit = {},
    onNavigateToMedia: (Uuid) -> Unit = {},
    onGoBack: () -> Unit,
    initialQuery: String = "",
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = koinViewModel(),
) {
    val searchState by viewModel.searchState.collectAsState()
    val queryText by viewModel.queryText.collectAsState()

    LaunchedEffect(initialQuery) {
        if (initialQuery.isNotBlank() && queryText.isBlank()) {
            viewModel.updateQuery(initialQuery)
        }
    }

    SearchScreenContent(
        searchState = searchState,
        queryText = queryText,
        initialQuery = initialQuery,
        onQueryChange = viewModel::updateQuery,
        onCommitSearch = viewModel::commitSearch,
        onNavigateToDay = onNavigateToDay,
        onNavigateToJournal = onNavigateToJournal,
        onNavigateToPerson = onNavigateToPerson,
        onNavigateToNote = onNavigateToNote,
        onNavigateToPostcard = onNavigateToPostcard,
        onNavigateToRewind = onNavigateToRewind,
        onNavigateToMedia = onNavigateToMedia,
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
    onNavigateToPerson: (Uuid) -> Unit,
    onNavigateToNote: (Uuid) -> Unit,
    onNavigateToPostcard: (Uuid) -> Unit,
    onNavigateToRewind: (Uuid) -> Unit,
    onNavigateToMedia: (Uuid) -> Unit,
    onGoBack: () -> Unit,
    initialQuery: String = "",
    queryText: String = initialQuery,
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

    LaunchedEffect(queryText) {
        val currentText = textFieldState.text.toString()
        if (queryText != currentText) {
            textFieldState.edit {
                replace(0, length, queryText)
            }
        }
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
                    modifier =
                        Modifier
                            .testTag(SEARCH_SCREEN_INPUT_ACCESSIBILITY_TAG)
                            .semantics {
                                contentDescription = SEARCH_SCREEN_INPUT_ACCESSIBILITY_TAG
                            },
                    placeholder = { Text(stringResource(Res.string.search_entries)) },
                    leadingIcon = {
                        IconButton(onClick = onGoBack) {
                            Icon(
                                Icons.AutoMirrored.Default.ArrowBack,
                                contentDescription = stringResource(UiRes.string.common_go_back),
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

                is SearchScreenState.Searching -> {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        Text(
                            text = stringResource(Res.string.searching_entries),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                is SearchScreenState.Empty -> {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        Text(
                            text = stringResource(Res.string.search_no_results, searchState.query),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                is SearchScreenState.Results -> {
                    val highlightStyle = rememberSearchHighlightStyle()
                    val resultRows =
                        remember(searchState.results, highlightStyle) {
                            searchState.results.map { result ->
                                SearchResultRow(
                                    result = result,
                                    uiState = result.toUniversalSearchResultUiState(highlightStyle),
                                )
                            }
                        }
                    LazyColumn(modifier = Modifier.fillMaxWidth()) {
                        items(
                            items = resultRows,
                            key = { it.uiState.id },
                        ) { resultRow ->
                            UniversalSearchResultItem(
                                state = resultRow.uiState,
                                onClick = {
                                    onCommitSearch()
                                    navigateToResult(
                                        result = resultRow.result,
                                        onNavigateToDay = onNavigateToDay,
                                        onNavigateToJournal = onNavigateToJournal,
                                        onNavigateToPerson = onNavigateToPerson,
                                        onNavigateToNote = onNavigateToNote,
                                        onNavigateToPostcard = onNavigateToPostcard,
                                        onNavigateToRewind = onNavigateToRewind,
                                        onNavigateToMedia = onNavigateToMedia,
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

private data class SearchResultRow(
    val result: SearchResult,
    val uiState: UniversalSearchResultUiState,
)

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
    onNavigateToPerson: (Uuid) -> Unit,
    onNavigateToNote: (Uuid) -> Unit,
    onNavigateToPostcard: (Uuid) -> Unit,
    onNavigateToRewind: (Uuid) -> Unit,
    onNavigateToMedia: (Uuid) -> Unit,
) {
    when (result.contentType) {
        SearchContentType.JOURNAL -> onNavigateToJournal(result.uid)
        SearchContentType.PERSON -> onNavigateToPerson(result.uid)
        SearchContentType.TEXT_NOTE -> onNavigateToNote(result.uid)
        SearchContentType.POSTCARD -> onNavigateToPostcard(result.uid)
        SearchContentType.REWIND -> onNavigateToRewind(result.uid)
        SearchContentType.MEDIA_CAPTION -> onNavigateToMedia(result.uid)
        SearchContentType.TRANSCRIPTION,
        SearchContentType.AMBIENT_SOUND,
        SearchContentType.STICKER,
        SearchContentType.PLACE,
        -> {
            // Until dedicated detail screens exist for these types (or TimelineDetailRoute gains an
            // entryId parameter — Phase 2e), fall back to the containing day.
            val date =
                result.created
                    .toLocalDateTime(TimeZone.currentSystemDefault())
                    .date
            onNavigateToDay(date)
        }
    }
}
