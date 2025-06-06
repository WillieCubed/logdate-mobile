@file:OptIn(ExperimentalLayoutApi::class)

package app.logdate.ui.timeline.newstuff

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PeopleAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import app.logdate.ui.common.applyPaddingIfLast
import app.logdate.ui.common.formatting.asRelativeDate
import app.logdate.ui.location.PlaceUiState
import app.logdate.ui.profiles.PersonUiState
import app.logdate.ui.theme.Spacing
import app.logdate.ui.timeline.TimelineDayUiState
import app.logdate.ui.timeline.TimelineSuggestionBlock
import app.logdate.ui.timeline.TimelineSuggestionBlockUiState
import app.logdate.util.now
import coil3.compose.AsyncImage
import kotlinx.datetime.LocalDate
import kotlin.math.absoluteValue
import kotlin.uuid.Uuid

/**
 * UI state for the end of timeline section.
 */
sealed class EndOfTimelineUiState {
    /**
     * State when the user has set their birthday.
     * 
     * @param birthDate The user's birth date
     * @param daysSinceBirth Number of days since the user was born
     */
    data class BirthdayCelebration(
        val birthDate: LocalDate, 
        val daysSinceBirth: Int
    ) : EndOfTimelineUiState()
    
    /**
     * State when the user has not set their birthday.
     * Shows an easter egg message encouraging them to add their birthday.
     */
    data object DiscoveryEasterEgg : EndOfTimelineUiState()
}

@Composable
fun TimelineList(
    items: List<TimelineDayUiState>,
    endOfTimelineState: EndOfTimelineUiState,
    onOpenDay: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    showSuggestedEntryBlock: Boolean = true,
    onAddToMemory: (memoryId: String) -> Unit = {},
    onShare: (memoryId: String) -> Unit = {},
    listState: LazyListState = rememberLazyListState(),
) {
    LazyColumn(
        modifier = modifier.safeDrawingPadding(),
        state = listState,
//        contentPadding = PaddingValues(vertical = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.xl),
    ) {
        item {
            AnimatedVisibility(visible = showSuggestedEntryBlock) {
                TimelineSuggestionBlock(
                    state = TimelineSuggestionBlockUiState(
                        memoryId = "1",
                        message = "You haven't added any memories today.",
                    ),
                    onAddToMemory = onAddToMemory,
                    onShare = onShare,
                    modifier = Modifier.padding(Spacing.lg)
                )
            }
        }

        itemsIndexed(
            items,
            key = { _, item -> item.date.toString() },
        ) { index, item ->
            TimelineDayListItem(
                item = item,
                onOpenDay = onOpenDay,
                modifier = Modifier
                    .applyPaddingIfLast(
                        currentIndex = index,
                        totalItems = items.size,
                    ),
            )
            // Check if we should show the time gap message
            val isNotLastItem = index < items.size - 1
            if (isNotLastItem) {
                val currentDate = item.date
                val nextDate = items[index + 1].date
                val daysBetween = (currentDate.toEpochDays() - nextDate.toEpochDays()).absoluteValue
                val shouldShowTimeGapMessage = daysBetween > 10

                if (shouldShowTimeGapMessage) {
                    TimeGapMessageItem()
                }
            }
        }
        // Always show an end-of-timeline item
        item {
            TimeGapMessageItem()
        }
        item {
            EndOfTimelineItem(endOfTimelineState)
        }
    }
}

