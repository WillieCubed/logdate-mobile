@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.location.timeline.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.logdate.feature.location.timeline.ui.model.CurrentLocationUiModel
import app.logdate.feature.location.timeline.ui.model.LocationStopUiModel
import app.logdate.feature.location.timeline.ui.model.LocationTimelineUiState
import logdate.client.feature.location.timeline.generated.resources.Res
import logdate.client.feature.location.timeline.generated.resources.close
import logdate.client.feature.location.timeline.generated.resources.current_location
import logdate.client.feature.location.timeline.generated.resources.location_timeline
import logdate.client.feature.location.timeline.generated.resources.no_location_history_yet
import logdate.client.feature.location.timeline.generated.resources.open_full_location_timeline
import logdate.client.feature.location.timeline.generated.resources.recent_stops
import logdate.client.feature.location.timeline.generated.resources.unable_to_load_location_timeline
import logdate.client.feature.location.timeline.generated.resources.your_location_timeline_will_appear_here_as_you_move_around
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationTimelineBottomSheet(
    onDismissRequest: () -> Unit,
    onOpenFullTimeline: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LocationTimelineViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        dragHandle = { BottomSheetDefaults.DragHandle() },
        modifier = modifier,
    ) {
        LocationTimelineQuickPeekContent(
            uiState = uiState,
            onDismissRequest = onDismissRequest,
            onOpenFullTimeline = onOpenFullTimeline,
            onSelectStop = viewModel::selectStop,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun LocationTimelineQuickPeekContent(
    uiState: LocationTimelineUiState,
    onDismissRequest: () -> Unit,
    onOpenFullTimeline: () -> Unit,
    onSelectStop: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(Res.string.location_timeline),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )

            IconButton(onClick = onDismissRequest) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(Res.string.close),
                )
            }
        }

        when (uiState) {
            is LocationTimelineUiState.Loading -> QuickPeekLoadingState()
            is LocationTimelineUiState.Error -> QuickPeekErrorState(message = uiState.message)
            is LocationTimelineUiState.Success ->
                QuickPeekSuccessState(
                    uiState = uiState,
                    onSelectStop = onSelectStop,
                )
        }

        FilledTonalButton(
            onClick = onOpenFullTimeline,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(Res.string.open_full_location_timeline))
        }
    }
}

@Composable
private fun QuickPeekLoadingState() {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(220.dp),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun QuickPeekErrorState(message: String) {
    Card {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = stringResource(Res.string.unable_to_load_location_timeline),
                style = MaterialTheme.typography.titleMedium,
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
private fun QuickPeekSuccessState(
    uiState: LocationTimelineUiState.Success,
    onSelectStop: (String) -> Unit,
) {
    val previewStops = uiState.stops.take(3)

    if (uiState.currentLocation == null && previewStops.isEmpty()) {
        Card {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = stringResource(Res.string.no_location_history_yet),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(Res.string.your_location_timeline_will_appear_here_as_you_move_around),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        return
    }

    LocationTimelineMap(
        stops = previewStops,
        currentLocation = uiState.currentLocation,
        selectedStopId = uiState.selectedStopId,
        onSelectStop = onSelectStop,
        modifier =
            Modifier
                .fillMaxWidth()
                .height(220.dp),
    )

    uiState.currentLocation?.let { currentLocation ->
        CurrentLocationCard(currentLocation)
    }

    if (previewStops.isNotEmpty()) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(Res.string.recent_stops),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
            )

            previewStops.forEach { stop ->
                QuickPeekStopCard(
                    stop = stop,
                    selected = stop.id == uiState.selectedStop?.id,
                    onClick = { onSelectStop(stop.id) },
                )
            }
        }
    }
}

@Composable
private fun CurrentLocationCard(currentLocation: CurrentLocationUiModel) {
    Card {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = stringResource(Res.string.current_location),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = currentLocation.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = currentLocation.subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun QuickPeekStopCard(
    stop: LocationStopUiModel,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
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
            verticalArrangement = Arrangement.spacedBy(8.dp),
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
            Text(
                text = stop.timeRange,
                style = MaterialTheme.typography.bodySmall,
            )
            AssistChip(
                onClick = onClick,
                label = { Text(stop.sourceLabel) },
            )
        }
    }
}
