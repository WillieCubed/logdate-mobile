@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.events.ui.calendarsync

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.logdate.shared.model.Event
import app.logdate.ui.adaptive.FoldableBookLayout
import app.logdate.ui.common.MaterialContainer
import app.logdate.ui.common.SettingsScaffold
import app.logdate.ui.theme.Spacing
import app.logdate.util.toReadableDateTimeRangeShort
import logdate.client.feature.events.generated.resources.Res
import logdate.client.feature.events.generated.resources.calendar_sync_activity_empty
import logdate.client.feature.events.generated.resources.calendar_sync_activity_title
import logdate.client.feature.events.generated.resources.calendar_sync_settings_activity_row_subtitle
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

    CalendarSyncActivityContent(
        events = events,
        onBack = onBack,
        onNavigateToEvent = onNavigateToEvent,
        modifier = modifier,
    )
}

@Composable
fun CalendarSyncActivityContent(
    events: List<Event>,
    onBack: () -> Unit,
    onNavigateToEvent: (Uuid) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isEmpty = events.isEmpty()

    FoldableBookLayout(
        modifier = modifier.fillMaxSize(),
        minPaneWidth = 320.dp,
        startPane = {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.lg),
            ) {
                ImportedEventsSummaryHeader(importedCount = events.size)
            }
        },
        endPane = {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.lg),
            ) {
                if (isEmpty) {
                    Text(
                        text = stringResource(Res.string.calendar_sync_activity_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = Spacing.lg),
                    )
                } else {
                    events.forEach { event ->
                        ImportedEventRow(event = event, onClick = { onNavigateToEvent(event.id) })
                    }
                }
            }
        },
        standardContent = {
            SettingsScaffold(
                title = stringResource(Res.string.calendar_sync_activity_title),
                onBack = onBack,
                modifier = modifier,
            ) {
                if (isEmpty) {
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
        },
    )
}

/**
 * Context summary shown in the start pane of the book-posture split: how many events
 * LogDate has mirrored so far and a short line describing where they came from. The list
 * of rows lives in the end pane, keeping the split divided into "status" and "browse".
 */
@Composable
private fun ImportedEventsSummaryHeader(importedCount: Int) {
    MaterialContainer(
        modifier = Modifier.padding(horizontal = Spacing.lg),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(
                text = stringResource(Res.string.calendar_sync_activity_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = importedCount.toString(),
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                text = stringResource(Res.string.calendar_sync_settings_activity_row_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
                text = event.startTime.toReadableDateTimeRangeShort(end = event.endTime, isAllDay = event.isAllDay),
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
