package app.logdate.wear.v2.moments

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import app.logdate.client.repository.journals.JournalNote
import app.logdate.wear.R
import kotlinx.datetime.TimeZone

object MomentRowTags {
    const val ROW = "moment_row"
}

/** A moment in a list: when it was, and what it is in a few words. With [onOpen] the row opens it. */
@Composable
fun MomentRow(
    note: JournalNote,
    timeZone: TimeZone,
    onOpen: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val openLabel = stringResource(R.string.wear_moment_open)
    val opens = if (onOpen == null) Modifier else Modifier.clickable(onClickLabel = openLabel, role = Role.Button, onClick = onOpen)
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .then(opens)
                .padding(vertical = 4.dp)
                .testTag(MomentRowTags.ROW),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = momentDayAndTime(note.creationTimestamp, timeZone),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = momentLabel(note),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}
