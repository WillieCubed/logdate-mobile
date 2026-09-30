@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.location.timeline.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import logdate.client.feature.location.timeline.generated.resources.Res
import logdate.client.feature.location.timeline.generated.resources.history_empty_places
import logdate.client.feature.location.timeline.generated.resources.history_empty_places_body
import logdate.client.feature.location.timeline.generated.resources.history_filter
import logdate.client.feature.location.timeline.generated.resources.history_list
import logdate.client.feature.location.timeline.generated.resources.history_map
import logdate.client.feature.location.timeline.generated.resources.history_no_matching_places
import logdate.client.feature.location.timeline.generated.resources.history_no_matching_places_body
import logdate.client.feature.location.timeline.generated.resources.history_search
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun HistoryPlaces(
    state: HumanLocationHistoryState,
    actions: HumanLocationHistoryActions,
    mapContent: @Composable (Modifier) -> Unit,
) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                OutlinedTextField(
                    value = state.placesQuery,
                    onValueChange = actions.onPlacesQuery,
                    label = { Text(stringResource(Res.string.history_search)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !state.placesMapVisible,
                        onClick = { actions.onPlacesMapVisible(false) },
                        label = { Text(stringResource(Res.string.history_list)) },
                    )
                    FilterChip(
                        selected = state.placesMapVisible,
                        onClick = { actions.onPlacesMapVisible(true) },
                        label = { Text(stringResource(Res.string.history_map)) },
                    )
                    TextButton(onClick = actions.onPlacesFilter) {
                        Text(state.placesFilterLabel.ifBlank { stringResource(Res.string.history_filter) })
                    }
                }
            }
        }
        if (state.placesMapVisible) {
            item { mapContent(Modifier.fillMaxWidth().height(280.dp).padding(horizontal = 16.dp)) }
        }
        val places = state.filteredPlaces()
        if (places.isEmpty()) {
            item {
                val searchHasNoResults = state.placesQuery.isNotBlank()
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(if (searchHasNoResults) Res.string.history_no_matching_places else Res.string.history_empty_places),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        stringResource(
                            if (searchHasNoResults) Res.string.history_no_matching_places_body else Res.string.history_empty_places_body,
                        ),
                    )
                }
            }
        }
        items(places, key = { it.id }) { place ->
            Card(onClick = { actions.onOpenPlace(place.id) }, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(place.title, style = MaterialTheme.typography.titleMedium)
                    Text(place.supportingText, style = MaterialTheme.typography.bodyMedium)
                    place.memories.take(3).forEach { HistoryMemoryPreview(it, actions.onOpenMemory) }
                }
            }
        }
    }
}
