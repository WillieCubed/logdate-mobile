@file:Suppress("ktlint:standard:function-naming", "ktlint:standard:no-wildcard-imports")

package app.logdate.feature.journals.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import app.logdate.feature.journals.ui.list.JournalsListDialog
import app.logdate.shared.model.Journal
import app.logdate.ui.platform.PlatformIcons
import app.logdate.ui.theme.Spacing
import logdate.client.feature.journal.generated.resources.*
import logdate.client.feature.journal.generated.resources.Res
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Clock

/**
 * A button that displays "See More" and opens a dialog with a full list of journals when clicked.
 *
 * @param journals The complete list of journals to display in the dialog
 * @param onJournalClick Callback when a journal is selected from the dialog
 * @param modifier Modifier for the button
 */
@Composable
fun JournalsSeeMoreButton(
    journals: List<Journal>,
    onJournalClick: (Journal) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showAllJournalsDialog by remember { mutableStateOf(false) }

    Button(
        onClick = { showAllJournalsDialog = true },
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(vertical = Spacing.md),
        colors =
            ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
    ) {
        Icon(
            painter = PlatformIcons.more(),
            contentDescription = null,
            modifier = Modifier.padding(end = Spacing.sm),
        )
        Text(
            text = stringResource(Res.string.see_all_journals),
            textAlign = TextAlign.Center,
        )
    }

    if (showAllJournalsDialog) {
        JournalsListDialog(
            journals = journals,
            onJournalClick = onJournalClick,
            onDismiss = { showAllJournalsDialog = false },
        )
    }
}

@Preview
@Composable
private fun JournalsSeeMoreButtonPreview() {
    val sampleJournals =
        listOf(
            Journal(
                title = "Travel Journal",
                created = Clock.System.now(),
            ),
            Journal(
                title = "Daily Notes",
                created = Clock.System.now(),
            ),
            Journal(
                title = "Work Log",
                created = Clock.System.now(),
            ),
        )

    JournalsSeeMoreButton(
        journals = sampleJournals,
        onJournalClick = {},
    )
}
