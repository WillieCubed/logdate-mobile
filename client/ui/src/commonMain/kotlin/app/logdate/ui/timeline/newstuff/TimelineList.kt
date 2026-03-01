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
import androidx.compose.foundation.layout.fillMaxWidth
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
import app.logdate.ui.timeline.TimelineSuggestionBlockType
import app.logdate.ui.timeline.TimelineSuggestionBlockUiState
import app.logdate.util.now
import coil3.compose.AsyncImage
import kotlinx.datetime.LocalDate
import kotlin.math.absoluteValue
import kotlin.uuid.Uuid
import org.jetbrains.compose.resources.stringResource
import logdate.client.ui.generated.resources.*
import logdate.client.ui.generated.resources.Res
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

/**
 * Scrollable list of timeline days with an optional suggestion block at the top.
 *
 * @param items Timeline days to display, ordered most-recent first.
 * @param endOfTimelineState Content shown at the bottom of the list (birthday or easter egg).
 * @param onOpenDay Called when the user taps a day row.
 * @param timelineSuggestion When non-null, a prompt card is shown at the top of the list.
 *   [TimelineSuggestionBlock.OngoingEvent] renders as a "Happening Now" prompt;
 *   [TimelineSuggestionBlock.PastMoment] renders as an "Update" prompt for a past day.
 *   Pass null to hide the card entirely.
 * @param onAddToMemory Called when the user accepts the suggestion for a given memory ID.
 * @param onShare Called when the user shares the suggestion for a given memory ID.
 */
@Composable
fun TimelineList(
    items: List<TimelineDayUiState>,
    endOfTimelineState: EndOfTimelineUiState,
    onOpenDay: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    timelineSuggestion: TimelineSuggestionBlock? = null,
    onAddToMemory: (memoryId: String) -> Unit = {},
    onShare: (memoryId: String) -> Unit = {},
    listState: LazyListState = rememberLazyListState(),
) {
    val suggestionBlockState: TimelineSuggestionBlockUiState? = when (timelineSuggestion) {
        is TimelineSuggestionBlock.OngoingEvent -> TimelineSuggestionBlockUiState(
            memoryId = timelineSuggestion.memoryId,
            type = TimelineSuggestionBlockType.HAPPENING_NOW,
            message = timelineSuggestion.message,
            location = timelineSuggestion.location,
            people = timelineSuggestion.people,
            mediaUris = timelineSuggestion.mediaUris,
        )
        is TimelineSuggestionBlock.PastMoment -> TimelineSuggestionBlockUiState(
            memoryId = timelineSuggestion.memoryId,
            type = TimelineSuggestionBlockType.UPDATE,
            message = timelineSuggestion.message,
            location = timelineSuggestion.location,
            people = timelineSuggestion.people,
            mediaUris = timelineSuggestion.mediaUris,
        )
        null -> null
    }

    LazyColumn(
        modifier = modifier.safeDrawingPadding(),
        state = listState,
        verticalArrangement = Arrangement.spacedBy(Spacing.xl),
    ) {
        item {
            AnimatedVisibility(visible = suggestionBlockState != null) {
                suggestionBlockState?.let { blockState ->
                    TimelineSuggestionBlock(
                        state = blockState,
                        onAddToMemory = onAddToMemory,
                        onShare = onShare,
                        modifier = Modifier.padding(Spacing.lg)
                    )
                }
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
            text = stringResource(Res.string.a_long_time_passed),
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
                        text = stringResource(Res.string.happy_birthday),
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    Text(
                        text = stringResource(
                            Res.string.journey_days_count,
                            state.daysSinceBirth
                        ),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            
            EndOfTimelineUiState.DiscoveryEasterEgg -> {
                Text(stringResource(Res.string.youve_reached_the_end), style = MaterialTheme.typography.titleLarge)
                Column(
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    Text(
                        text = stringResource(Res.string.congrats_curious_explorer),
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    Text(
                        text = stringResource(Res.string.add_your_birthday_in_settings_to_see_something_special_here),
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
            (LocalDate.now().toEpochDays() - birthDate.toEpochDays()).toInt()
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
            .widthIn(min = 320.dp)
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
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
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                modifier = Modifier.padding(Spacing.sm),
            ) {
                Text(
                    "In summary",
                    style = MaterialTheme.typography.labelLarge.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                )
                if (item.isLoadingSummary) {
                    SummaryLoadingPlaceholder()
                } else {
                    Text(item.summary, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun SummaryLoadingPlaceholder(
    modifier: Modifier = Modifier,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        modifier = modifier,
    ) {
        // Shimmer-like placeholder lines
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            shape = MaterialTheme.shapes.extraSmall,
            modifier = Modifier.height(16.dp).widthIn(min = 280.dp),
        ) {}
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            shape = MaterialTheme.shapes.extraSmall,
            modifier = Modifier.height(16.dp).widthIn(min = 200.dp),
        ) {}
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
