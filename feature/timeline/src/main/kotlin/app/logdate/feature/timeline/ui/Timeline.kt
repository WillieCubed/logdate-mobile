package app.logdate.feature.timeline.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.dp
import app.logdate.core.data.notes.JournalNote
import app.logdate.feature.timeline.R
import app.logdate.ui.theme.Spacing
import app.logdate.util.toReadableDateShort
import app.logdate.util.weeksAgo
import kotlinx.datetime.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

@Composable
internal fun Timeline(
    timelineItems: List<JournalNote>,
    onItemSelected: (uid: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier
    ) {
        constructTimeline(items = timelineItems, onItemSelected = onItemSelected)
        item {
            // TODO: Fetch origin date from user data
            // TODO: Display birthday if there are items before date of first onboarding, "journey begins"
            TimelineOriginItem(originDate = TEST_ORIGIN_DATE)
        }
    }
}

@Composable
private fun TimelineContentHeader(title: String) {
    Row(
        modifier = Modifier
            .height(56.dp)
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm)
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
        IconButton(onClick = { /*TODO*/ }) {
            Icon(imageVector = Icons.Default.MoreVert, contentDescription = "More")
        }
    }
}

@Composable
private fun TimelineContentItem(item: JournalNote, onItemSelected: (uid: String) -> Unit) {
    var showOptions by rememberSaveable { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm)
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        TimelineLine(
            modifier = Modifier.padding(top = Spacing.lg),
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) { // Content container
            Row(
                modifier = Modifier
//                    .heightIn(min = 72.dp)
//                    .padding(vertical = Spacing.sm)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
                verticalAlignment = Alignment.CenterVertically,
            ) {// Header block
                // TODO: Add metadata row
                Text(
                    item.creationTimestamp.toReadableDateShort(),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge
                )
                IconButton(onClick = { showOptions = !showOptions }) {
                    Icon(imageVector = Icons.Default.MoreVert, contentDescription = "More")
                }
            }
            // Actual content
            when (item) {
                is JournalNote.Text -> {
                    Text(
                        item.content,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            lineBreak = LineBreak.Paragraph,
                        ),
                    )
                }

                else -> {
                    // TODO: Handle other types of journal notes
                }
            }
        }
    }
}

@Composable
private fun determineHeaderTitle(date: Instant): String {
    val localDateTime = date.toLocalDateTime(TimeZone.currentSystemDefault())
    val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())

    return when (localDateTime.date) {
        // If the date is within this week, show "This Week"
        in today.date.minus(DatePeriod(days = 1))..today.date.plus(DatePeriod(days = 7)) -> {
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
            stringResource(R.string.period_years_ago, yearsAgo)
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
            localDateTime.toReadableDateShort()
        }
    }
}

/**
 * Converts a sequential list of [TimelineItem]s into a timeline view.
 * This creates a list of [TimelineContentItem]s and [TimelineContentHeader]s
 * to represent the timeline, dividing items by week.
 */
fun LazyListScope.constructTimeline(
    items: List<JournalNote>,
    onItemSelected: (uid: String) -> Unit,
) {
    // Sort items in reverse order, grouping them by weeks before this week, adding headers for each week.
    val groupedItems = items.sortedByDescending { it.creationTimestamp }.groupBy {
        val weeksBeforeNow = it.creationTimestamp.weeksAgo()
        weeksBeforeNow
    }

    // Now we have a map of week number to list of items for that week.
    groupedItems.forEach { (week, items) ->
        item {
            TimelineContentHeader(determineHeaderTitle(date = items.first().creationTimestamp))
        }
        items.forEach { item ->
            item {
                TimelineContentItem(item, onItemSelected)
            }
        }
    }
}