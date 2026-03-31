@file:Suppress("ktlint:standard:function-naming")
@file:OptIn(ExperimentalMaterial3Api::class)

package app.logdate.feature.journals.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.logdate.client.repository.search.SearchContentType
import app.logdate.client.repository.search.SearchResult
import app.logdate.ui.search.EntrySearchResultItem
import app.logdate.ui.search.EntrySearchResultUiState
import app.logdate.util.toReadableDateTimeShort
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import logdate.client.feature.journal.generated.resources.Res
import logdate.client.feature.journal.generated.resources.cd_clear_search
import logdate.client.feature.journal.generated.resources.cd_close_search
import logdate.client.feature.journal.generated.resources.search_entries_loading
import logdate.client.feature.journal.generated.resources.search_journals
import logdate.client.feature.journal.generated.resources.search_no_results
import logdate.client.feature.journal.generated.resources.search_section_entries
import logdate.client.feature.journal.generated.resources.search_section_journals
import org.jetbrains.compose.resources.stringResource

/**
 * MD3 search toolbar for the journals screen.
 *
 * Collapsed: pill-shaped search bar in the top bar slot.
 * Expanded: full-screen overlay with filtered journals and entry search results.
 */
@Composable
fun JournalSearchToolbar(
    searchQuery: String,
    isEntrySearchInProgress: Boolean = false,
    filteredJournals: List<JournalListItemUiState>,
    entryResults: List<SearchResult>,
    onQueryChange: (String) -> Unit,
    onOpenJournal: JournalClickCallback,
    onNavigateToDay: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val searchBarState = rememberSearchBarState()
    val textFieldState = rememberSaveable(saver = TextFieldState.Saver) { TextFieldState() }
    val searchBarScope = rememberCoroutineScope()

    LaunchedEffect(textFieldState) {
        snapshotFlow { textFieldState.text.toString() }
            .collectLatest { onQueryChange(it) }
    }

    LaunchedEffect(searchQuery) {
        val currentText = textFieldState.text.toString()
        if (searchQuery != currentText) {
            textFieldState.edit {
                replace(0, length, searchQuery)
            }
        }
    }

    SearchBar(
        state = searchBarState,
        inputField = {
            SearchBarDefaults.InputField(
                searchBarState = searchBarState,
                textFieldState = textFieldState,
                onSearch = {},
                placeholder = { Text(stringResource(Res.string.search_journals)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            )
        },
        modifier = modifier.fillMaxWidth(),
    )

    ExpandedFullScreenSearchBar(
        state = searchBarState,
        inputField = {
            SearchBarDefaults.InputField(
                searchBarState = searchBarState,
                textFieldState = textFieldState,
                onSearch = {},
                placeholder = { Text(stringResource(Res.string.search_journals)) },
                leadingIcon = {
                    IconButton(onClick = { searchBarScope.launch { searchBarState.animateToCollapsed() } }) {
                        Icon(
                            Icons.AutoMirrored.Default.ArrowBack,
                            contentDescription = stringResource(Res.string.cd_close_search),
                        )
                    }
                },
                trailingIcon = {
                    if (textFieldState.text.isNotEmpty()) {
                        IconButton(onClick = { textFieldState.clearText() }) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = stringResource(Res.string.cd_clear_search),
                            )
                        }
                    }
                },
            )
        },
    ) {
        JournalSearchResults(
            query = searchQuery,
            isEntrySearchInProgress = isEntrySearchInProgress,
            filteredJournals = filteredJournals,
            entryResults = entryResults,
            onOpenJournal = onOpenJournal,
            onNavigateToDay = onNavigateToDay,
        )
    }
}

@Composable
private fun JournalSearchResults(
    query: String,
    isEntrySearchInProgress: Boolean,
    filteredJournals: List<JournalListItemUiState>,
    entryResults: List<SearchResult>,
    onOpenJournal: JournalClickCallback,
    onNavigateToDay: (LocalDate) -> Unit,
) {
    val matchingJournals = filteredJournals.filterIsInstance<JournalListItemUiState.ExistingJournal>()
    val hasJournals = matchingJournals.isNotEmpty()
    val hasEntries = entryResults.isNotEmpty()

    if (query.isNotEmpty() && !hasJournals && isEntrySearchInProgress) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(Res.string.search_entries_loading),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    if (query.isNotEmpty() && !hasJournals && !hasEntries) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(Res.string.search_no_results, query),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    LazyColumn(modifier = Modifier.fillMaxWidth()) {
        if (query.isNotEmpty() && hasJournals) {
            item(key = "header_journals") {
                Text(
                    text = stringResource(Res.string.search_section_journals),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            items(
                items = matchingJournals,
                key = { it.data.id.toString() },
            ) { item ->
                ListItem(
                    headlineContent = {
                        Text(
                            text = item.data.title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    supportingContent =
                        if (item.data.description.isNotBlank()) {
                            {
                                Text(
                                    text = item.data.description,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        } else {
                            null
                        },
                    modifier = Modifier.clickable { onOpenJournal(item.data.id) },
                )
            }
        }

        if (query.isNotEmpty() && hasJournals && hasEntries) {
            item(key = "divider") {
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            }
        }

        if (query.isNotEmpty() && hasJournals && isEntrySearchInProgress && !hasEntries) {
            item(key = "entries_loading") {
                Text(
                    text = stringResource(Res.string.search_entries_loading),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }

        if (query.isNotEmpty() && hasEntries) {
            item(key = "header_entries") {
                Text(
                    text = stringResource(Res.string.search_section_entries),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            items(
                items = entryResults,
                key = { it.uid.toString() },
            ) { result ->
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

private fun SearchResult.toUiState(): EntrySearchResultUiState =
    EntrySearchResultUiState(
        id = uid.toString(),
        content = content,
        dateLabel = created.toReadableDateTimeShort(),
        typeLabel =
            when (contentType) {
                SearchContentType.TEXT_NOTE -> "Text note"
                SearchContentType.TRANSCRIPTION -> "Voice note"
                else -> "Note"
            },
        typeIcon =
            when (contentType) {
                SearchContentType.TEXT_NOTE -> Icons.Default.Search
                SearchContentType.TRANSCRIPTION -> Icons.Default.Mic
                else -> Icons.Default.Search
            },
    )
