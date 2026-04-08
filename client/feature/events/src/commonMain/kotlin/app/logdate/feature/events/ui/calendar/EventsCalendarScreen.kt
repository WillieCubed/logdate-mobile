@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.events.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.logdate.shared.model.Event
import app.logdate.ui.theme.Spacing
import app.logdate.util.toReadableDateTimeRangeShort
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import logdate.client.feature.events.generated.resources.Res
import logdate.client.feature.events.generated.resources.events_calendar_empty_day
import logdate.client.feature.events.generated.resources.events_calendar_next_month
import logdate.client.feature.events.generated.resources.events_calendar_previous_month
import logdate.client.feature.events.generated.resources.events_calendar_title
import logdate.client.ui.generated.resources.common_back
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import kotlin.uuid.Uuid

/**
 * Month-grid calendar surface for inferred and imported events.
 *
 * Renders a 7-column grid for the visible month with day numbers and small dot
 * indicators where there are events. Tapping a day reveals the event list for that day
 * in the section below the grid; tapping an event navigates to the event detail screen.
 *
 * The screen reads from [EventsCalendarViewModel] which observes events reactively, so
 * the grid refreshes whenever the inference or calendar import worker materializes a
 * new event without the user having to pull-to-refresh.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventsCalendarScreen(
    onBack: () -> Unit,
    onNavigateToEvent: (Uuid) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: EventsCalendarViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.events_calendar_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(logdate.client.ui.generated.resources.Res.string.common_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .padding(padding)
                    .fillMaxSize(),
        ) {
            MonthHeader(
                displayedMonth = uiState.displayedMonth,
                onPrevious = viewModel::showPreviousMonth,
                onNext = viewModel::showNextMonth,
            )
            WeekdayHeaderRow()
            MonthGrid(
                displayedMonth = uiState.displayedMonth,
                today = uiState.today,
                selectedDay = uiState.selectedDay,
                eventsByDay = uiState.eventsByDay,
                onSelectDay = viewModel::selectDay,
            )
            Spacer(modifier = Modifier.height(Spacing.lg))
            DayEventsList(
                selectedDay = uiState.selectedDay,
                events = uiState.selectedDay?.let { uiState.eventsByDay[it] }.orEmpty(),
                onNavigateToEvent = onNavigateToEvent,
            )
        }
    }
}

@Composable
private fun MonthHeader(
    displayedMonth: LocalDate,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrevious) {
            Icon(
                Icons.Rounded.ChevronLeft,
                contentDescription = stringResource(Res.string.events_calendar_previous_month),
            )
        }
        Text(
            text = "${displayedMonth.month.name.lowercase().replaceFirstChar { it.uppercase() }} ${displayedMonth.year}",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.weight(1f),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        IconButton(onClick = onNext) {
            Icon(
                Icons.Rounded.ChevronRight,
                contentDescription = stringResource(Res.string.events_calendar_next_month),
            )
        }
    }
}

@Composable
private fun WeekdayHeaderRow() {
    // ISO weekday order, Monday-first. We display short two-letter labels in English; a
    // proper localized version (e.g. via java.text.DateFormatSymbols on Android) is a
    // good follow-up but matches the rest of the app's English-only screens for now.
    val labels = listOf("Mo", "Tu", "We", "Th", "Fr", "Sa", "Su")
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.sm)) {
        labels.forEach { label ->
            Box(
                modifier = Modifier.weight(1f).padding(vertical = Spacing.xs),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun MonthGrid(
    displayedMonth: LocalDate,
    today: LocalDate,
    selectedDay: LocalDate?,
    eventsByDay: Map<LocalDate, List<Event>>,
    onSelectDay: (LocalDate) -> Unit,
) {
    val firstWeekdayOffset = (displayedMonth.dayOfWeek.isoDayNumber - 1)
    val gridStart = displayedMonth.plus(-firstWeekdayOffset, DateTimeUnit.DAY)
    // 6 weeks × 7 columns = 42 cells covers every month.
    val cells = (0 until WEEKS_VISIBLE * 7).map { offset -> gridStart.plus(offset, DateTimeUnit.DAY) }
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.sm)) {
        cells.chunked(7).forEach { weekRow ->
            Row(modifier = Modifier.fillMaxWidth()) {
                weekRow.forEach { day ->
                    DayCell(
                        day = day,
                        isInDisplayedMonth = day.month == displayedMonth.month && day.year == displayedMonth.year,
                        isToday = day == today,
                        isSelected = day == selectedDay,
                        eventCount = eventsByDay[day]?.size ?: 0,
                        onClick = { onSelectDay(day) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    day: LocalDate,
    isInDisplayedMonth: Boolean,
    isToday: Boolean,
    isSelected: Boolean,
    eventCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cellBackground =
        when {
            isSelected -> MaterialTheme.colorScheme.primaryContainer
            isToday -> MaterialTheme.colorScheme.surfaceContainerHigh
            else -> MaterialTheme.colorScheme.surface
        }
    val numberColor =
        when {
            isSelected -> MaterialTheme.colorScheme.onPrimaryContainer
            !isInDisplayedMonth -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            else -> MaterialTheme.colorScheme.onSurface
        }
    Box(
        modifier =
            modifier
                .aspectRatio(1f)
                .padding(2.dp)
                .clip(MaterialTheme.shapes.small)
                .background(cellBackground)
                .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = day.day.toString(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                color = numberColor,
            )
            if (eventCount > 0) {
                Spacer(modifier = Modifier.height(2.dp))
                Box(
                    modifier =
                        Modifier
                            .size(4.dp)
                            .clip(CircleShape)
                            .background(
                                if (isSelected) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.primary
                                },
                            ),
                )
            }
        }
    }
}

@Composable
private fun DayEventsList(
    selectedDay: LocalDate?,
    events: List<Event>,
    onNavigateToEvent: (Uuid) -> Unit,
) {
    if (selectedDay == null) return
    if (events.isEmpty()) {
        Text(
            text = stringResource(Res.string.events_calendar_empty_day),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md),
        )
        return
    }
    LazyColumn(modifier = Modifier.fillMaxWidth()) {
        items(items = events, key = { event -> event.id.toString() }) { event ->
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
                        .clickable { onNavigateToEvent(event.id) }
                        .padding(horizontal = Spacing.lg),
            )
        }
    }
}

private val DayOfWeek.isoDayNumber: Int get() = ordinal + 1

private const val WEEKS_VISIBLE = 6
