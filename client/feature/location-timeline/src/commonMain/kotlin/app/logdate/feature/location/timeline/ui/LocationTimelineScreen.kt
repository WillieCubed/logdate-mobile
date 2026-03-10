@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.location.timeline.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.logdate.feature.location.timeline.ui.model.LocationStopUiModel
import app.logdate.feature.location.timeline.ui.model.LocationTimelineUiState
import logdate.client.feature.location.timeline.generated.resources.Res
import logdate.client.feature.location.timeline.generated.resources.location_timeline
import logdate.client.feature.location.timeline.generated.resources.no_location_history_yet
import logdate.client.feature.location.timeline.generated.resources.unable_to_load_location_timeline
import logdate.client.feature.location.timeline.generated.resources.your_location_timeline_will_appear_here_as_you_move_around
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationTimelineScreen(
    modifier: Modifier = Modifier,
    viewModel: LocationTimelineViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(Res.string.location_timeline),
                        style = MaterialTheme.typography.headlineMedium,
                    )
                },
            )
        },
        modifier = modifier,
    ) { paddingValues ->
        LocationTimelineContent(
            uiState = uiState,
            onSelectStop = viewModel::selectStop,
            onDeleteStop = viewModel::deleteStop,
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
        )
    }
}

@Composable
fun LocationTimelineContent(
    uiState: LocationTimelineUiState,
    onSelectStop: (String) -> Unit,
    onDeleteStop: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (uiState) {
        is LocationTimelineUiState.Loading -> LoadingState(modifier)
        is LocationTimelineUiState.Error -> ErrorState(uiState.message, modifier)
        is LocationTimelineUiState.Success -> SuccessState(uiState, onSelectStop, onDeleteStop, modifier)
    }
}

@Composable
private fun LoadingState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ErrorState(
    message: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(Res.string.unable_to_load_location_timeline),
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SuccessState(
    uiState: LocationTimelineUiState.Success,
    onSelectStop: (String) -> Unit,
    onDeleteStop: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (uiState.stops.isEmpty() && uiState.currentLocation == null) {
        EmptyLocationTimeline(modifier = modifier)
        return
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val useTwoPane = maxWidth >= 840.dp

        if (useTwoPane) {
            Row(
                modifier = Modifier.fillMaxSize(),
            ) {
                LocationTimelineMap(
                    stops = uiState.stops,
                    currentLocation = uiState.currentLocation,
                    selectedStopId = uiState.selectedStopId,
                    onSelectStop = onSelectStop,
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .padding(16.dp),
                )
                Divider(modifier = Modifier.fillMaxSize().width(1.dp))
                StopsList(
                    stops = uiState.stops,
                    selectedStopId = uiState.selectedStop?.id,
                    onSelectStop = onSelectStop,
                    onDeleteStop = onDeleteStop,
                    modifier =
                        Modifier
                            .widthIn(min = 320.dp, max = 420.dp)
                            .fillMaxSize(),
                )
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                LocationTimelineMap(
                    stops = uiState.stops,
                    currentLocation = uiState.currentLocation,
                    selectedStopId = uiState.selectedStopId,
                    onSelectStop = onSelectStop,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(320.dp)
                            .padding(16.dp),
                )
                Divider()
                StopsList(
                    stops = uiState.stops,
                    selectedStopId = uiState.selectedStop?.id,
                    onSelectStop = onSelectStop,
                    onDeleteStop = onDeleteStop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun StopsList(
    stops: List<LocationStopUiModel>,
    selectedStopId: String?,
    onSelectStop: (String) -> Unit,
    onDeleteStop: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(
            items = stops,
            key = { it.id },
        ) { stop ->
            StopCard(
                stop = stop,
                selected = stop.id == selectedStopId,
                onSelect = { onSelectStop(stop.id) },
                onDelete = { onDeleteStop(stop.id) },
            )
        }
    }
}

@Composable
private fun StopCard(
    stop: LocationStopUiModel,
    selected: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        onClick = onSelect,
        border =
            if (selected) {
                BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
            } else {
                null
            },
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = stop.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = stop.subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AssistChip(
                    onClick = onSelect,
                    label = { Text(stop.sourceLabel) },
                )
                Text(
                    text = "${stop.sampleCount} samples",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Text(
                text = stop.timeRange,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = "Stayed ${stop.duration}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EmptyLocationTimeline(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(72.dp)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.shapes.large),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp),
                )
            }
            Text(
                text = stringResource(Res.string.no_location_history_yet),
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = stringResource(Res.string.your_location_timeline_will_appear_here_as_you_move_around),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
