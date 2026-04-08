@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.events.ui.calendarsync

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import app.logdate.shared.model.Event
import app.logdate.ui.common.SettingsScaffold
import app.logdate.ui.theme.Spacing
import app.logdate.util.toReadableDateTimeRangeShort
import logdate.client.feature.events.generated.resources.Res
import logdate.client.feature.events.generated.resources.calendar_sync_activity_empty
import logdate.client.feature.events.generated.resources.calendar_sync_activity_title
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import kotlin.uuid.Uuid

/**
 * Recent imports list. Shows every event LogDate mirrored from the device calendar,
 * sorted newest-first. Tapping a row navigates into the regular event detail screen via
 * [onNavigateToEvent] so the user can edit metadata or attach captures.
 */
@Composable
fun CalendarSyncActivityScreen(
    onBack: () -> Unit,
    onNavigateToEvent: (Uuid) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CalendarSyncActivityViewModel = koinViewModel(),
) {
    val events by viewModel.events.collectAsState()

    SettingsScaffold(
        title = stringResource(Res.string.calendar_sync_activity_title),
        onBack = onBack,
        modifier = modifier,
    ) {
        if (events.isEmpty()) {
            item {
                Text(
                    text = stringResource(Res.string.calendar_sync_activity_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.lg),
                )
            }
            return@SettingsScaffold
        }
        items(items = events, key = { event -> event.id.toString() }) { event ->
            ImportedEventRow(event = event, onClick = { onNavigateToEvent(event.id) })
        }
    }
}

@Composable
private fun ImportedEventRow(
    event: Event,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(event.title) },
        supportingContent = {
            Text(
                text = event.startTime.toReadableDateTimeRangeShort(event.endTime),
                style = MaterialTheme.typography.bodySmall,
            )
        },
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = Spacing.lg),
    )
}
