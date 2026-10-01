@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.location.timeline.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.logdate.ui.common.adaptivePanelShape
import app.logdate.ui.theme.Spacing
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
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        BoxWithConstraints(Modifier.widthIn(max = 720.dp).fillMaxWidth().fillMaxHeight()) {
            Surface(
                modifier = Modifier.fillMaxSize().padding(top = Spacing.sm),
                color = MaterialTheme.colorScheme.surface,
                shape = adaptivePanelShape(maxWidth, maxHeight),
            ) {
                LazyColumn(
                    contentPadding = PaddingValues(Spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    item { HistoryPlaceSearch(state, actions) }
                    if (state.placesMapVisible) {
                        item { mapContent(Modifier.fillMaxWidth().height(192.dp).clip(RoundedCornerShape(16.dp))) }
                    }
                    val places = state.filteredPlaces()
                    if (places.isEmpty()) {
                        item {
                            val noResults = state.placesQuery.isNotBlank()
                            Column(Modifier.padding(vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    stringResource(
                                        if (noResults) Res.string.history_no_matching_places else Res.string.history_empty_places,
                                    ),
                                    style = MaterialTheme.typography.titleLarge,
                                )
                                Text(
                                    stringResource(
                                        if (noResults) Res.string.history_no_matching_places_body else Res.string.history_empty_places_body,
                                    ),
                                )
                            }
                        }
                    }
                    items(places, key = { it.id }) { place -> HistoryPlaceRow(place) { actions.onOpenPlace(place.id) } }
                }
            }
        }
    }
}

@Composable
private fun HistoryPlaceSearch(
    state: HumanLocationHistoryState,
    actions: HumanLocationHistoryActions,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TextField(
            value = state.placesQuery,
            onValueChange = actions.onPlacesQuery,
            placeholder = { Text(stringResource(Res.string.history_search)) },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = TextFieldDefaults.colors(focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !state.placesMapVisible, onClick = {
                actions.onPlacesMapVisible(false)
            }, label = {
                Text(
                    stringResource(Res.string.history_list),
                )
            }, leadingIcon = { Icon(Icons.AutoMirrored.Filled.ViewList, null, Modifier.size(18.dp)) })
            FilterChip(selected = state.placesMapVisible, onClick = {
                actions.onPlacesMapVisible(true)
            }, label = {
                Text(
                    stringResource(Res.string.history_map),
                )
            }, leadingIcon = { Icon(Icons.Default.Map, null, Modifier.size(18.dp)) })
            FilterChip(selected = true, onClick = actions.onPlacesFilter, label = {
                Text(
                    state.placesFilterLabel.ifBlank {
                        stringResource(Res.string.history_filter)
                    },
                )
            }, leadingIcon = { Icon(Icons.Default.CalendarMonth, null, Modifier.size(18.dp)) })
        }
    }
}

@Composable
private fun HistoryPlaceRow(
    place: HistoryPlaceUi,
    onClick: () -> Unit,
) {
    Surface(onClick = onClick, shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val image = place.memories.firstOrNull { it.thumbnailUri != null }
            if (image?.thumbnailUri != null) {
                HistoryMemoryThumbnail(image.thumbnailUri, image.kind == HistoryMemoryKind.Video, 48.dp)
            } else {
                Surface(Modifier.size(48.dp), shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Place, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                }
            }
            Column(Modifier.weight(1f)) {
                Text(place.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    place.supportingText,
                    Modifier.padding(top = 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                place.memories.firstOrNull()?.let {
                    Text(
                        it.title,
                        Modifier.padding(top = 10.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                null,
                Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
