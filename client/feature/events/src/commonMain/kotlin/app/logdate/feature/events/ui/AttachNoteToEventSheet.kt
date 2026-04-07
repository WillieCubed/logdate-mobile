@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.events.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass.Companion.WIDTH_DP_EXPANDED_LOWER_BOUND
import app.logdate.client.repository.journals.JournalNote
import app.logdate.ui.theme.Spacing
import app.logdate.util.toReadableDateTimeShort
import kotlin.uuid.Uuid

/**
 * Adaptive sheet/dialog for picking a note to attach to an event.
 *
 * The sheet shows the candidate notes the ViewModel computed (notes within the event's time
 * window that aren't already linked). Selecting a note immediately attaches it via [onAttach]
 * and the sheet stays open so the user can attach several captures in a row. The "Done" button
 * closes the sheet.
 *
 * @param attachableNotes candidate notes to display.
 * @param onAttach invoked with the picked note id when the user taps a row.
 * @param onDismiss invoked when the user dismisses the sheet (back press, scrim tap, or "Done").
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AttachNoteToEventSheet(
    attachableNotes: List<JournalNote>,
    onAttach: (Uuid) -> Unit,
    onDismiss: () -> Unit,
) {
    val isExpanded =
        currentWindowAdaptiveInfo()
            .windowSizeClass
            .isWidthAtLeastBreakpoint(WIDTH_DP_EXPANDED_LOWER_BOUND)

    if (isExpanded) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Attach a capture") },
            text = {
                AttachNoteList(
                    attachableNotes = attachableNotes,
                    onAttach = onAttach,
                )
            },
            confirmButton = {
                TextButton(onClick = onDismiss) { Text("Done") }
            },
        )
    } else {
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = sheetState,
        ) {
            Column(modifier = Modifier.padding(bottom = Spacing.lg)) {
                Text(
                    text = "Attach a capture",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md),
                )
                AttachNoteList(
                    attachableNotes = attachableNotes,
                    onAttach = onAttach,
                )
            }
        }
    }
}

@Composable
private fun AttachNoteList(
    attachableNotes: List<JournalNote>,
    onAttach: (Uuid) -> Unit,
) {
    if (attachableNotes.isEmpty()) {
        Text(
            text = "Nothing nearby in time. Captures from a day before or after the event show up here.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(Spacing.lg),
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        items(attachableNotes, key = { it.uid }) { note ->
            ListItem(
                headlineContent = { Text(note.headline()) },
                supportingContent = { Text(note.creationTimestamp.toReadableDateTimeShort()) },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable { onAttach(note.uid) },
            )
        }
    }
}
