@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.events.ui.calendarsync

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import app.logdate.client.calendar.DeviceCalendar
import app.logdate.ui.common.SettingsScaffold
import app.logdate.ui.theme.Spacing
import logdate.client.feature.events.generated.resources.Res
import logdate.client.feature.events.generated.resources.calendar_sync_calendars_empty
import logdate.client.feature.events.generated.resources.calendar_sync_calendars_title
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * Per-account calendar picker. Loaded from `DeviceCalendarReader.listCalendars()` on
 * entry, grouped by account name, with a checkbox per row. The selection is committed to
 * preferences when the user taps back, so the next periodic worker run picks it up.
 */
@Composable
fun CalendarSyncCalendarsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CalendarSyncCalendarsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()

    CalendarSyncCalendarsContent(
        state = state,
        onBack = {
            viewModel.save(onBack)
        },
        onToggle = viewModel::toggleCalendar,
        modifier = modifier,
    )
}

@Composable
fun CalendarSyncCalendarsContent(
    state: CalendarSyncCalendarsUiState,
    onBack: () -> Unit,
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsScaffold(
        title = stringResource(Res.string.calendar_sync_calendars_title),
        onBack = onBack,
        modifier = modifier,
    ) {
        if (state.calendars.isEmpty() && !state.isLoading) {
            item {
                Text(
                    text = stringResource(Res.string.calendar_sync_calendars_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.lg),
                )
            }
            return@SettingsScaffold
        }

        // Group by account so the user sees one section per Google/iCloud/local account
        // they have on the device. The headers are non-interactive labels — only the
        // calendar rows themselves toggle.
        val grouped = state.calendars.groupBy { it.accountName }
        for ((accountName, calendars) in grouped) {
            item(key = "header:$accountName") {
                Text(
                    text = accountName,
                    style = MaterialTheme.typography.titleSmall,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                )
            }
            calendarRows(calendars, state.selectedIds, onToggle)
        }
    }
}

private fun LazyListScope.calendarRows(
    calendars: List<DeviceCalendar>,
    selectedIds: Set<String>,
    onToggle: (String) -> Unit,
) {
    items(items = calendars, key = { calendar -> "calendar:${calendar.id}" }) { calendar ->
        CalendarRow(calendar = calendar, selected = calendar.id in selectedIds, onToggle = onToggle)
    }
}

@Composable
private fun CalendarRow(
    calendar: DeviceCalendar,
    selected: Boolean,
    onToggle: (String) -> Unit,
) {
    ListItem(
        headlineContent = { Text(calendar.displayName) },
        trailingContent = {
            Checkbox(
                checked = selected,
                onCheckedChange = { onToggle(calendar.id) },
            )
        },
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable { onToggle(calendar.id) }
                .padding(horizontal = Spacing.lg),
    )
}
