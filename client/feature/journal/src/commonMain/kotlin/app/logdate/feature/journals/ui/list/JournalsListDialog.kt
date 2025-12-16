package app.logdate.feature.journals.ui.list

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import app.logdate.shared.model.Journal
import app.logdate.ui.theme.Spacing
import kotlinx.datetime.Clock
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * A dialog that displays a scrollable list of all available journals.
 *
 * @param journals The list of journals to display
 * @param onJournalClick Callback when a journal is clicked
 * @param onDismiss Callback when the dialog is dismissed
 * @param properties Optional dialog properties
 */
@Composable
fun JournalsListDialog(
    journals: List<Journal>,
    onJournalClick: (Journal) -> Unit,
    onDismiss: () -> Unit,
    properties: DialogProperties = DialogProperties()
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = properties,
        title = {
            Text(
                text = "All Journals",
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                if (journals.isEmpty()) {
                    Text(
                        text = "You don't have any journals yet.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    // Create a custom list with a scrollstate
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 400.dp)
                            .padding(top = Spacing.sm)
                    ) {
                        JournalsList(
                            journals = journals,
                            onJournalClick = { journal ->
                                onJournalClick(journal)
                                onDismiss() // Close dialog after selection
                            }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Preview
@Composable
private fun JournalsListDialogPreview() {
    val sampleJournals = listOf(
        Journal(
            title = "Travel Journal",
            created = Clock.System.now()
        ),
        Journal(
            title = "Daily Notes",
            created = Clock.System.now()
        ),
        Journal(
            title = "Work Log",
            created = Clock.System.now()
        ),
        Journal(
            title = "Personal Journal",
            created = Clock.System.now()
        ),
        Journal(
            title = "Book Notes",
            created = Clock.System.now()
        ),
        Journal(
            title = "Ideas & Concepts",
            created = Clock.System.now()
        ),
        Journal(
            title = "", // Will show as "Untitled Journal"
            created = Clock.System.now()
        )
    )
    
    JournalsListDialog(
        journals = sampleJournals,
        onJournalClick = {},
        onDismiss = {}
    )
}