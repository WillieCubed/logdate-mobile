@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.events.ui.calendarsync

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.logdate.client.calendar.DeviceCalendar
import app.logdate.ui.adaptive.FoldableBookLayout
import app.logdate.ui.common.MaterialContainer
import app.logdate.ui.common.SettingsScaffold
import app.logdate.ui.theme.Spacing
import logdate.client.feature.events.generated.resources.Res
import logdate.client.feature.events.generated.resources.calendar_sync_calendars_default_label
import logdate.client.feature.events.generated.resources.calendar_sync_calendars_empty
import logdate.client.feature.events.generated.resources.calendar_sync_calendars_title
import logdate.client.feature.events.generated.resources.calendar_sync_settings_calendars_row_subtitle_none
import logdate.client.feature.events.generated.resources.calendar_sync_settings_calendars_row_subtitle_some
import logdate.client.feature.events.generated.resources.calendar_sync_settings_grant_rationale
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * Per-account calendar picker.
 *
 * Calendars are loaded once on entry, grouped by their account name, and rendered using
 * the same MaterialContainer + ListItem pattern the rest of the settings screens use
 * (see `MemoriesSettingsScreen`). Each row has a leading colored badge in the calendar's
 * provider color, the calendar name as the headline, an optional "Default calendar"
 * supporting line for the user's primary calendar, and a trailing Material switch.
 * Toggles auto-save through the ViewModel — there is no save button.
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
        onBack = onBack,
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
    // Group once per calendar-list change so toggling a selection doesn't re-bucket on
    // every recomposition.
    val groupedByAccount =
        remember(state.calendars) {
            state.calendars.groupBy { it.accountName }
        }
    val isEmpty = state.calendars.isEmpty() && !state.isLoading

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
                CalendarsSummaryHeader(state = state)
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
                        text = stringResource(Res.string.calendar_sync_calendars_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = Spacing.lg),
                    )
                } else {
                    for ((accountName, calendars) in groupedByAccount) {
                        AccountSection(
                            accountName = accountName,
                            calendars = calendars,
                            selectedIds = state.selectedIds,
                            onToggle = onToggle,
                        )
                    }
                }
            }
        },
        standardContent = {
            SettingsScaffold(
                title = stringResource(Res.string.calendar_sync_calendars_title),
                onBack = onBack,
                modifier = modifier,
            ) {
                if (isEmpty) {
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

                for ((accountName, calendars) in groupedByAccount) {
                    item(key = "account:$accountName") {
                        AccountSection(
                            accountName = accountName,
                            calendars = calendars,
                            selectedIds = state.selectedIds,
                            onToggle = onToggle,
                        )
                    }
                }
            }
        },
    )
}

/**
 * Context summary shown in the start pane of the book-posture split: how many calendars
 * the device exposed, how many the user already mirrors, and a short reassurance line
 * about read-only access. Carries no controls — the picker in the end pane owns every
 * toggle — so the two panes stay cleanly divided into "what is this" and "choose".
 */
@Composable
private fun CalendarsSummaryHeader(state: CalendarSyncCalendarsUiState) {
    val total = state.calendars.size
    val selectedCount = state.calendars.count { it.id in state.selectedIds }
    val selectionLabel =
        if (selectedCount == 0) {
            stringResource(Res.string.calendar_sync_settings_calendars_row_subtitle_none)
        } else {
            stringResource(
                Res.string.calendar_sync_settings_calendars_row_subtitle_some,
                selectedCount,
                total,
            )
        }

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
                text = stringResource(Res.string.calendar_sync_calendars_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = selectionLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(Res.string.calendar_sync_settings_grant_rationale),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AccountSection(
    accountName: String,
    calendars: List<DeviceCalendar>,
    selectedIds: Set<String>,
    onToggle: (String) -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(
            text = accountName,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(vertical = Spacing.sm),
        )
        MaterialContainer {
            calendars.forEach { calendar ->
                CalendarRow(
                    calendar = calendar,
                    selected = calendar.id in selectedIds,
                    onToggle = onToggle,
                )
            }
        }
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
        supportingContent =
            if (calendar.isPrimary) {
                {
                    Text(
                        text = stringResource(Res.string.calendar_sync_calendars_default_label),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                null
            },
        leadingContent = { CalendarColorBadge(color = calendar.color) },
        trailingContent = {
            Switch(
                checked = selected,
                onCheckedChange = { onToggle(calendar.id) },
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable { onToggle(calendar.id) },
    )
}

@Composable
private fun CalendarColorBadge(color: Long?) {
    val accent = color?.let { argb -> Color(argb.toInt()) } ?: MaterialTheme.colorScheme.primary
    Box(
        modifier =
            Modifier
                .size(40.dp)
                .clip(MaterialTheme.shapes.medium)
                .background(accent),
    )
}
