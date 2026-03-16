@file:OptIn(ExperimentalLayoutApi::class)
@file:Suppress("ktlint:standard:function-naming")

package app.logdate.ui.timeline.newstuff

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateBounds
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PeopleAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LookaheadScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.logdate.ui.common.applyPaddingIfLast
import app.logdate.ui.common.formatting.asRelativeDate
import app.logdate.ui.restore.LocalAcknowledgeCloudRestore
import app.logdate.ui.restore.LocalIsPostCloudRestore
import app.logdate.ui.theme.Spacing
import app.logdate.ui.timeline.DayPresentation
import app.logdate.ui.timeline.MomentAudioUiState
import app.logdate.ui.timeline.MomentMediaUiState
import app.logdate.ui.timeline.MomentUiState
import app.logdate.ui.timeline.TimelineAudioSectionUiState
import app.logdate.ui.timeline.TimelineDayCardLayout
import app.logdate.ui.timeline.TimelineDayRecapUiState
import app.logdate.ui.timeline.TimelineDaySectionUiState
import app.logdate.ui.timeline.TimelineDayUiState
import app.logdate.ui.timeline.TimelineLoadingState
import app.logdate.ui.timeline.TimelineMediaItemUiState
import app.logdate.ui.timeline.TimelineMediaSectionUiState
import app.logdate.ui.timeline.TimelinePlaceSectionUiState
import app.logdate.ui.timeline.TimelineSuggestionBlock
import app.logdate.ui.timeline.TimelineSuggestionBlockType
import app.logdate.ui.timeline.TimelineSuggestionBlockUiState
import app.logdate.ui.timeline.TimelineTextSnippetSectionUiState
import app.logdate.util.now
import coil3.compose.AsyncImage
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.datetime.LocalDate
import logdate.client.ui.generated.resources.Res
import logdate.client.ui.generated.resources.a_long_time_passed
import logdate.client.ui.generated.resources.add_your_birthday_in_settings_to_see_something_special_here
import logdate.client.ui.generated.resources.congrats_curious_explorer
import logdate.client.ui.generated.resources.happy_birthday
import logdate.client.ui.generated.resources.journey_days_count
import logdate.client.ui.generated.resources.post_restore_import_action
import logdate.client.ui.generated.resources.post_restore_import_message
import logdate.client.ui.generated.resources.post_restore_start_fresh
import logdate.client.ui.generated.resources.post_restore_welcome_back
import logdate.client.ui.generated.resources.suggestion_draft_fallback
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

private enum class TimelineDayLayoutMode {
    COMPACT,
    MEDIUM,
    EXPANDED,
}

private enum class TimelineListContentType {
    SUGGESTION,
    DAY_COMPACT,
    DAY_MEDIUM,
    DAY_EXPANDED,
    GAP,
    SKELETON,
    EMPTY_STATE,
    APPEND_LOADING,
    APPEND_ERROR,
    END_OF_TIMELINE,
}

private data class TimelineDayStyle(
    val accentColor: Color,
    val railColor: Color,
    val softAccentColor: Color,
    val chipColor: Color,
    val textHighlightColor: Color,
)

