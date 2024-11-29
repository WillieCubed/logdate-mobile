package app.logdate.feature.timeline.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Note
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.PeopleAlt
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import app.logdate.client.repository.journals.JournalNote
import app.logdate.ui.theme.Spacing
import app.logdate.ui.timeline.TimelineLine
import app.logdate.util.toReadableDateShort
import app.logdate.util.weeksAgo
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownTypography
import kotlinx.datetime.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import logdate.client.feature.timeline.generated.resources.Res
import logdate.client.feature.timeline.generated.resources.period_last_week
import logdate.client.feature.timeline.generated.resources.period_months_ago
import logdate.client.feature.timeline.generated.resources.period_this_week
import logdate.client.feature.timeline.generated.resources.period_weeks_ago
import logdate.client.feature.timeline.generated.resources.period_years_ago
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Duration.Companion.hours


/**
 * Checks that there is an entry within the past eight hours.
 */
internal fun hasEntryFromToday(timelineItems: List<JournalNote>): Boolean {
    val now = Clock.System.now()
    val eightHoursAgo = now.minus(8.hours)
    return timelineItems.any { it.creationTimestamp > eightHoursAgo }
}

// TODO: Move to :client:ui
@Composable
internal fun Timeline(
    timelineItems: List<JournalNote>,
    onItemSelected: (uid: String) -> Unit,
    onItemDeleted: (uid: String) -> Unit,
    onNewEntry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier, contentPadding = PaddingValues(top = Spacing.xl)
    ) {
        constructTimeline(
            items = timelineItems,
            onItemSelected = onItemSelected,
            onItemDeleted = onItemDeleted,
            onNewEntry = onNewEntry,
        )
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
    }
}

enum class TimeDetail {
    /**
     *
     */
    DAY,

    /**
     *
     */
    HOUR,
}

/**
 * A dropdown menu that displays options for a timeline entry.
 */
@Composable
private fun EntryDropdownMenu(
    isExpanded: Boolean,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    DropdownMenu(
        expanded = isExpanded, onDismissRequest = onDismiss,
        modifier = modifier,
    ) {
        // TODO: Re-enable edit functionality when editing is implemented
//        DropdownMenuItem(
//            text = {
//                Text("Edit")
//            },
//            onClick = onEdit,
//            leadingIcon = {
//                Icon(imageVector = Icons.Default.Edit, contentDescription = null)
//            },
//        )
        DropdownMenuItem(
            text = {
                Text("Delete")
            },
            onClick = onDelete,
            leadingIcon = {
                Icon(imageVector = Icons.Default.Delete, contentDescription = null)
            },
        )
    }
}

data class TimelineItemMetadata(
    val peopleSeen: Int = 0,
    val placesVisited: Int = 0,
    val notesRecorded: Int = 0,
)

@Composable
private fun TimelineItemMetadataBlock(
    metadata: TimelineItemMetadata,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        if (metadata.peopleSeen > 0) {
            MetadataItem(
                text = "${metadata.peopleSeen} people",
                icon = {
                    Icon(imageVector = Icons.Outlined.PeopleAlt, contentDescription = "People")
                },
            )
        }
        if (metadata.placesVisited > 0) {
            MetadataItem(
                text = "${metadata.placesVisited} places",
                icon = {
                    Icon(imageVector = Icons.Default.LocationOn, contentDescription = "Places")
                },
            )
        }
        if (metadata.notesRecorded > 0) {
            MetadataItem(
                text = "${metadata.notesRecorded} notes",
                icon = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Default.Note,
                        contentDescription = "Notes"
                    )
                },
            )
        }
    }
}


@Composable
internal fun MetadataItem(
    text: String,
    icon: @Composable () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Box(modifier = Modifier.size(20.dp))
        {
            icon()
        }
        Text(text = text)
    }
}

