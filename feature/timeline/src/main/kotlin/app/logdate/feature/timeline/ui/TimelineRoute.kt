package app.logdate.feature.timeline.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import app.logdate.feature.timeline.R
import app.logdate.model.TimelineItem
import app.logdate.ui.theme.Spacing
import kotlinx.datetime.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

@Composable
fun TimelineRoute(
    onOpenTimelineItem: (uid: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TimelineViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    TimelineScreen(state, onOpenTimelineItem, modifier)
}

@Composable
internal fun TimelineScreen(
    state: TimelineUiState,
    onOpenTimelineItem: (uid: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state) {
        TimelineUiState.Loading -> TimelineLoadingPlaceholder(modifier)
        is TimelineUiState.Success -> Content(state, onOpenTimelineItem, modifier)
    }
}

@Composable
internal fun Content(
    state: TimelineUiState.Success,
    onItemSelected: (uid: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Timeline(state.items, onItemSelected, modifier = modifier)
}

@Composable
internal fun Timeline(
    timelineItems: List<TimelineItem>,
    onItemSelected: (uid: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier
    ) {
        constructTimeline(items = timelineItems, onItemSelected = onItemSelected)
    }
}

@Composable
internal fun TimelineContentHeader(title: String) {
    Row(
        modifier = Modifier
            .height(56.dp)
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title)
        IconButton(onClick = { /*TODO*/ }) {
            Icon(imageVector = Icons.Default.MoreVert, contentDescription = "More")
        }
    }
}

@Composable
internal fun TimelineLine() {
    val color = MaterialTheme.colorScheme.primary
    Canvas(modifier = Modifier.width(16.dp)) {
        drawCircle(color, radius = 8.dp.toPx())
        drawLine(
            start = Offset(x = 8f, y = 0f),
            end = Offset(x = 8f, y = size.height),
            strokeWidth = 2f,
            color = color,
        )
    }
}

@Preview
@Composable
fun TimelineLinePreview() {
    TimelineLine()
}

@Composable
internal fun TimelineContentItem(item: TimelineItem, onItemSelected: (uid: String) -> Unit) {
    var showOptions by remember { mutableStateOf(false) }
    Row {
        TimelineLine()
        Column { // Content container
            Row(
                modifier = Modifier
                    .height(72.dp)
                    .padding(vertical = Spacing.sm),
                horizontalArrangement = Arrangement.spacedBy(Spacing.lg, Alignment.Start),
                verticalAlignment = Alignment.CenterVertically,
            ) {// Header block
                Column(
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    horizontalAlignment = Alignment.Start,
                ) { /// Title section

                }
                IconButton(onClick = { showOptions = !showOptions }) {
                    Icon(imageVector = Icons.Default.MoreVert, contentDescription = "More")
                }
            }
            Text(item.date.toString())
        }
    }
}

@Composable
private fun determineHeaderTitle(date: Instant): String {
    val localDateTime = date.toLocalDateTime(TimeZone.currentSystemDefault())
    val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())

    val context = LocalContext.current
    return when (localDateTime.date) {
        // If the date is within this week, show "This Week"
        in today.date..today.date.plus(DatePeriod(days = 7)) -> {
            stringResource(R.string.period_this_week)
        }
        // If the date is within the previous week, show "Last Week"
        in today.date.minus(DatePeriod(days = 7))..today.date -> {
            stringResource(R.string.period_last_week)
        }
        // If the date is within the last month, show the number of weeks ago
        in today.date.minus(DatePeriod(days = 30))..today.date -> {
            val weeksAgo = (today.date.dayOfYear - localDateTime.date.dayOfYear) / 7
            stringResource(R.string.period_weeks_ago, weeksAgo)
        }
        // If the date was more than five weeks ago, show the number of months ago
        in today.date.minus(DatePeriod(days = 365))..today.date -> {
            val monthsAgo = (today.date.dayOfYear - localDateTime.date.dayOfYear) / 30
            stringResource(R.string.period_months_ago, monthsAgo)
        }
        // If the date was more than a year ago, show the number of years ago
        else -> {
            val yearsAgo = today.date.year - localDateTime.date.year
            "$yearsAgo years ago"
        }

    }
}

private fun determineTitle(date: Instant): String {
    val localDateTime = date.toLocalDateTime(TimeZone.currentSystemDefault())
    val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
    return when (localDateTime.date) {
        // If the date is today, show "Today"
        today.date -> {
            "Today"
        }
        // If the date is yesterday, show "Yesterday"
        today.date.minus(DatePeriod(days = 1)) -> {
            "Yesterday"
        }
        // If the date is within the last calendar week, show the day of the week
        in today.date.minus(DatePeriod(days = 7))..today.date -> {
            localDateTime.date.dayOfWeek.name
        }
        // Otherwise, show the date
        else -> {
            localDateTime.date.toString()
        }
    }
}

/**
 * Converts a sequential list of [TimelineItem]s into a timeline view.
 * This creates a list of [TimelineContentItem]s and [TimelineContentHeader]s
 * to represent the timeline, dividing items by week.
 */
fun LazyListScope.constructTimeline(
    items: List<TimelineItem>,
    onItemSelected: (uid: String) -> Unit,
) {
    // Sort items in reverse order, grouping them by weeks before this week, adding headers for each week.
    val groupedItems = items.sortedByDescending { it.date }.groupBy {
        val weeksBeforeNow = it.date.weeksAgo()
        weeksBeforeNow
    }

    // Now we have a map of week number to list of items for that week.
    groupedItems.forEach { (week, items) ->
        item {
            TimelineContentHeader(determineHeaderTitle(date = items.first().date))
        }
        items.forEach { item ->
            item {
                TimelineContentItem(item, onItemSelected)
            }
        }
    }


}

/**
 * Returns the number of weeks between this [Instant] and the current time.
 *
 * Rounds down to the nearest week.
 */
private fun Instant.weeksAgo(): Int {
    val now = Clock.System.now()
    val days = (now.epochSeconds - this.epochSeconds) / (60 * 60 * 24)
    return (days / 7).toInt()
}
