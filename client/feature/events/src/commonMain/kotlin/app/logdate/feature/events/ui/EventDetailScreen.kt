@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.events.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.logdate.shared.model.Place
import app.logdate.shared.model.displayLabel
import app.logdate.ui.theme.Spacing
import app.logdate.util.toReadableDateTimeRangeShort
import org.koin.compose.viewmodel.koinViewModel
import kotlin.uuid.Uuid

/**
 * Screen for viewing and editing a single event.
 *
 * Lets the user see and change the cover image, title, description, time bounds, place, and
 * attached captures, then save or delete. Layout follows Material 3 Expressive: a large
 * collapsing top app bar, sectioned scrollable body, sticky save bar that animates in once
 * the user has unsaved changes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventDetailScreen(
    eventId: Uuid,
    onGoBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: EventDetailViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    LaunchedEffect(eventId) {
        viewModel.loadEvent(eventId)
    }

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text("Event") },
                navigationIcon = {
                    IconButton(onClick = onGoBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (uiState is EventDetailUiState.Loaded) {
                        IconButton(onClick = { viewModel.delete(onGoBack) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete event")
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        bottomBar = {
            EventDetailSaveBar(state = uiState, onSave = viewModel::save)
        },
    ) { contentPadding ->
        when (val state = uiState) {
            EventDetailUiState.Loading -> CenteredProgress(contentPadding)
            EventDetailUiState.NotFound -> CenteredEmptyState(contentPadding, "Event not found")
            is EventDetailUiState.Loaded ->
                EventDetailContent(
                    state = state,
                    actions = viewModel,
                    contentPadding = contentPadding,
                )
        }
    }

    // Pickers must live as siblings of the Scaffold body, not inside the scrollable LazyColumn,
    // so their internal scroll/gesture handling doesn't conflict with the body's scroll.
    (uiState as? EventDetailUiState.Loaded)?.let { loaded ->
        if (loaded.isPlacePickerOpen) {
            EventPlacePicker(
                availablePlaces = loaded.availablePlaces,
                selectedPlaceId = loaded.event.placeId,
                onPlaceSelected = viewModel::updatePlace,
                onDismiss = viewModel::dismissPlacePicker,
            )
        }
        if (loaded.isAttachSheetOpen) {
            AttachNoteToEventSheet(
                attachableNotes = loaded.attachableNotes,
                onAttach = viewModel::linkNote,
                onDismiss = viewModel::dismissAttachSheet,
            )
        }
    }
}

@Composable
private fun CenteredProgress(contentPadding: PaddingValues) {
    Column(
        modifier = Modifier.fillMaxSize().padding(contentPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun CenteredEmptyState(
    contentPadding: PaddingValues,
    text: String,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(contentPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

/**
 * Editable body for an event in the [EventDetailUiState.Loaded] state.
 *
 * Lays out cover, source chip (if grounded), title, time, place, description, original time
 * line, attached captures, and any error message — each as a separate LazyColumn item so the
 * editor stays cheap to scroll on long events with many attachments.
 */
@Composable
private fun EventDetailContent(
    state: EventDetailUiState.Loaded,
    actions: EventDetailActions,
    contentPadding: PaddingValues,
) {
    val launchPicker =
        rememberCoverImageLauncher { picked ->
            if (picked != null) actions.updateCoverImage(picked)
        }

    LazyColumn(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(contentPadding),
        contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        item("cover") {
            EventCoverImageCard(
                coverImageUri = state.event.coverImageUri,
                onPickImage = launchPicker,
                onClearImage = { actions.updateCoverImage(null) },
            )
        }

        state.event.externalCalendarSource?.let { source ->
            item("source-chip") {
                AssistChip(
                    onClick = {},
                    enabled = false,
                    label = { Text("From ${source.displayLabel()}") },
                    colors = AssistChipDefaults.assistChipColors(),
                )
            }
        }

        item("title") {
            OutlinedTextField(
                value = state.event.title,
                onValueChange = actions::updateTitle,
                label = { Text("Title") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        }

        item("when") {
            EventTimeRangeSection(
                startTime = state.event.startTime,
                endTime = state.event.endTime,
                onStartTimeChange = actions::updateStartTime,
                onEndTimeChange = actions::updateEndTime,
                onTogglePointInTime = actions::togglePointInTime,
            )
        }

        item("where") {
            EventPlaceSection(
                resolvedPlace = state.resolvedPlace,
                hasPlace = state.event.placeId != null,
                onChoosePlace = actions::openPlacePicker,
            )
        }

        item("description") {
            OutlinedTextField(
                value = state.event.description.orEmpty(),
                onValueChange = actions::updateDescription,
                label = { Text("Description") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
            )
        }

        item("original-time") {
            Text(
                text = "Originally " + state.event.startTime.toReadableDateTimeRangeShort(state.event.endTime),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        item("divider") {
            HorizontalDivider()
        }

        item("attached") {
            EventLinkedNotesSection(
                notes = state.linkedNotes,
                onUnlink = actions::unlinkNote,
                onAddCapture = actions::openAttachSheet,
            )
        }

        state.errorMessage?.let { message ->
            item("error") {
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun EventPlaceSection(
    resolvedPlace: Place.UserDefined?,
    hasPlace: Boolean,
    onChoosePlace: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(Spacing.lg),
    ) {
        Column(modifier = Modifier.padding(Spacing.lg)) {
            Text(
                text = "Where",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ListItem(
                headlineContent = {
                    Text(resolvedPlace?.displayName ?: "No place set")
                },
                supportingContent =
                    resolvedPlace?.description?.let { description ->
                        { Text(description) }
                    },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = Spacing.sm),
            )
            FilledTonalButton(onClick = onChoosePlace) {
                Text(if (hasPlace) "Change place" else "Choose a place")
            }
        }
    }
}

/**
 * Sticky save bar pinned to the bottom of the editor. Animates in once the screen is loaded.
 * The Save button is disabled while the title is blank or a save is in flight.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EventDetailSaveBar(
    state: EventDetailUiState,
    onSave: () -> Unit,
) {
    AnimatedVisibility(
        visible = state is EventDetailUiState.Loaded,
        enter = scaleIn() + fadeIn(),
        exit = scaleOut() + fadeOut(),
    ) {
        val loaded = state as? EventDetailUiState.Loaded ?: return@AnimatedVisibility
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 3.dp,
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.lg, vertical = Spacing.md),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(
                        onClick = onSave,
                        enabled = !loaded.isSaving && loaded.event.title.isNotBlank(),
                    ) {
                        Text(if (loaded.isSaving) "Saving…" else "Save")
                    }
                }
            }
        }
    }
}
