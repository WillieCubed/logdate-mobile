package app.logdate.feature.editor.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import app.logdate.shared.model.UserPlace
import app.logdate.ui.theme.Spacing
import app.logdate.util.localTime
import kotlinx.datetime.Instant
import logdate.client.feature.editor.generated.resources.Res
import logdate.client.feature.editor.generated.resources.text_placeholder_location
import logdate.client.feature.editor.generated.resources.text_placeholder_start_writing
import org.jetbrains.compose.resources.stringResource

@Composable
fun WritingEntryBlock(
    creationTime: Instant,
    location: UserPlace?,
    entryContents: String,
    onNoteUpdate: (newContent: String) -> Unit,
    onRequestLocationUpdate: () -> Unit,
    modifier: Modifier = Modifier,
    locationEnabled: Boolean = false,
    onLocationClick: () -> Unit = {},
    onCreationTimeClick: () -> Unit,
) {
    val expanded by rememberSaveable { mutableStateOf(false) }
    val lineColor = MaterialTheme.colorScheme.onSurfaceVariant
    val locationText =
        location?.longitude?.toString() ?: stringResource(Res.string.text_placeholder_location)
    // TODO: Use actual location text

    fun handleLocationClick() {
        onRequestLocationUpdate()
    }

    Column(
        modifier.padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Canvas(
                Modifier.size(16.dp),
            ) {
                drawCircle(lineColor, center = center, radius = size.width / 2f)
            }
            Text(
                creationTime.localTime,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .clip(MaterialTheme.shapes.small)
                    .clickable { onCreationTimeClick() }
                    .padding(
                        horizontal = Spacing.sm, vertical = Spacing.xs,
                    ),
            )

            Spacer(modifier = Modifier.weight(1f))
            LocationChip(
                location = locationText, enabled = locationEnabled, onClick = ::handleLocationClick,
            )
        }
        EditorField(
            expanded = expanded,
            contents = entryContents,
            onNoteUpdate = onNoteUpdate,
            placeholder = stringResource(Res.string.text_placeholder_start_writing)
        )
    }
}