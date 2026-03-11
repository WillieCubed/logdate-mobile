@file:OptIn(ExperimentalLayoutApi::class)
@file:Suppress("ktlint:standard:function-naming")

package app.logdate.ui.timeline.newstuff

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PeopleAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Videocam
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.logdate.ui.common.applyPaddingIfLast
import app.logdate.ui.common.formatting.asRelativeDate
import app.logdate.ui.theme.Spacing
import app.logdate.ui.timeline.TimelineAudioSectionUiState
import app.logdate.ui.timeline.TimelineDayCardLayout
import app.logdate.ui.timeline.TimelineDayRecapUiState
import app.logdate.ui.timeline.TimelineDaySectionUiState
import app.logdate.ui.timeline.TimelineDayUiState
import app.logdate.ui.timeline.TimelineLoadingState
import app.logdate.ui.timeline.TimelineMediaSectionUiState
import app.logdate.ui.timeline.TimelinePlaceSectionUiState
import app.logdate.ui.timeline.TimelineSuggestionBlock
import app.logdate.ui.timeline.TimelineSuggestionBlockType
import app.logdate.ui.timeline.TimelineSuggestionBlockUiState
import app.logdate.ui.timeline.TimelineTextSnippetSectionUiState
import app.logdate.util.now
import coil3.compose.AsyncImage
import kotlinx.datetime.LocalDate
import logdate.client.ui.generated.resources.Res
import logdate.client.ui.generated.resources.a_long_time_passed
import logdate.client.ui.generated.resources.add_your_birthday_in_settings_to_see_something_special_here
import logdate.client.ui.generated.resources.congrats_curious_explorer
import logdate.client.ui.generated.resources.happy_birthday
import logdate.client.ui.generated.resources.journey_days_count
import logdate.client.ui.generated.resources.youve_reached_the_end
import org.jetbrains.compose.resources.stringResource
import kotlin.math.absoluteValue

sealed class EndOfTimelineUiState {
    data class BirthdayCelebration(
        val birthDate: LocalDate,
        val daysSinceBirth: Int,
    ) : EndOfTimelineUiState()

    data object DiscoveryEasterEgg : EndOfTimelineUiState()
}

@Composable
fun TimelineList(
    items: List<TimelineDayUiState>,
    endOfTimelineState: EndOfTimelineUiState,
    onOpenDay: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    loadingState: TimelineLoadingState = TimelineLoadingState.Loaded,
    timelineSuggestion: TimelineSuggestionBlock? = null,
    onAddToMemory: (memoryId: String) -> Unit = {},
    onShare: (memoryId: String) -> Unit = {},
    listState: LazyListState = rememberLazyListState(),
) {
    val suggestionBlockState: TimelineSuggestionBlockUiState? =
        when (timelineSuggestion) {
            is TimelineSuggestionBlock.OngoingEvent ->
                TimelineSuggestionBlockUiState(
                    memoryId = timelineSuggestion.memoryId,
                    type = TimelineSuggestionBlockType.HAPPENING_NOW,
                    message = timelineSuggestion.message,
                    location = timelineSuggestion.location,
                    people = timelineSuggestion.people,
                    mediaUris = timelineSuggestion.mediaUris,
                )
            is TimelineSuggestionBlock.PastMoment ->
                TimelineSuggestionBlockUiState(
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
                        modifier = Modifier.padding(Spacing.lg),
                    )
                }
            }
        }

        if (items.isEmpty() && loadingState != TimelineLoadingState.Loaded) {
            items(placeholderLayouts) { layout ->
                TimelineDaySkeleton(layout = layout)
            }
        } else {
            itemsIndexed(
                items,
                key = { _, item -> item.date.toString() },
            ) { index, item ->
                TimelineDayListItem(
                    item = item,
                    onOpenDay = onOpenDay,
                    modifier =
                        Modifier
                            .applyPaddingIfLast(
                                currentIndex = index,
                                totalItems = items.size,
                            ),
                )

                val isNotLastItem = index < items.size - 1
                if (isNotLastItem) {
                    val currentDate = item.date
                    val nextDate = items[index + 1].date
                    val daysBetween = (currentDate.toEpochDays() - nextDate.toEpochDays()).absoluteValue
                    if (daysBetween > 10) {
                        TimeGapMessageItem()
                    }
                }
            }
        }

        item {
            TimeGapMessageItem()
        }
        item {
            EndOfTimelineItem(endOfTimelineState)
        }
    }
}

