package app.logdate.feature.editor.ui.newstuff

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.logdate.util.localTime
import app.logdate.util.toReadableDateShort
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * A toolbar that displays context actions for the note editor.
 *
 * The toolbar displays a timestamp in the title and a button to save the note.
 * When the timestamp is clicked, the user is prompted to change the timestamp.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NoteEditorToolbar(
    timestamp: Instant,
    onClose: () -> Unit,
    onFinish: () -> Unit,
    onTimestampClick: () -> Unit,
    allowTimestampChange: Boolean = true,
) {
    TopAppBar(
        title = {
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier
                    .padding(2.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .clickable(
                        allowTimestampChange,
                        onClickLabel = "Update entry timestamp",
                        role = Role.Button,
                    ) { onTimestampClick() },
            ) {
                Text(
                    timestamp.toReadableDateShort(),
                )
                Text(timestamp.localTime, style = MaterialTheme.typography.labelLarge)
            }
        },
        colors = TopAppBarDefaults.topAppBarColors().copy(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(imageVector = Icons.Default.Close, contentDescription = "Exit")
            }
        },
        actions = {
            // Finish entry
            IconButton(onClick = onFinish) {
                Icon(Icons.Default.Save, contentDescription = "Save")
            }
        },
    )
}

@Preview
@Composable
private fun NoteEditorToolbarPreview() {
    NoteEditorToolbar(
        timestamp = Clock.System.now(),
        onClose = {},
        onFinish = {},
        onTimestampClick = {},
    )
}