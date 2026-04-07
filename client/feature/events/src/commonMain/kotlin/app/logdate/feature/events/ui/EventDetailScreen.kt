@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.events.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.logdate.shared.model.ExternalCalendarSource
import app.logdate.ui.theme.Spacing
import app.logdate.util.toReadableDateTimeShort
import coil3.compose.AsyncImage
import org.koin.compose.viewmodel.koinViewModel
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Screen for viewing and editing a single event.
 *
 * Lets the user see the event's cover image, when it happens, where it came from (if grounded
 * in an external calendar), how many things are attached to it, and edit its title and
 * description. The user can also delete the event from the top app bar.
 *
 * @param eventId The id of the event to open.
 * @param onGoBack Called when the user backs out or after a successful delete.
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

    LaunchedEffect(eventId) {
        viewModel.loadEvent(eventId)
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Event") },
                navigationIcon = {
                    IconButton(onClick = onGoBack) {
                        Icon(Icons.AutoMirrored.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (uiState is EventDetailUiState.Loaded) {
                        IconButton(onClick = { viewModel.delete(onGoBack) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete event")
                        }
                    }
                },
            )
        },
    ) { contentPadding ->
        when (val state = uiState) {
            EventDetailUiState.Loading -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(contentPadding),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator()
                }
            }

            EventDetailUiState.NotFound -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(contentPadding),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text("Event not found", style = MaterialTheme.typography.bodyLarge)
                }
            }

            is EventDetailUiState.Loaded -> {
                EventDetailContent(
                    state = state,
                    contentPadding = contentPadding,
                    onTitleChange = viewModel::updateTitle,
                    onDescriptionChange = viewModel::updateDescription,
                    onSave = viewModel::save,
                )
            }
        }
    }
}

/**
 * Editable body of an event. Stacks the cover image, source chip, title, time range,
 * description, linked-items count, any error, and the Save button into a vertical scroll.
 * Sections that don't apply (no cover image, no source, no linked items) are omitted.
 */
@Composable
private fun EventDetailContent(
    state: EventDetailUiState.Loaded,
    contentPadding: androidx.compose.foundation.layout.PaddingValues,
    onTitleChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onSave: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .verticalScroll(rememberScrollState())
                .padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        state.event.coverImageUri?.let { uri ->
            AsyncImage(
                model = uri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(Spacing.md)),
            )
        }

        state.event.externalCalendarSource?.let { source ->
            AssistChip(
                onClick = {},
                enabled = false,
                label = { Text("From ${source.displayLabel()}") },
                colors = AssistChipDefaults.assistChipColors(),
            )
        }

        OutlinedTextField(
            value = state.event.title,
            onValueChange = onTitleChange,
            label = { Text("Title") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )

        Text(
            text = formatTimeRange(state.event.startTime, state.event.endTime),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        OutlinedTextField(
            value = state.event.description.orEmpty(),
            onValueChange = onDescriptionChange,
            label = { Text("Description") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
        )

        if (state.linkedNoteCount > 0) {
            val label =
                if (state.linkedNoteCount == 1) "1 linked item" else "${state.linkedNoteCount} linked items"
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        state.errorMessage?.let { message ->
            Text(
                text = message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        Button(
            onClick = onSave,
            enabled = !state.isSaving && state.event.title.isNotBlank(),
        ) {
            Text(if (state.isSaving) "Saving…" else "Save")
        }
    }
}

/**
 * Human-readable label for an external calendar source. Used by the source chip on the event
 * detail screen so the user can see at a glance where a grounded event came from.
 */
private fun ExternalCalendarSource.displayLabel(): String =
    when (this) {
        ExternalCalendarSource.GOOGLE_CALENDAR -> "Google Calendar"
        ExternalCalendarSource.APPLE_CALENDAR -> "Apple Calendar"
    }

/**
 * Formats an event's time bounds for the read-only time line on the detail screen.
 *
 * Three cases:
 * - `end` is `null` → render the start timestamp alone (point-in-time event).
 * - `end == start` → render the start timestamp alone (zero-duration event).
 * - otherwise → render `"start – end"` separated by an en-dash.
 *
 * Both timestamps are converted to the device's local time zone via
 * [toReadableDateTimeShort].
 */
private fun formatTimeRange(
    start: Instant,
    end: Instant?,
): String {
    val startText = start.toReadableDateTimeShort()
    return if (end == null || end == start) {
        startText
    } else {
        "$startText – ${end.toReadableDateTimeShort()}"
    }
}
