package app.logdate.feature.journals.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ElevatedButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.logdate.feature.journals.ui.JournalClickCallback
import app.logdate.feature.journals.ui.JournalCover
import app.logdate.model.Journal
import app.logdate.ui.content.audience.AudienceMember
import kotlinx.datetime.Instant

/**
 * A settings screen that allows a user to change various properties of a journal.
 */
@Composable
fun JournalSettingsScreen(journal: Journal) {
    LazyColumn {
        item {
            SettingsHeaderSection(journal = journal) {

            }
        }

    }
}

@Composable
private fun SettingsHeaderSection(
    journal: Journal,
    onShareJournal: () -> Unit,
) {
    Column {
        JournalPreview(journal)
        ElevatedButton(onClick = { onShareJournal() }) {

        }
    }
}

@Composable
private fun JournalPreview(journal: Journal) {

}


@Composable
fun AudienceBlock() {

}

@Composable
private fun AudienceHeader(
    title: String,
) {

}

/**
 * An audience member that is allowed to access a journal.
 */
@Composable
private fun AudienceMemberItem(
    member: AudienceMember,
    dateAdded: Instant,
    onOpen: () -> Unit,
    onRemove: () -> Unit = {},
) {

}

@Composable
fun PeopleMetadataBlock() {

}


@Composable
fun LargeJournalCover(
    journal: Journal,
    onClick: JournalClickCallback,
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color.Blue,
) {
    JournalCover(
        journal = journal,
        onClick = onClick,
        modifier = modifier.height(180.dp),
        backgroundColor = backgroundColor
    )
}