private val placeholderLayouts =
    listOf(
        TimelineDayCardLayout.MEDIA_LED,
        TimelineDayCardLayout.VOICE_LED,
        TimelineDayCardLayout.PLACE_LED,
        TimelineDayCardLayout.STORY_LED,
    )

@Composable
internal fun TimeGapMessageItem(modifier: Modifier = Modifier) {
    Column(
        verticalArrangement =
            Arrangement.spacedBy(
                Spacing.lg,
                alignment = Alignment.CenterVertically,
            ),
        modifier =
            modifier
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

@Composable
internal fun EndOfTimelineItem(
    state: EndOfTimelineUiState,
    modifier: Modifier = Modifier,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        modifier =
            modifier
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
                        text = stringResource(Res.string.journey_days_count, state.daysSinceBirth),
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
        state =
            EndOfTimelineUiState.BirthdayCelebration(
                birthDate = birthDate,
                daysSinceBirth = daysSinceBirthday,
            ),
        modifier = modifier,
    )
}

@Composable
internal fun TimelineDayListItem(
    item: TimelineDayUiState,
    onOpenDay: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shell = item.layout.shell()
    Surface(
        color = shell.containerColor,
        shape = shell.shape,
        modifier =
            modifier
                .widthIn(min = 320.dp)
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg)
                .clip(shell.shape)
                .clickable { onOpenDay(item.date) },
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
            modifier =
                Modifier
                    .background(shell.backgroundColor)
                    .padding(Spacing.lg),
        ) {
            TimelineDayHeader(item = item)
            TimelineHeroSection(item = item)
            TimelineRecapStrip(recap = item.recap, accentColor = shell.accentColor)
            item.supportingSections.forEach { section ->
                TimelineSupportingSection(
                    section = section,
                    layout = item.layout,
                )
            }
            item.supportingSummary?.let { summary ->
                SupportingSummaryFooter(summary = summary)
            }
        }
    }
}

@Composable
private fun TimelineDayHeader(
    item: TimelineDayUiState,
    modifier: Modifier = Modifier,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        modifier = modifier,
    ) {
        Text(
            text = item.date.asRelativeDate(),
            style = MaterialTheme.typography.headlineSmall,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (item.placesVisited.isNotEmpty()) {
                DayMetaPill(
                    icon = Icons.Default.LocationOn,
                    text = "${item.placesVisited.size} places",
                )
            }
            if (item.people.isNotEmpty()) {
                DayMetaPill(
                    icon = Icons.Default.PeopleAlt,
                    text = "${item.people.size} people",
                )
            }
        }
    }
}

@Composable
private fun TimelineHeroSection(
    item: TimelineDayUiState,
    modifier: Modifier = Modifier,
) {
    item.heroSection?.let { section ->
        when (section) {
            is TimelineAudioSectionUiState -> {
                AudioSection(
                    section = section,
                    modifier = modifier,
                    emphasized = true,
                )
            }
            is TimelineMediaSectionUiState -> {
                MediaSection(
                    section = section,
                    modifier = modifier,
                    emphasized = true,
                )
            }
            is TimelinePlaceSectionUiState -> {
                PlaceSection(
                    section = section,
                    modifier = modifier,
                    emphasized = true,
                )
            }
            is TimelineTextSnippetSectionUiState -> {
                TextSnippetSection(
                    section = section,
                    modifier = modifier,
                    emphasized = true,
                )
            }
        }
    }
}

@Composable
private fun TimelineSupportingSection(
    section: TimelineDaySectionUiState,
    layout: TimelineDayCardLayout,
    modifier: Modifier = Modifier,
) {
    when (section) {
        is TimelineAudioSectionUiState -> AudioSection(section = section, modifier = modifier)
        is TimelineMediaSectionUiState ->
            MediaSection(
                section = section,
                modifier = modifier,
                compact =
                    layout != TimelineDayCardLayout.MEDIA_LED,
            )
        is TimelinePlaceSectionUiState -> PlaceSection(section = section, modifier = modifier)
        is TimelineTextSnippetSectionUiState -> TextSnippetSection(section = section, modifier = modifier)
    }
}