@Composable
fun TimelineList(
    items: List<TimelineDayUiState>,
    endOfTimelineState: EndOfTimelineUiState,
    onOpenDay: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    loadingState: TimelineLoadingState = TimelineLoadingState.Loaded,
    isLoadingMore: Boolean = false,
    hasMoreOlderContent: Boolean = false,
    appendError: String? = null,
    onLoadMoreOlder: () -> Unit = {},
    timelineSuggestion: TimelineSuggestionBlock? = null,
    onStartWriting: () -> Unit = {},
    onOpenDraft: (draftId: String) -> Unit = {},
    onViewMemoryDay: (LocalDate) -> Unit = {},
    onShareMemory: (LocalDate) -> Unit = {},
    onImportBackup: () -> Unit = {},
    listState: LazyListState = rememberLazyListState(),
) {
    val isPostCloudRestore = LocalIsPostCloudRestore.current
    val draftMessage = stringResource(Res.string.suggestion_draft_fallback)
    val suggestionBlockState: TimelineSuggestionBlockUiState? =
        remember(timelineSuggestion, draftMessage) {
            when (timelineSuggestion) {
                is TimelineSuggestionBlock.CompleteDraft ->
                    TimelineSuggestionBlockUiState(
                        type = TimelineSuggestionBlockType.COMPLETE_DRAFT,
                        message = draftMessage,
                        draftId = timelineSuggestion.draftId,
                    )
                is TimelineSuggestionBlock.EmptyDay ->
                    TimelineSuggestionBlockUiState(
                        type = TimelineSuggestionBlockType.EMPTY_DAY,
                        message = timelineSuggestion.message,
                        location = timelineSuggestion.locationName,
                    )
                is TimelineSuggestionBlock.MemoryRecall ->
                    TimelineSuggestionBlockUiState(
                        type = TimelineSuggestionBlockType.MEMORY_RECALL,
                        message = timelineSuggestion.title,
                        memoryDate = timelineSuggestion.memoryDate,
                        people = timelineSuggestion.people,
                        mediaUris = timelineSuggestion.mediaUris,
                        isAiGenerated = timelineSuggestion.isAiGenerated,
                    )
                null -> null
            }
        }
    // Retain the last non-null state so the exit animation has content to render.
    var lastSuggestion by remember { mutableStateOf(suggestionBlockState) }
    if (suggestionBlockState != null) {
        lastSuggestion = suggestionBlockState
    }

    BoxWithConstraints(modifier = modifier) {
        val layoutMode = maxWidth.toTimelineLayoutMode()

        LaunchedEffect(listState, items.size, hasMoreOlderContent, isLoadingMore, appendError) {
            snapshotFlow {
                val lastVisibleIndex =
                    listState.layoutInfo.visibleItemsInfo
                        .lastOrNull()
                        ?.index ?: return@snapshotFlow false
                hasMoreOlderContent &&
                    !isLoadingMore &&
                    appendError == null &&
                    items.isNotEmpty() &&
                    lastVisibleIndex >= (listState.layoutInfo.totalItemsCount - 4).coerceAtLeast(0)
            }.distinctUntilChanged()
                .filter { shouldLoadMore -> shouldLoadMore }
                .collect {
                    onLoadMoreOlder()
                }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(Spacing.xl),
        ) {
            item(
                contentType = TimelineListContentType.SUGGESTION,
            ) {
                AnimatedVisibility(
                    visible = suggestionBlockState != null,
                    enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
                    exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
                ) {
                    lastSuggestion?.let { blockState ->
                        TimelineSuggestionBlock(
                            state = blockState,
                            onStartWriting = onStartWriting,
                            onOpenDraft = onOpenDraft,
                            onViewMemoryDay = onViewMemoryDay,
                            onShareMemory = onShareMemory,
                            modifier = Modifier.padding(Spacing.lg),
                        )
                    }
                }
            }

            if (items.isEmpty() && loadingState != TimelineLoadingState.Loaded) {
                items(
                    items = placeholderLayouts,
                    key = { layout -> layout.name },
                    contentType = { TimelineListContentType.SKELETON },
                ) { layout ->
                    TimelineDaySkeleton(layout = layout)
                }
            } else if (items.isEmpty() && loadingState == TimelineLoadingState.Loaded) {
                item(
                    contentType = TimelineListContentType.EMPTY_STATE,
                ) {
                    if (isPostCloudRestore) {
                        PostRestoreEmptyState(
                            onImportBackup = onImportBackup,
                            onStartWriting = onStartWriting,
                            modifier = Modifier.padding(Spacing.lg),
                        )
                    }
                }
            } else {
                itemsIndexed(
                    items = items,
                    key = { _, item -> item.date.toString() },
                    contentType = { _, _ -> layoutMode.contentType() },
                ) { index, item ->
                    TimelineDayListItem(
                        item = item,
                        layoutMode = layoutMode,
                        onOpenDay = onOpenDay,
                        modifier =
                            Modifier.applyPaddingIfLast(
                                currentIndex = index,
                                totalItems = items.size,
                            ),
                    )

                    if (index < items.lastIndex) {
                        val currentDate = item.date
                        val nextDate = items[index + 1].date
                        val daysBetween = (currentDate.toEpochDays() - nextDate.toEpochDays()).absoluteValue
                        if (daysBetween > 10) {
                            TimeGapMessageItem()
                        }
                    }
                }
            }

            if (isLoadingMore) {
                item(
                    contentType = TimelineListContentType.APPEND_LOADING,
                ) {
                    TimelineAppendLoadingItem()
                }
            }

            if (appendError != null) {
                item(
                    contentType = TimelineListContentType.APPEND_ERROR,
                ) {
                    TimelineAppendErrorItem(
                        message = appendError,
                        onRetry = onLoadMoreOlder,
                    )
                }
            }

            if (!hasMoreOlderContent && items.isNotEmpty()) {
                item(
                    contentType = TimelineListContentType.END_OF_TIMELINE,
                ) {
                    EndOfTimelineItem(endOfTimelineState)
                }
            }
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
    Text(
        text = stringResource(Res.string.a_long_time_passed),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier =
            modifier
                .padding(horizontal = Spacing.lg)
                .padding(start = 80.dp),
    )
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
                .padding(horizontal = Spacing.lg, vertical = Spacing.xl),
    ) {
        when (state) {
            is EndOfTimelineUiState.BirthdayCelebration -> {
                Text(
                    text = state.birthDate.asRelativeDate(),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(Res.string.happy_birthday),
                    style = MaterialTheme.typography.headlineMedium,
                )
                Text(
                    text = stringResource(Res.string.journey_days_count, state.daysSinceBirth),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            EndOfTimelineUiState.DiscoveryEasterEgg -> {
                Text(
                    text = stringResource(Res.string.youve_reached_the_end),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(Res.string.congrats_curious_explorer),
                    style = MaterialTheme.typography.headlineMedium,
                )
                Text(
                    text = stringResource(Res.string.add_your_birthday_in_settings_to_see_something_special_here),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
private fun TimelineDayListItem(
    item: TimelineDayUiState,
    layoutMode: TimelineDayLayoutMode,
    onOpenDay: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val style = item.layout.style()

    Row(
        horizontalArrangement = Arrangement.spacedBy(if (layoutMode == TimelineDayLayoutMode.COMPACT) Spacing.md else Spacing.xl),
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg)
                .clip(RoundedCornerShape(32.dp))
                .clickable { onOpenDay(item.date) }
                .padding(vertical = if (layoutMode == TimelineDayLayoutMode.COMPACT) Spacing.sm else Spacing.md),
    ) {
        TimelineDayRail(
            item = item,
            style = style,
            layoutMode = layoutMode,
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
            modifier = Modifier.weight(1f),
        ) {
            if (item.moments.isNotEmpty()) {
                // Semantic Timeline: narrative header + moment-based content
                SemanticTimelineDayHeader(
                    item = item,
                    style = style,
                    layoutMode = layoutMode,
                )
                SemanticTimelineDayContent(
                    item = item,
                    style = style,
                    layoutMode = layoutMode,
                )
            } else {
                // Legacy rendering
                TimelineDayHeader(
                    item = item,
                    style = style,
                    layoutMode = layoutMode,
                )
                TimelineDayContent(
                    item = item,
                    style = style,
                    layoutMode = layoutMode,
                )
            }
        }
    }
}

@Composable
private fun TimelineDayRail(
    item: TimelineDayUiState,
    style: TimelineDayStyle,
    layoutMode: TimelineDayLayoutMode,
    modifier: Modifier = Modifier,
) {
    val railWidth =
        when (layoutMode) {
            TimelineDayLayoutMode.COMPACT -> 56.dp
            TimelineDayLayoutMode.MEDIUM -> 72.dp
            TimelineDayLayoutMode.EXPANDED -> 88.dp
        }
    val dayStyle =
        when (layoutMode) {
            TimelineDayLayoutMode.COMPACT -> MaterialTheme.typography.headlineLarge
            TimelineDayLayoutMode.MEDIUM -> MaterialTheme.typography.displaySmall
            TimelineDayLayoutMode.EXPANDED -> MaterialTheme.typography.displayMedium
        }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier =
            modifier
                .width(railWidth)
                .fillMaxHeight(),
    ) {
        Text(
            text =
                item.date.day
                    .toString()
                    .padStart(2, '0'),
            style = dayStyle,
            color = style.accentColor,
        )
        Text(
            text = item.date.shortMonthLabel(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box(
            modifier =
                Modifier
                    .padding(top = Spacing.sm)
                    .fillMaxHeight(),
            contentAlignment = Alignment.TopCenter,
        ) {
            Box(
                modifier =
                    Modifier
                        .width(2.dp)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(100.dp))
                        .background(style.railColor),
            )
            Box(
                modifier =
                    Modifier
                        .size(if (layoutMode == TimelineDayLayoutMode.EXPANDED) 16.dp else 12.dp)
                        .clip(CircleShape)
                        .background(style.accentColor),
            )
        }
    }
}

@Composable
private fun TimelineDayHeader(
    item: TimelineDayUiState,
    style: TimelineDayStyle,
    layoutMode: TimelineDayLayoutMode,
    modifier: Modifier = Modifier,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        modifier = modifier,
    ) {
        Text(
            text = item.date.asRelativeDate(),
            style = MaterialTheme.typography.labelLarge,
            color = style.accentColor,
            fontWeight = FontWeight.SemiBold,
        )

        item.supportingSummary?.let { summary ->
            Text(
                text = summary,
                style =
                    when (layoutMode) {
                        TimelineDayLayoutMode.COMPACT -> MaterialTheme.typography.titleLarge
                        TimelineDayLayoutMode.MEDIUM -> MaterialTheme.typography.headlineSmall
                        TimelineDayLayoutMode.EXPANDED -> MaterialTheme.typography.headlineMedium
                    },
                maxLines = if (layoutMode == TimelineDayLayoutMode.COMPACT) 3 else 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            if (item.placesVisited.isNotEmpty()) {
                DayMetaPill(
                    icon = Icons.Default.LocationOn,
                    text = "${item.placesVisited.size} places",
                    style = style,
                )
            }
            if (item.people.isNotEmpty()) {
                DayMetaPill(
                    icon = Icons.Default.PeopleAlt,
                    text = "${item.people.size} people",
                    style = style,
                )
            }
            if (item.notes.isNotEmpty()) {
                DayMetaPill(
                    icon = Icons.Default.GraphicEq,
                    text = "${item.notes.size} captures",
                    style = style,
                )
            }
        }
    }
}

@Composable
private fun TimelineDayContent(
    item: TimelineDayUiState,
    style: TimelineDayStyle,
    layoutMode: TimelineDayLayoutMode,
    modifier: Modifier = Modifier,
) {
    when (layoutMode) {
        TimelineDayLayoutMode.COMPACT ->
            Column(
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
                modifier = modifier.fillMaxWidth(),
            ) {
                item.heroSection?.let { section ->
                    TimelineSection(
                        section = section,
                        style = style,
                        layoutMode = layoutMode,
                        emphasized = true,
                    )
                }
                if (item.supportingSections.isNotEmpty()) {
                    TimelineSupportingFlow(
                        sections = item.supportingSections,
                        style = style,
                        layoutMode = layoutMode,
                    )
                }
                TimelineRecapStrip(
                    recap = item.recap,
                    style = style,
                )
            }

        TimelineDayLayoutMode.MEDIUM,
        TimelineDayLayoutMode.EXPANDED,
        ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(if (layoutMode == TimelineDayLayoutMode.EXPANDED) Spacing.xl else Spacing.lg),
                verticalAlignment = Alignment.Top,
                modifier = modifier.fillMaxWidth(),
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(Spacing.md),
                    modifier = Modifier.weight(1.25f),
                ) {
                    item.heroSection?.let { section ->
                        TimelineSection(
                            section = section,
                            style = style,
                            layoutMode = layoutMode,
                            emphasized = true,
                        )
                    }
                    TimelineRecapStrip(
                        recap = item.recap,
                        style = style,
                    )
                }

                Column(
                    verticalArrangement = Arrangement.spacedBy(Spacing.md),
                    modifier = Modifier.weight(1f),
                ) {
                    if (item.supportingSections.isNotEmpty()) {
                        TimelineSupportingFlow(
                            sections = item.supportingSections,
                            style = style,
                            layoutMode = layoutMode,
                        )
                    } else if (item.heroSection == null) {
                        TimelineRecapStrip(
                            recap = item.recap,
                            style = style,
                        )
                    }
                }
            }
    }
}

@Composable
private fun TimelineSupportingFlow(
    sections: List<TimelineDaySectionUiState>,
    style: TimelineDayStyle,
    layoutMode: TimelineDayLayoutMode,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
        modifier = modifier.fillMaxWidth(),
    ) {
        sections.forEach { section ->
            val sectionModifier =
                when {
                    section is TimelineTextSnippetSectionUiState ->
                        Modifier.fillMaxWidth()
                    layoutMode == TimelineDayLayoutMode.COMPACT ->
                        Modifier
                            .weight(1f, fill = true)
                            .widthIn(min = 168.dp)
                    else ->
                        Modifier
                            .weight(1f, fill = true)
                            .widthIn(min = 220.dp)
                }

            TimelineSection(
                section = section,
                style = style,
                layoutMode = layoutMode,
                modifier = sectionModifier,
            )
        }
    }
}

// region Semantic Timeline composables

@Composable
private fun SemanticTimelineDayHeader(
    item: TimelineDayUiState,
    style: TimelineDayStyle,
    layoutMode: TimelineDayLayoutMode,
    modifier: Modifier = Modifier,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        modifier = modifier,
    ) {
        Text(
            text = item.date.asRelativeDate(),
            style = MaterialTheme.typography.labelLarge,
            color = style.accentColor,
            fontWeight = FontWeight.SemiBold,
        )

        item.supportingSummary?.let { summary ->
            Text(
                text = summary,
                style =
                    when (layoutMode) {
                        TimelineDayLayoutMode.COMPACT -> MaterialTheme.typography.titleLarge
                        TimelineDayLayoutMode.MEDIUM -> MaterialTheme.typography.headlineSmall
                        TimelineDayLayoutMode.EXPANDED -> MaterialTheme.typography.headlineMedium
                    },
                maxLines = if (layoutMode == TimelineDayLayoutMode.COMPACT) 3 else 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun SemanticTimelineDayContent(
    item: TimelineDayUiState,
    style: TimelineDayStyle,
    layoutMode: TimelineDayLayoutMode,
    modifier: Modifier = Modifier,
) {
    val isCompact = layoutMode == TimelineDayLayoutMode.COMPACT
    val gap =
        when (layoutMode) {
            TimelineDayLayoutMode.COMPACT -> Spacing.lg
            TimelineDayLayoutMode.MEDIUM -> Spacing.lg
            TimelineDayLayoutMode.EXPANDED -> Spacing.xl
        }
    val momentMinWidth =
        when (layoutMode) {
            TimelineDayLayoutMode.COMPACT -> 0.dp
            TimelineDayLayoutMode.MEDIUM -> 260.dp
            TimelineDayLayoutMode.EXPANDED -> 300.dp
        }

    LookaheadScope {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(gap),
            verticalArrangement = Arrangement.spacedBy(gap),
            modifier = modifier.fillMaxWidth(),
        ) {
            item.moments.forEach { moment ->
                val itemModifier =
                    if (moment.isHero || isCompact) {
                        Modifier
                            .fillMaxWidth()
                            .animateBounds(this@LookaheadScope)
                    } else {
                        Modifier
                            .widthIn(min = momentMinWidth)
                            .weight(1f, fill = true)
                            .animateBounds(this@LookaheadScope)
                    }

                when (item.dayPresentation) {
                    DayPresentation.FLOWING ->
                        FlowingMomentItem(
                            moment = moment,
                            style = style,
                            layoutMode = layoutMode,
                            modifier = itemModifier,
                        )
                    DayPresentation.STACKED ->
                        StackedMomentCard(
                            moment = moment,
                            style = style,
                            layoutMode = layoutMode,
                            modifier = itemModifier,
                        )
                }
            }
        }
    }
}

@Composable
private fun FlowingMomentItem(
    moment: MomentUiState,
    style: TimelineDayStyle,
    layoutMode: TimelineDayLayoutMode,
    modifier: Modifier = Modifier,
) {
    val hasSingleMedia = moment.media.size == 1
    val hasText = moment.textSnippet != null
    val useInlineLayout = hasSingleMedia && hasText && !moment.isHero

    // Adapt proportions: on wider screens the image gets relatively smaller
    val imageWeight =
        when (layoutMode) {
            TimelineDayLayoutMode.COMPACT -> 0.4f
            TimelineDayLayoutMode.MEDIUM -> 0.35f
            TimelineDayLayoutMode.EXPANDED -> 0.3f
        }
    val textWeight = 1f - imageWeight

    val textStyle =
        when {
            moment.isHero && layoutMode == TimelineDayLayoutMode.EXPANDED ->
                MaterialTheme.typography.headlineSmall
            moment.isHero -> MaterialTheme.typography.titleLarge
            else -> MaterialTheme.typography.bodyLarge
        }

    Column(
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        modifier =
            modifier
                .fillMaxWidth()
                .animateContentSize(),
    ) {
        // Contextual label
        MomentLabel(
            label = moment.label,
            timeOfDay = moment.timeOfDay,
            color = style.accentColor,
        )

        if (useInlineLayout) {
            // Side-by-side: single image with text beside it
            Row(
                horizontalArrangement =
                    Arrangement.spacedBy(
                        if (layoutMode == TimelineDayLayoutMode.COMPACT) Spacing.md else Spacing.lg,
                    ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                TimelineMediaTile(
                    media =
                        TimelineMediaItemUiState(
                            uri = moment.media.first().uri,
                            isVideo = moment.media.first().isVideo,
                        ),
                    aspectRatio = 1f,
                    modifier = Modifier.weight(imageWeight),
                )
                Column(
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                    modifier = Modifier.weight(textWeight),
                ) {
                    Text(
                        text = moment.textSnippet ?: "",
                        style = textStyle,
                        maxLines = if (layoutMode == TimelineDayLayoutMode.COMPACT) 4 else 6,
                        overflow = TextOverflow.Ellipsis,
                    )
                    MomentPeopleInline(moment.people)
                }
            }
        } else {
            // Vertical stack for hero moments, multi-media, or text-only

            if (moment.media.isNotEmpty()) {
                MomentMediaGrid(
                    media = moment.media,
                    layoutMode = layoutMode,
                    emphasized = moment.isHero,
                )
            }

            moment.audio?.let { audio ->
                MomentAudioInline(audio = audio, style = style)
            }

            moment.textSnippet?.let { snippet ->
                Text(
                    text = snippet,
                    style = textStyle,
                    maxLines = if (moment.isHero) 4 else 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            MomentPeopleInline(moment.people)
        }
    }
}

@Composable
private fun StackedMomentCard(
    moment: MomentUiState,
    style: TimelineDayStyle,
    layoutMode: TimelineDayLayoutMode,
    modifier: Modifier = Modifier,
) {
    val cardPadding =
        when (layoutMode) {
            TimelineDayLayoutMode.COMPACT -> Spacing.md
            TimelineDayLayoutMode.MEDIUM -> Spacing.lg
            TimelineDayLayoutMode.EXPANDED -> Spacing.xl
        }

    Surface(
        color = style.softAccentColor,
        shape = RoundedCornerShape(24.dp),
        modifier =
            modifier
                .fillMaxWidth()
                .animateContentSize(),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            modifier = Modifier.padding(cardPadding),
        ) {
            MomentLabel(
                label = moment.label,
                timeOfDay = moment.timeOfDay,
                color = style.accentColor,
            )

            if (moment.media.isNotEmpty()) {
                MomentMediaGrid(
                    media = moment.media,
                    layoutMode = layoutMode,
                    emphasized = moment.isHero,
                )
            }

            moment.audio?.let { audio ->
                MomentAudioInline(audio = audio, style = style)
            }

            moment.textSnippet?.let { snippet ->
                Text(
                    text = snippet,
                    style =
                        when (layoutMode) {
                            TimelineDayLayoutMode.EXPANDED -> MaterialTheme.typography.titleMedium
                            else -> MaterialTheme.typography.bodyLarge
                        },
                    maxLines = if (layoutMode == TimelineDayLayoutMode.COMPACT) 4 else 6,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            MomentCardMetadata(moment = moment, style = style)
        }
    }
}

/**
 * Embedded metadata row for stacked cards: place + people, integrated into the surface.
 */
@Composable
private fun MomentCardMetadata(
    moment: MomentUiState,
    style: TimelineDayStyle,
    modifier: Modifier = Modifier,
) {
    if (moment.places.isEmpty() && moment.people.isEmpty()) return

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        modifier = modifier,
    ) {
        moment.places.firstOrNull()?.let { place ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = style.accentColor,
                )
                Text(
                    text = place.title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (moment.places.isNotEmpty() && moment.people.isNotEmpty()) {
            Text(
                text = "\u00b7",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (moment.people.isNotEmpty()) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.PeopleAlt,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = style.accentColor,
                )
                Text(
                    text = moment.people.joinToString(", "),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun MomentPeopleInline(
    people: List<String>,
    modifier: Modifier = Modifier,
) {
    if (people.isNotEmpty()) {
        Text(
            text = "with ${people.joinToString(", ")}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier,
        )
    }
}

@Composable
private fun MomentLabel(
    label: String,
    timeOfDay: String?,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val displayText =
        if (timeOfDay != null && !label.lowercase().contains(timeOfDay)) {
            "$label \u00b7 $timeOfDay"
        } else {
            label
        }
    Text(
        text = displayText,
        style = MaterialTheme.typography.labelLarge,
        color = color,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier,
    )
}

@Composable
private fun MomentMediaGrid(
    media: List<MomentMediaUiState>,
    layoutMode: TimelineDayLayoutMode,
    emphasized: Boolean,
    modifier: Modifier = Modifier,
) {
    // Reuse the same asymmetric layout from MediaSection
    when (media.size) {
        0 -> Unit
        1 ->
            TimelineMediaTile(
                media = TimelineMediaItemUiState(uri = media.first().uri, isVideo = media.first().isVideo),
                aspectRatio = if (emphasized) 1.2f else 1.05f,
                modifier = modifier.fillMaxWidth(),
            )
        2 ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                modifier = modifier.fillMaxWidth(),
            ) {
                TimelineMediaTile(
                    media = TimelineMediaItemUiState(uri = media[0].uri, isVideo = media[0].isVideo),
                    aspectRatio = if (emphasized) 0.95f else 1f,
                    modifier = Modifier.weight(1.2f),
                )
                TimelineMediaTile(
                    media = TimelineMediaItemUiState(uri = media[1].uri, isVideo = media[1].isVideo),
                    aspectRatio = if (layoutMode == TimelineDayLayoutMode.COMPACT) 0.95f else 1.15f,
                    modifier = Modifier.weight(1f),
                )
            }
        else ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                modifier = modifier.fillMaxWidth(),
            ) {
                TimelineMediaTile(
                    media = TimelineMediaItemUiState(uri = media.first().uri, isVideo = media.first().isVideo),
                    aspectRatio = if (emphasized) 0.9f else 1.05f,
                    modifier = Modifier.weight(1.35f),
                )
                Column(
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    modifier = Modifier.weight(1f),
                ) {
                    media.drop(1).take(2).forEach { item ->
                        TimelineMediaTile(
                            media = TimelineMediaItemUiState(uri = item.uri, isVideo = item.isVideo),
                            aspectRatio = 1.2f,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
    }
}

@Composable
private fun MomentAudioInline(
    audio: MomentAudioUiState,
    style: TimelineDayStyle,
    modifier: Modifier = Modifier,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(style.softAccentColor)
                .padding(horizontal = Spacing.lg, vertical = Spacing.md),
    ) {
        Box(
            modifier =
                Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(style.accentColor.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.GraphicEq,
                contentDescription = null,
                tint = style.accentColor,
            )
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = "Voice note",
                style = MaterialTheme.typography.titleMedium,
            )
            AudioWaveBars(accentColor = style.accentColor)
        }
        Text(
            text = audio.durationMs.toDurationLabel(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// endregion

@Composable
private fun TimelineSection(
    section: TimelineDaySectionUiState,
    style: TimelineDayStyle,
    layoutMode: TimelineDayLayoutMode,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
) {
    when (section) {
        is TimelineAudioSectionUiState ->
            AudioSection(
                section = section,
                style = style,
                modifier = modifier,
                emphasized = emphasized,
            )
        is TimelineMediaSectionUiState ->
            MediaSection(
                section = section,
                style = style,
                modifier = modifier,
                layoutMode = layoutMode,
                emphasized = emphasized,
            )
        is TimelinePlaceSectionUiState ->
            PlaceSection(
                section = section,
                style = style,
                modifier = modifier,
                emphasized = emphasized,
            )
        is TimelineTextSnippetSectionUiState ->
            TextSnippetSection(
                section = section,
                style = style,
                modifier = modifier,
                emphasized = emphasized,
            )
    }
}

@Composable
private fun TimelineRecapStrip(
    recap: TimelineDayRecapUiState,
    style: TimelineDayStyle,
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
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        modifier = modifier.fillMaxWidth(),
    ) {
        recapItems.forEach { item ->
            Surface(
                color = style.chipColor,
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
    style: TimelineDayStyle,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        modifier = modifier.fillMaxWidth(),
    ) {
        Box(
            modifier =
                Modifier
                    .width(4.dp)
                    .heightIn(min = if (emphasized) 88.dp else 64.dp)
                    .clip(RoundedCornerShape(100.dp))
                    .background(if (emphasized) style.accentColor else style.accentColor.copy(alpha = 0.55f)),
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            modifier = Modifier.weight(1f),
        ) {
            SectionLabel(
                text = section.label,
                color = style.accentColor,
            )
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
    style: TimelineDayStyle,
    layoutMode: TimelineDayLayoutMode,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        modifier = modifier.fillMaxWidth(),
    ) {
        SectionLabel(
            text = section.label,
            color = style.accentColor,
        )
        when (section.items.size) {
            0 -> Unit
            1 ->
                TimelineMediaTile(
                    media = section.items.first(),
                    aspectRatio = if (emphasized) 1.2f else 1.05f,
                    modifier = Modifier.fillMaxWidth(),
                )
            2 ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    TimelineMediaTile(
                        media = section.items[0],
                        aspectRatio = if (emphasized) 0.95f else 1f,
                        modifier = Modifier.weight(1.2f),
                    )
                    TimelineMediaTile(
                        media = section.items[1],
                        aspectRatio = if (layoutMode == TimelineDayLayoutMode.COMPACT) 0.95f else 1.15f,
                        modifier = Modifier.weight(1f),
                    )
                }
            else ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    TimelineMediaTile(
                        media = section.items.first(),
                        aspectRatio = if (emphasized) 0.9f else 1.05f,
                        modifier = Modifier.weight(1.35f),
                    )
                    Column(
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                        modifier = Modifier.weight(1f),
                    ) {
                        section.items.drop(1).take(2).forEach { media ->
                            TimelineMediaTile(
                                media = media,
                                aspectRatio = 1.2f,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
        }
    }
}

@Composable
private fun TimelineMediaTile(
    media: TimelineMediaItemUiState,
    aspectRatio: Float,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .clip(RoundedCornerShape(28.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .aspectRatio(aspectRatio),
    ) {
        AsyncImage(
            model = media.uri,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
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

@Composable
private fun AudioSection(
    section: TimelineAudioSectionUiState,
    style: TimelineDayStyle,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            modifier
                .clip(RoundedCornerShape(28.dp))
                .background(style.softAccentColor)
                .padding(horizontal = Spacing.lg, vertical = Spacing.md),
    ) {
        Box(
            modifier =
                Modifier
                    .size(if (emphasized) 52.dp else 44.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(style.accentColor.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.GraphicEq,
                contentDescription = null,
                tint = style.accentColor,
            )
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            modifier = Modifier.weight(1f),
        ) {
            SectionLabel(
                text = section.label,
                color = style.accentColor,
            )
            Text(
                text = "Voice note",
                style =
                    if (emphasized) {
                        MaterialTheme.typography.titleLarge
                    } else {
                        MaterialTheme.typography.titleMedium
                    },
            )
            AudioWaveBars(accentColor = style.accentColor)
        }
        Text(
            text = section.note.duration.toDurationLabel(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AudioWaveBars(
    accentColor: Color,
    modifier: Modifier = Modifier,
) {
    val waveHeights =
        listOf(
            10.dp,
            18.dp,
            12.dp,
            22.dp,
            14.dp,
            20.dp,
            11.dp,
            17.dp,
        )

    Row(
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalAlignment = Alignment.Bottom,
        modifier = modifier,
    ) {
        waveHeights.forEach { barHeight ->
            Box(
                modifier =
                    Modifier
                        .width(5.dp)
                        .height(barHeight)
                        .clip(RoundedCornerShape(100.dp))
                        .background(accentColor.copy(alpha = 0.78f)),
            )
        }
    }
}

@Composable
private fun PlaceSection(
    section: TimelinePlaceSectionUiState,
    style: TimelineDayStyle,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        modifier = modifier.fillMaxWidth(),
    ) {
        SectionLabel(
            text = section.label,
            color = style.accentColor,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            section.places.forEach { place ->
                Surface(
                    color = if (emphasized) style.softAccentColor else style.chipColor,
                    shape = RoundedCornerShape(20.dp),
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
                            tint = style.accentColor,
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

@Composable
private fun SectionLabel(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = color,
        modifier = modifier,
    )
}

@Composable
private fun DayMetaPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    style: TimelineDayStyle,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = style.chipColor,
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
                tint = style.accentColor,
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
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.84f),
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
private fun TimelineAppendLoadingItem(modifier: Modifier = Modifier) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            modifier
                .padding(horizontal = Spacing.lg, vertical = Spacing.md)
                .padding(start = 80.dp),
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(20.dp),
            strokeWidth = 2.dp,
        )
        Text(
            text = "Loading older days",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TimelineAppendErrorItem(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg, vertical = Spacing.md)
                .padding(start = 80.dp),
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.weight(1f),
        )
        FilledTonalButton(onClick = onRetry) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = null,
                modifier = Modifier.padding(end = Spacing.xs),
            )
            Text("Retry")
        }
    }
}

@Composable
private fun TimelineDaySkeleton(
    layout: TimelineDayCardLayout,
    modifier: Modifier = Modifier,
) {
    val style = layout.style()

    Row(
        horizontalArrangement = Arrangement.spacedBy(Spacing.xl),
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(72.dp),
        ) {
            PlaceholderLine(width = 40.dp, height = 32.dp)
            PlaceholderLine(width = 28.dp, height = 14.dp)
            Box(
                modifier =
                    Modifier
                        .padding(top = Spacing.sm)
                        .width(2.dp)
                        .height(200.dp)
                        .clip(RoundedCornerShape(100.dp))
                        .background(style.railColor),
            )
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
            modifier = Modifier.weight(1f),
        ) {
            PlaceholderLine(width = 96.dp, height = 14.dp)
            PlaceholderLine(width = 280.dp, height = 28.dp)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                PlaceholderPill(width = 104.dp)
                PlaceholderPill(width = 92.dp)
                PlaceholderPill(width = 120.dp)
            }
            when (layout) {
                TimelineDayCardLayout.MEDIA_LED -> PlaceholderBlock(height = 280.dp)
                TimelineDayCardLayout.VOICE_LED -> PlaceholderBlock(height = 120.dp)
                TimelineDayCardLayout.PLACE_LED -> PlaceholderBlock(height = 132.dp)
                TimelineDayCardLayout.STORY_LED -> PlaceholderBlock(height = 160.dp)
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                PlaceholderPill(width = 116.dp)
                PlaceholderPill(width = 140.dp)
                PlaceholderPill(width = 96.dp)
            }
        }
    }
}

@Composable
private fun PlaceholderBlock(
    height: Dp,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = RoundedCornerShape(28.dp),
        modifier =
            modifier
                .fillMaxWidth()
                .height(height),
    ) {}
}

@Composable
private fun PlaceholderLine(
    width: Dp,
    modifier: Modifier = Modifier,
    height: Dp = 18.dp,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = RoundedCornerShape(8.dp),
        modifier =
            modifier
                .width(width)
                .height(height),
    ) {}
}

@Composable
private fun PlaceholderPill(
    modifier: Modifier = Modifier,
    width: Dp = 84.dp,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = RoundedCornerShape(18.dp),
        modifier =
            modifier
                .width(width)
                .height(32.dp),
    ) {}
}

@Composable
private fun TimelineDayCardLayout.style(): TimelineDayStyle =
    when (this) {
        TimelineDayCardLayout.MEDIA_LED ->
            TimelineDayStyle(
                accentColor = MaterialTheme.colorScheme.primary,
                railColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                softAccentColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f),
                chipColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.32f),
                textHighlightColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f),
            )
        TimelineDayCardLayout.VOICE_LED ->
            TimelineDayStyle(
                accentColor = MaterialTheme.colorScheme.secondary,
                railColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.18f),
                softAccentColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.52f),
                chipColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.34f),
                textHighlightColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.22f),
            )
        TimelineDayCardLayout.PLACE_LED ->
            TimelineDayStyle(
                accentColor = MaterialTheme.colorScheme.tertiary,
                railColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.18f),
                softAccentColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.52f),
                chipColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.34f),
                textHighlightColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.22f),
            )
        TimelineDayCardLayout.STORY_LED ->
            TimelineDayStyle(
                accentColor = MaterialTheme.colorScheme.primary,
                railColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                softAccentColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                chipColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                textHighlightColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            )
    }

private fun Dp.toTimelineLayoutMode(): TimelineDayLayoutMode =
    when {
        this >= 920.dp -> TimelineDayLayoutMode.EXPANDED
        this >= 620.dp -> TimelineDayLayoutMode.MEDIUM
        else -> TimelineDayLayoutMode.COMPACT
    }

private fun TimelineDayLayoutMode.contentType(): TimelineListContentType =
    when (this) {
        TimelineDayLayoutMode.COMPACT -> TimelineListContentType.DAY_COMPACT
        TimelineDayLayoutMode.MEDIUM -> TimelineListContentType.DAY_MEDIUM
        TimelineDayLayoutMode.EXPANDED -> TimelineListContentType.DAY_EXPANDED
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

private fun LocalDate.shortMonthLabel(): String =
    when (month) {
        kotlinx.datetime.Month.JANUARY -> "JAN"
        kotlinx.datetime.Month.FEBRUARY -> "FEB"
        kotlinx.datetime.Month.MARCH -> "MAR"
        kotlinx.datetime.Month.APRIL -> "APR"
        kotlinx.datetime.Month.MAY -> "MAY"
        kotlinx.datetime.Month.JUNE -> "JUN"
        kotlinx.datetime.Month.JULY -> "JUL"
        kotlinx.datetime.Month.AUGUST -> "AUG"
        kotlinx.datetime.Month.SEPTEMBER -> "SEP"
        kotlinx.datetime.Month.OCTOBER -> "OCT"
        kotlinx.datetime.Month.NOVEMBER -> "NOV"
        else -> "DEC"
    }

@Composable
private fun PostRestoreEmptyState(
    onImportBackup: () -> Unit,
    onStartWriting: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val acknowledgeRestore = LocalAcknowledgeCloudRestore.current

    Column(
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        modifier =
            modifier
                .fillMaxWidth()
                .padding(vertical = Spacing.xl),
    ) {
        Text(
            text = stringResource(Res.string.post_restore_welcome_back),
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            text = stringResource(Res.string.post_restore_import_message),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            FilledTonalButton(onClick = {
                acknowledgeRestore()
                onImportBackup()
            }) {
                Text(stringResource(Res.string.post_restore_import_action))
            }
            FilledTonalButton(onClick = {
                acknowledgeRestore()
                onStartWriting()
            }) {
                Text(stringResource(Res.string.post_restore_start_fresh))
            }
        }
    }
}
