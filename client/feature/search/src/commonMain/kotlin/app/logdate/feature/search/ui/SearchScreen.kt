@file:Suppress("ktlint:standard:function-naming", "ktlint:standard:no-wildcard-imports")

package app.logdate.feature.search.ui

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
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExpandedFullScreenSearchBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import app.logdate.client.repository.search.SearchResult
import app.logdate.client.repository.search.SearchResultType
import app.logdate.ui.search.EntrySearchResultItem
import app.logdate.ui.search.EntrySearchResultUiState
import app.logdate.util.toReadableDateTimeShort
import kotlinx.coroutines.flow.collectLatest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import logdate.client.feature.search.generated.resources.*
import logdate.client.feature.search.generated.resources.Res
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * Search screen for searching across all entries.
 *
 * @param onNavigateToDay Callback when a search result is tapped
 * @param onGoBack Callback when back button is pressed
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onNavigateToDay: (LocalDate) -> Unit,
    onGoBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = koinViewModel(),
) {
    val searchResults by viewModel.searchResults.collectAsState()

    SearchScreenContent(
        searchResults = searchResults,
        onQueryChange = viewModel::updateQuery,
        onNavigateToDay = onNavigateToDay,
        onGoBack = onGoBack,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreenContent(
    searchResults: List<SearchResult>,
    onQueryChange: (String) -> Unit,
    onNavigateToDay: (LocalDate) -> Unit,
    onGoBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val searchBarState = rememberSearchBarState()
    val textFieldState = rememberSaveable(saver = TextFieldState.Saver) { TextFieldState() }

    // Auto-expand on entry since this screen is navigated to for search
    LaunchedEffect(Unit) {
        searchBarState.animateToExpanded()
    }

    // Propagate text changes to ViewModel
    LaunchedEffect(textFieldState) {
        snapshotFlow { textFieldState.text.toString() }
            .collectLatest { onQueryChange(it) }
    }

    Box(modifier = modifier) {
        // Collapsed bar (briefly visible before auto-expand)
        SearchBar(
            state = searchBarState,
            inputField = {
                SearchBarDefaults.InputField(
                    searchBarState = searchBarState,
                    textFieldState = textFieldState,
                    onSearch = {},
                    placeholder = { Text(stringResource(Res.string.search_entries)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                )
            },
            modifier = Modifier.fillMaxWidth(),
        )

        // Expanded full-screen overlay
        ExpandedFullScreenSearchBar(
            state = searchBarState,
            inputField = {
                SearchBarDefaults.InputField(
                    searchBarState = searchBarState,
                    textFieldState = textFieldState,
                    onSearch = {},
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
            if (searchResults.isEmpty()) {
                EmptySearchState(modifier = Modifier.fillMaxSize())
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(searchResults, key = { it.uid.toString() }) { result ->
                        EntrySearchResultItem(
                            state = result.toUiState(),
                            onClick = {
                                val date =
                                    result.created
                                        .toLocalDateTime(TimeZone.currentSystemDefault())
                                        .date
                                onNavigateToDay(date)
                            },
                        )
                    }
                }
            }
        }
    }
}

/**
 * Empty state shown when there are no search results or no query.
 */
@Composable
private fun EmptySearchState(modifier: Modifier = Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.fillMaxSize(),
    ) {
        Text(
            text = stringResource(Res.string.search_for_entries),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun SearchResult.toUiState(): EntrySearchResultUiState =
    EntrySearchResultUiState(
        id = uid.toString(),
        content = content,
        dateLabel = created.toReadableDateTimeShort(),
        typeLabel =
            when (type) {
                SearchResultType.TEXT_NOTE -> "Text note"
                SearchResultType.TRANSCRIPTION -> "Voice note"
            },
        typeIcon =
            when (type) {
                SearchResultType.TEXT_NOTE -> Icons.Default.Search
                SearchResultType.TRANSCRIPTION -> Icons.Default.Mic
            },
    )
