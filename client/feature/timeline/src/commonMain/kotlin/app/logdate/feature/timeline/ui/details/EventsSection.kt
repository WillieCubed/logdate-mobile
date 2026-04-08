@file:Suppress("ktlint:standard:function-naming", "ktlint:standard:no-wildcard-imports")

package app.logdate.feature.timeline.ui.details

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import app.logdate.ui.common.noteDropTarget
import app.logdate.ui.theme.Spacing
import app.logdate.ui.timeline.DayEventUiState
import logdate.client.feature.timeline.generated.resources.*
import logdate.client.feature.timeline.generated.resources.Res
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun EventsSection(
    events: List<DayEventUiState>,
    onOpenEvent: (eventId: String) -> Unit,
    onAttachNoteToEvent: (noteId: String, eventId: String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    if (events.isEmpty()) return
    Column(
        modifier = modifier.padding(horizontal = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(stringResource(Res.string.events), style = MaterialTheme.typography.titleSmall)
        events.forEach { event ->
            EventItem(
                event = event,
                onOpenEvent = { onOpenEvent(event.eventId) },
                onAttachNote = { noteId -> onAttachNoteToEvent(noteId, event.eventId) },
            )
        }
    }
}

@Composable
private fun EventItem(
    event: DayEventUiState,
    onOpenEvent: () -> Unit,
    onAttachNote: (noteId: String) -> Unit,
) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Spacing.md))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .noteDropTarget { droppedText -> onAttachNote(droppedText) }
                .clickable { onOpenEvent() }
                .padding(Spacing.lg),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(
                text = event.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            event.description?.takeIf { it.isNotBlank() }?.let { description ->
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
