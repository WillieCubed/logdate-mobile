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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.logdate.client.permissions.rememberLocationPermissionState
import app.logdate.feature.location.timeline.ui.model.LocationStopUiModel
import app.logdate.feature.location.timeline.ui.model.LocationTimelineErrorUiState
import app.logdate.feature.location.timeline.ui.model.LocationTimelineUiState
import logdate.client.feature.location.timeline.generated.resources.Res
import logdate.client.feature.location.timeline.generated.resources.cancel
import logdate.client.feature.location.timeline.generated.resources.delete_location
import logdate.client.feature.location.timeline.generated.resources.delete_location_confirmation
import logdate.client.feature.location.timeline.generated.resources.enable
import logdate.client.feature.location.timeline.generated.resources.location_permission_required
import logdate.client.feature.location.timeline.generated.resources.location_permission_required_description
import logdate.client.feature.location.timeline.generated.resources.location_services_disabled
import logdate.client.feature.location.timeline.generated.resources.location_services_disabled_description
import logdate.client.feature.location.timeline.generated.resources.location_timeline
import logdate.client.feature.location.timeline.generated.resources.locations_temporarily_unavailable_description
import logdate.client.feature.location.timeline.generated.resources.no_location_history_yet
import logdate.client.feature.location.timeline.generated.resources.samples_count
import logdate.client.feature.location.timeline.generated.resources.stayed_for_duration
import logdate.client.feature.location.timeline.generated.resources.try_again
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
                    Text(text = stringResource(Res.string.location_timeline))
                },
            )
        },
        modifier = modifier,
    ) { paddingValues ->
        LocationTimelineContent(
            uiState = uiState,
            onSelectStop = viewModel::selectStop,
            onDeleteStop = viewModel::deleteStop,
            onRetry = viewModel::retry,
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
    onRetry: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    when (uiState) {
        is LocationTimelineUiState.Loading -> LoadingState(modifier)
        is LocationTimelineUiState.Error -> ErrorState(error = uiState.error, onRetry = onRetry, modifier = modifier)
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
    error: LocationTimelineErrorUiState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        LocationTimelineErrorCard(
            error = error,
            onRetry = onRetry,
            modifier =
                Modifier
                    .widthIn(max = 420.dp)
                    .padding(24.dp),
        )
    }
}

@Composable
internal fun LocationTimelineErrorCard(
    error: LocationTimelineErrorUiState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val permissionState = rememberLocationPermissionState()

    LaunchedEffect(error, permissionState.hasPermission) {
        if (error == LocationTimelineErrorUiState.PermissionRequired && permissionState.hasPermission) {
            onRetry()
        }
    }

    val title: String
    val description: String
    val actionLabel: String
    val action: () -> Unit

    when (error) {
        LocationTimelineErrorUiState.PermissionRequired -> {
            title = stringResource(Res.string.location_permission_required)
            description = stringResource(Res.string.location_permission_required_description)
            actionLabel = stringResource(Res.string.enable)
            action = permissionState.requestPermission
        }

        LocationTimelineErrorUiState.LocationServicesDisabled -> {
            title = stringResource(Res.string.location_services_disabled)
            description = stringResource(Res.string.location_services_disabled_description)
            actionLabel = stringResource(Res.string.try_again)
            action = onRetry
        }

        LocationTimelineErrorUiState.TemporarilyUnavailable -> {
            title = stringResource(Res.string.unable_to_load_location_timeline)
            description = stringResource(Res.string.locations_temporarily_unavailable_description)
            actionLabel = stringResource(Res.string.try_again)
            action = onRetry
        }
    }

    Card(modifier = modifier) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(56.dp)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.shapes.large),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FilledTonalButton(onClick = action) {
                Text(actionLabel)
            }
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
            Row(modifier = Modifier.fillMaxSize()) {
                locationTimelineMap(
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
                VerticalDivider(modifier = Modifier.fillMaxHeight())
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
                locationTimelineMap(
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
                HorizontalDivider()
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
    var showActions by remember(stop.id) { mutableStateOf(false) }
    var showDeleteConfirmation by remember(stop.id) { mutableStateOf(false) }

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

                Box {
                    IconButton(onClick = { showActions = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = null)
                    }

                    DropdownMenu(
                        expanded = showActions,
                        onDismissRequest = { showActions = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.delete_location)) },
                            onClick = {
                                showActions = false
                                showDeleteConfirmation = true
                            },
                        )
                    }
                }
            }

            Text(
                text = stop.timeRange,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = stringResource(Res.string.stayed_for_duration, stop.duration),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "${stop.sourceLabel} • ${stringResource(Res.string.samples_count, stop.sampleCount)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text(stringResource(Res.string.delete_location)) },
            text = { Text(stringResource(Res.string.delete_location_confirmation)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirmation = false
                        onDelete()
                    },
                ) {
                    Text(stringResource(Res.string.delete_location))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) {
                    Text(stringResource(Res.string.cancel))
                }
            },
        )
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
