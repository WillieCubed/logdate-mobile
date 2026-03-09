@file:Suppress("ktlint:standard:function-naming", "ktlint:standard:no-wildcard-imports")

package app.logdate.feature.search.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.logdate.client.repository.search.SearchResult
import app.logdate.client.repository.search.SearchResultType
import app.logdate.util.toReadableDateTimeShort
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
    val query by viewModel.query.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()

    SearchScreenContent(
        query = query,
        searchResults = searchResults,
        onQueryChange = viewModel::updateQuery,
        onClearSearch = viewModel::clearSearch,
        onNavigateToDay = onNavigateToDay,
        onGoBack = onGoBack,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreenContent(
    query: String,
    searchResults: List<SearchResult>,
    onQueryChange: (String) -> Unit,
    onClearSearch: () -> Unit,
    onNavigateToDay: (LocalDate) -> Unit,
    onGoBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    TextField(
                        value = query,
                        onValueChange = onQueryChange,
                        placeholder = { Text(stringResource(Res.string.search_entries)) },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = stringResource(Res.string.search))
                        },
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                IconButton(onClick = onClearSearch) {
                                    Icon(Icons.Default.Clear, contentDescription = stringResource(Res.string.clear_search))
                                }
                            }
                        },
                        colors =
                            TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                            ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onGoBack) {
                        Icon(Icons.AutoMirrored.Default.ArrowBack, contentDescription = stringResource(Res.string.go_back))
                    }
                },
            )
        },
    ) { paddingValues ->
        SearchResultsList(
            results = searchResults,
            onResultClick = { result ->
                val date =
                    result.created
                        .toLocalDateTime(TimeZone.currentSystemDefault())
                        .date
                onNavigateToDay(date)
            },
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
        )
    }
}

/**
 * List of search results.
 */
@Composable
private fun SearchResultsList(
    results: List<SearchResult>,
    onResultClick: (SearchResult) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (results.isEmpty()) {
        EmptySearchState(modifier = modifier)
    } else {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = modifier,
        ) {
            items(results, key = { it.uid.toString() }) { result ->
                SearchResultItem(
                    result = result,
                    onClick = { onResultClick(result) },
                )
            }
        }
    }
}

/**
 * Empty state when there are no search results.
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

/**
 * Individual search result item.
 */
@Composable
private fun SearchResultItem(
    result: SearchResult,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier =
            modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
    ) {
        ListItem(
            headlineContent = {
                Text(
                    text = result.content,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            supportingContent = {
                Column {
                    Text(
                        text = result.created.toReadableDateTimeShort(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text =
                            when (result.type) {
                                SearchResultType.TEXT_NOTE -> "Text note"
                                SearchResultType.TRANSCRIPTION -> "Voice note"
                            },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
            },
        )
    }
}