@Composable
private fun TimelineContentItem(
    item: JournalNote,
    metadata: TimelineItemMetadata,
    timeDetail: TimeDetail = TimeDetail.DAY,
    onItemSelected: (uid: String) -> Unit,
    onDeleteItem: (uid: String) -> Unit = {},
) {
    var showOptions by rememberSaveable { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .clip(MaterialTheme.shapes.medium)
            .clickable {
                onItemSelected(item.uid)
            }
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        TimelineLine(
            modifier = Modifier
                .padding(top = Spacing.lg)
                .fillMaxHeight(),
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) { // Content container
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
                verticalAlignment = Alignment.CenterVertically,
            ) { // Header block
                Column(
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = Spacing.sm),
                ) {
                    Text(
                        item.creationTimestamp.toReadableDateShort(),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    TimelineItemMetadataBlock(metadata = metadata)
                }
                Box(
                    modifier = Modifier
                        .wrapContentSize(Alignment.TopEnd),
                ) {
                    EntryDropdownMenu(
                        isExpanded = showOptions,
                        onDismiss = { showOptions = false },
                        onEdit = {
                            showOptions = false
                        },
                        onDelete = {
                            showOptions = false
                            onDeleteItem(item.uid)
                        },
                    )
                    IconButton(onClick = { showOptions = true }) {
                        Icon(imageVector = Icons.Default.MoreVert, contentDescription = "More")
                    }
                }
            }
            // Actual content
            when (item) {
                is JournalNote.Text -> {
                    Markdown(
                        content = item.content,
                        typography = markdownTypography(
                            text = MaterialTheme.typography.bodyMedium.copy(
                                lineBreak = MaterialTheme.typography.bodyMedium.lineBreak,
                            )
                        )
                    )
//                    Text(
//                        item.content,
//                        style = MaterialTheme.typography.bodyMedium.copy(
//                            lineBreak = LineBreak.Paragraph,
//                        ),
//                    )
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
            stringResource(Res.string.period_this_week)
        }
        // If the date is within the previous week, show "Last Week"
        in today.date.minus(DatePeriod(days = 7))..today.date -> {
            stringResource(Res.string.period_last_week)
        }
        // If the date is within the last month, show the number of weeks ago
        in today.date.minus(DatePeriod(days = 30))..today.date -> {
            val weeksAgo = (today.date.dayOfYear - localDateTime.date.dayOfYear) / 7
            pluralStringResource(Res.plurals.period_weeks_ago, weeksAgo, weeksAgo)
        }
        // If the date was more than five weeks ago, show the number of months ago
        in today.date.minus(DatePeriod(days = 365))..today.date -> {
            val monthsAgo = (today.date.dayOfYear - localDateTime.date.dayOfYear) / 30
            pluralStringResource(Res.plurals.period_months_ago, monthsAgo, monthsAgo)
        }
        // If the date was more than a year ago, show the number of years ago
        else -> {
            val yearsAgo = today.date.year - localDateTime.date.year
            stringResource(Res.string.period_years_ago, yearsAgo)
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

enum class ZoomLevel {
    /**
     * Group by day
     */
    DEFAULT,

    /**
     * Show every note under a separate header.
     *
     * If there are multiple entries under the same day, the date will only appear once, and each
     * item should have its own recorded time.
     */
    DETAILED,
}

/**
 * Converts a sequential list of [TimelineItem]s into a timeline view.
 * This creates a list of [TimelineContentItem]s and [TimelineContentHeader]s
 * to represent the timeline, dividing items by week.
 */
fun LazyListScope.constructTimeline(
    items: List<JournalNote>,
    onNewEntry: () -> Unit,
    onItemSelected: (uid: String) -> Unit,
    onItemDeleted: (uid: String) -> Unit,
    zoomLevel: ZoomLevel = ZoomLevel.DETAILED,
) {
    // Sort items in reverse order, grouping them by weeks before this week, adding headers for each week.
    val itemsGroupedByWeek = items.sortedByDescending { it.creationTimestamp }.groupBy {
        val weeksBeforeNow = it.creationTimestamp.weeksAgo()
        weeksBeforeNow
    }

    // Group items by day, sort in reverse order of created


    // Now we have a map of week number to list of items for that week.
    itemsGroupedByWeek.forEach { (_, items) ->
        item {
            TimelineContentHeader(determineHeaderTitle(date = items.first().creationTimestamp))
        }
        // If there is no entry from today, show the default new entry block
        if (!hasEntryFromToday(items)) {
            item {
                DefaultNewEntryBlock(
                    onNewEntry = onNewEntry,
                    message = "No notes from today yet!",
                )
            }
        }
        items.forEachIndexed { _, item ->
            when (zoomLevel) {
                ZoomLevel.DETAILED -> {
                    item {
                        TimelineContentItem(
                            item,
                            metadata = TimelineItemMetadata(
                                notesRecorded = 1,
                            ),
                            onItemSelected = onItemSelected,
                            onDeleteItem = onItemDeleted,
                        )
                    }
                }

                ZoomLevel.DEFAULT -> {

                }
            }
        }
    }
}


@Composable
fun PinchableContainer(
    defaultContent: @Composable () -> Unit,
    expandedContent: @Composable () -> Unit,
    label: String = "Pinchable Container",
) {
    var expanded by remember { mutableStateOf(false) }
    AnimatedContent(targetState = expanded, label = label) { isExpanded ->
        if (isExpanded) {
            expandedContent()
        } else {
            defaultContent()
        }
    }

}