@Composable
private fun TimelineRecapStrip(
    recap: TimelineDayRecapUiState,
    accentColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    val recapItems =
        buildList {
            if (recap.captureCount > 0) {
                add("${recap.captureCount} captured")
            }
            if (recap.placeCount > 0) {
                add("${recap.placeCount} visited")
            }
            if (recap.peopleCount > 0) {
                add("${recap.peopleCount} connected")
            }
            if (recap.activeSpanMinutes > 0) {
                add(recap.activeSpanMinutes.toSpanLabel())
            }
        }

    if (recapItems.isEmpty()) {
        return
    }

    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        recapItems.forEach { item ->
            Surface(
                color = accentColor.copy(alpha = 0.14f),
                shape = RoundedCornerShape(18.dp),
            ) {
                Text(
                    text = item,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
                )
            }
        }
    }
}

@Composable
private fun TextSnippetSection(
    section: TimelineTextSnippetSectionUiState,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
) {
    Surface(
        color =
            if (emphasized) {
                MaterialTheme.colorScheme.surfaceBright
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            },
        shape = RoundedCornerShape(if (emphasized) 24.dp else 18.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            modifier = Modifier.padding(Spacing.lg),
        ) {
            SectionLabel(section.label)
            Text(
                text = section.text,
                style =
                    if (emphasized) {
                        MaterialTheme.typography.titleLarge
                    } else {
                        MaterialTheme.typography.bodyLarge
                    },
                maxLines = if (emphasized) 4 else 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun MediaSection(
    section: TimelineMediaSectionUiState,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
    compact: Boolean = false,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        modifier = modifier.fillMaxWidth(),
    ) {
        SectionLabel(section.label)
        val mediaShape = RoundedCornerShape(if (emphasized) 28.dp else 20.dp)
        if (section.items.size == 1) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = mediaShape,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Box {
                    AsyncImage(
                        model = section.items.first().uri,
                        contentDescription = null,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(if (compact) 1.4f else 1.15f),
                    )
                    if (section.items.first().isVideo) {
                        MediaBadge(
                            icon = Icons.Default.Videocam,
                            text = "Video",
                            modifier =
                                Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(Spacing.md),
                        )
                    }
                }
            }
        } else {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                modifier = Modifier.fillMaxWidth(),
            ) {
                section.items.forEachIndexed { index, media ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        shape = mediaShape,
                        modifier = Modifier.weight(1f, fill = true),
                    ) {
                        Box {
                            AsyncImage(
                                model = media.uri,
                                contentDescription = null,
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(if (index == 0) 1.2f else 1f),
                            )
                            if (media.isVideo) {
                                MediaBadge(
                                    icon = Icons.Default.PlayArrow,
                                    text = "Clip",
                                    modifier =
                                        Modifier
                                            .align(Alignment.BottomStart)
                                            .padding(Spacing.sm),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AudioSection(
    section: TimelineAudioSectionUiState,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
) {
    Surface(
        color =
            if (emphasized) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            },
        shape = RoundedCornerShape(if (emphasized) 24.dp else 18.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(Spacing.lg),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(if (emphasized) 52.dp else 40.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.GraphicEq,
                    contentDescription = null,
                )
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                modifier = Modifier.weight(1f),
            ) {
                SectionLabel(section.label)
                Text(
                    text = "Voice note",
                    style =
                        if (emphasized) {
                            MaterialTheme.typography.titleLarge
                        } else {
                            MaterialTheme.typography.titleMedium
                        },
                )
                Text(
                    text = section.note.duration.toDurationLabel(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PlaceSection(
    section: TimelinePlaceSectionUiState,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
) {
    Surface(
        color =
            if (emphasized) {
                MaterialTheme.colorScheme.tertiaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            },
        shape = RoundedCornerShape(if (emphasized) 24.dp else 18.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            modifier = Modifier.padding(Spacing.lg),
        ) {
            SectionLabel(section.label)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                section.places.forEach { place ->
                    Surface(
                        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(18.dp),
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                text = place.title,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SupportingSummaryFooter(
    summary: String,
    modifier: Modifier = Modifier,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(
            text = "Summary",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = summary,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SectionLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

@Composable
private fun DayMetaPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = RoundedCornerShape(18.dp),
        modifier = modifier,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MediaBadge(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
private fun TimelineDaySkeleton(
    layout: TimelineDayCardLayout,
    modifier: Modifier = Modifier,
) {
    val shell = layout.shell()
    Surface(
        color = shell.containerColor,
        shape = shell.shape,
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
            modifier =
                Modifier
                    .background(shell.backgroundColor)
                    .padding(Spacing.lg),
        ) {
            PlaceholderLine(width = 180.dp, height = 28.dp)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                PlaceholderPill()
                PlaceholderPill()
            }
            when (layout) {
                TimelineDayCardLayout.MEDIA_LED -> PlaceholderBlock(height = 220.dp)
                TimelineDayCardLayout.VOICE_LED -> PlaceholderBlock(height = 120.dp)
                TimelineDayCardLayout.PLACE_LED -> PlaceholderBlock(height = 140.dp)
                TimelineDayCardLayout.STORY_LED -> PlaceholderBlock(height = 160.dp)
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                PlaceholderPill(width = 104.dp)
                PlaceholderPill(width = 92.dp)
                PlaceholderPill(width = 120.dp)
            }
            PlaceholderLine(width = 240.dp)
            PlaceholderLine(width = 200.dp)
        }
    }
}

@Composable
private fun PlaceholderBlock(
    height: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = RoundedCornerShape(24.dp),
        modifier =
            modifier
                .fillMaxWidth()
                .height(height),
    ) {}
}

@Composable
private fun PlaceholderLine(
    width: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 18.dp,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = RoundedCornerShape(8.dp),
        modifier =
            modifier
                .widthIn(min = width)
                .height(height),
    ) {}
}

@Composable
private fun PlaceholderPill(
    modifier: Modifier = Modifier,
    width: androidx.compose.ui.unit.Dp = 84.dp,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = RoundedCornerShape(18.dp),
        modifier =
            modifier
                .widthIn(min = width)
                .height(32.dp),
    ) {}
}

private data class TimelineCardShell(
    val containerColor: androidx.compose.ui.graphics.Color,
    val backgroundColor: androidx.compose.ui.graphics.Color,
    val accentColor: androidx.compose.ui.graphics.Color,
    val shape: RoundedCornerShape,
)

@Composable
private fun TimelineDayCardLayout.shell(): TimelineCardShell =
    when (this) {
        TimelineDayCardLayout.MEDIA_LED ->
            TimelineCardShell(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                backgroundColor = MaterialTheme.colorScheme.surfaceContainer,
                accentColor = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(topStart = 32.dp, topEnd = 12.dp, bottomEnd = 32.dp, bottomStart = 20.dp),
            )
        TimelineDayCardLayout.VOICE_LED ->
            TimelineCardShell(
                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f),
                backgroundColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f),
                accentColor = MaterialTheme.colorScheme.secondary,
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 32.dp, bottomEnd = 20.dp, bottomStart = 32.dp),
            )
        TimelineDayCardLayout.PLACE_LED ->
            TimelineCardShell(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f),
                backgroundColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f),
                accentColor = MaterialTheme.colorScheme.tertiary,
                shape = RoundedCornerShape(28.dp),
            )
        TimelineDayCardLayout.STORY_LED ->
            TimelineCardShell(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                backgroundColor = MaterialTheme.colorScheme.surfaceContainerLow,
                accentColor = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 28.dp, bottomEnd = 28.dp, bottomStart = 16.dp),
            )
    }

private fun Int.toSpanLabel(): String =
    when {
        this >= 60 -> {
            val hours = this / 60
            val minutes = this % 60
            if (minutes == 0) {
                "${hours}h span"
            } else {
                "${hours}h ${minutes}m span"
            }
        }
        this > 0 -> "${this}m span"
        else -> "Quick capture"
    }

private fun Long.toDurationLabel(): String {
    val totalSeconds = this / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}