@Composable
internal fun TimeGapMessageItem(
    modifier: Modifier = Modifier,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(
            Spacing.lg,
            alignment = Alignment.CenterVertically,
        ),
        modifier = modifier
            .defaultMinSize(minWidth = 320.dp)
            .padding(Spacing.lg),
    ) {
        Text(
            text = "A long time passed...",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * A composable that displays content at the end of the timeline.
 * Shows either a birthday celebration or an easter egg based on the provided state.
 * 
 * @param state The UI state for the end of timeline section
 * @param modifier Modifier to be applied to the container
 */
@Composable
internal fun EndOfTimelineItem(
    state: EndOfTimelineUiState,
    modifier: Modifier = Modifier,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        modifier = modifier
            .defaultMinSize(minWidth = 320.dp)
            .height(320.dp)
            .padding(Spacing.lg),
    ) {
        when (state) {
            is EndOfTimelineUiState.BirthdayCelebration -> {
                Text(state.birthDate.asRelativeDate(), style = MaterialTheme.typography.titleLarge)
                Column(
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    Text(
                        text = "Happy birthday.",
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    Text(
                        text = "You've been on this journey for ${state.daysSinceBirth} days.",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            
            EndOfTimelineUiState.DiscoveryEasterEgg -> {
                Text("You've reached the end", style = MaterialTheme.typography.titleLarge)
                Column(
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    Text(
                        text = "Congrats, curious explorer!",
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    Text(
                        text = "Add your birthday in settings to see something special here.",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * A timeline item that displays a birthday message.
 * This is kept for backward compatibility.
 *
 * @param birthDate The user's birth date
 * @param modifier A modifier to apply to the item container
 */
@Composable
internal fun BirthdayListItem(
    birthDate: LocalDate,
    modifier: Modifier = Modifier,
) {
    val daysSinceBirthday by remember(birthDate) {
        derivedStateOf {
            LocalDate.now().toEpochDays() - birthDate.toEpochDays()
        }
    }

    EndOfTimelineItem(
        state = EndOfTimelineUiState.BirthdayCelebration(
            birthDate = birthDate,
            daysSinceBirth = daysSinceBirthday
        ),
        modifier = modifier
    )
}

@Composable
internal fun TimelineDayListItem(
    item: TimelineDayUiState,
    onOpenDay: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .widthIn(min = 320.dp)
            .clickable {
                onOpenDay(item.date)
            }
            .padding(Spacing.lg),
    ) {
        TimelineDayHeader(
            date = item.date,
            places = item.placesVisited,
            people = item.people,
        )
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = MaterialTheme.shapes.small,
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                modifier = Modifier.padding(Spacing.sm),
            ) {
                Text(
                    "In summary",
                    style = MaterialTheme.typography.labelLarge.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                )
                Text(item.summary, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

sealed interface ActivitySectionUiState {
    val title: String
}

data class TextSnippetSectionUiState(
    override val title: String,
    val content: String,
) : ActivitySectionUiState

data class QuoteSnippetSectionUiState(
    override val title: String,
    val quote: String,
) : ActivitySectionUiState

data class MediaUiState(
    val mediaUri: String,
    val caption: String,
)

data class MediaSectionUiState(
    override val title: String,
    val media: List<MediaUiState>,
) : ActivitySectionUiState

@Composable
internal fun ActivitySection(uiState: ActivitySectionUiState) {
    when (uiState) {
        is QuoteSnippetSectionUiState -> {
            ActivitySectionWrapper(title = uiState.title) {
                Text(uiState.quote, style = MaterialTheme.typography.titleSmall)
            }
        }

        is MediaSectionUiState -> {
            ActivitySectionWrapper(title = uiState.title) {
                // TODO: Implement logic to display images in a visually aesthetic way
                FlowRow(
                ) {
                    uiState.media.forEach { media ->
                        AsyncImage(
                            model = media.mediaUri,
                            contentDescription = media.caption,
                            modifier = Modifier.height(200.dp),
                        )
                    }
                }
            }
        }

        is TextSnippetSectionUiState -> {
            ActivitySectionWrapper(title = uiState.title) {
                Text(uiState.content, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
internal fun ActivitySectionWrapper(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = Spacing.sm),
        )
        content()
    }

}

@Composable
private fun TimelineDayHeader(
    date: LocalDate,
    places: List<PlaceUiState>,
    people: List<PersonUiState>,
    // TODO: Implement click handlers
    onOpenPlace: (uid: Uuid) -> Unit = {},
    onOpenPeople: (uid: Uuid) -> Unit = {},
) {
    // TODO: Implement hoverable pop-ups for places and people
    Column(
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text(date.asRelativeDate(), style = MaterialTheme.typography.titleLarge)
        DayMetadata(
            places = places,
            people = people,
            onPlacesClick = {},
            onPeopleClick = {},
        )
    }
}

@Composable
private fun DayMetadata(
    places: List<PlaceUiState>,
    people: List<PersonUiState>,
    onPlacesClick: () -> Unit,
    onPeopleClick: () -> Unit,
) {
    Row(
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        if (places.isNotEmpty()) {
            Row {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = Spacing.sm)
                )
                Text(
                    "${places.size} places",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (people.isNotEmpty()) {
            Row {
                Icon(
                    imageVector = Icons.Default.PeopleAlt,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = Spacing.sm)
                )
                Text(
                    "${people.size} people",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}