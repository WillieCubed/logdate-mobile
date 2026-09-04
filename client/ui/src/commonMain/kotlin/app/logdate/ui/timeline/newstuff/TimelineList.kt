@file:OptIn(ExperimentalLayoutApi::class)
@file:Suppress("ktlint:standard:function-naming")

package app.logdate.ui.timeline.newstuff

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.logdate.ui.common.applyPaddingIfLast
import app.logdate.ui.common.formatting.LocalToday
import app.logdate.ui.common.formatting.asRelativeDate
import app.logdate.ui.restore.LocalAcknowledgeCloudRestore
import app.logdate.ui.restore.LocalIsPostCloudRestore
import app.logdate.ui.theme.Spacing
import app.logdate.ui.timeline.TimelineDayCardLayout
import app.logdate.ui.timeline.TimelineDayUiState
import app.logdate.ui.timeline.TimelineLoadingState
import app.logdate.ui.timeline.TimelineMediaItemUiState
import app.logdate.ui.timeline.TimelineSuggestionBlock
import app.logdate.ui.timeline.TimelineSuggestionBlockType
import app.logdate.ui.timeline.TimelineSuggestionBlockUiState
import app.logdate.ui.timeline.collectLazyTimelineAudioNoteIds
import app.logdate.util.now
import coil3.compose.AsyncImage
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.datetime.LocalDate
import logdate.client.ui.generated.resources.Res
import logdate.client.ui.generated.resources.a_long_time_passed
import logdate.client.ui.generated.resources.add_your_birthday_in_settings_to_see_something_special_here
import logdate.client.ui.generated.resources.cd_timeline_photo
import logdate.client.ui.generated.resources.cd_timeline_video
import logdate.client.ui.generated.resources.congrats_curious_explorer
import logdate.client.ui.generated.resources.happy_birthday
import logdate.client.ui.generated.resources.journey_days_count
import logdate.client.ui.generated.resources.post_restore_import_action
import logdate.client.ui.generated.resources.post_restore_import_message
import logdate.client.ui.generated.resources.post_restore_start_fresh
import logdate.client.ui.generated.resources.post_restore_welcome_back
import logdate.client.ui.generated.resources.suggestion_draft_fallback
import logdate.client.ui.generated.resources.timeline_empty_action
import logdate.client.ui.generated.resources.timeline_empty_message
import logdate.client.ui.generated.resources.timeline_empty_title
import logdate.client.ui.generated.resources.youve_reached_the_end
import org.jetbrains.compose.resources.stringResource
import kotlin.math.absoluteValue
import kotlin.uuid.Uuid

sealed class EndOfTimelineUiState {
    data class BirthdayCelebration(
        val birthDate: LocalDate,
        val daysSinceBirth: Int,
    ) : EndOfTimelineUiState()

    data object DiscoveryEasterEgg : EndOfTimelineUiState()
}

internal enum class TimelineDayLayoutMode {
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

internal data class TimelineDayStyle(
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
    onShareMemory: (TimelineSuggestionBlockUiState) -> Unit = {},
    onVisibleAudioNoteIdsChanged: (Set<Uuid>) -> Unit = {},
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
                is TimelineSuggestionBlock.UpcomingEvent ->
                    TimelineSuggestionBlockUiState(
                        type = TimelineSuggestionBlockType.UPCOMING_EVENT,
                        message = timelineSuggestion.title,
                        eventId = timelineSuggestion.eventId,
                        eventStartTime = timelineSuggestion.startTime,
                        location = timelineSuggestion.placeName,
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

        LaunchedEffect(listState, items) {
            snapshotFlow {
                val visibleDayIndices =
                    listState.layoutInfo.visibleItemsInfo
                        .mapNotNull { itemInfo ->
                            val dayIndex = itemInfo.index - 1
                            dayIndex.takeIf { it in items.indices }
                        }.toSet()

                collectLazyTimelineAudioNoteIds(
                    items = items,
                    visibleDayIndices = visibleDayIndices,
                )
            }.distinctUntilChanged()
                .collect { noteIds ->
                    onVisibleAudioNoteIdsChanged(noteIds)
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
                    } else {
                        TimelineEmptyState(
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
                    text = state.birthDate.asRelativeDate(LocalToday.current),
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
    val style = defaultDayStyle()

    if (layoutMode == TimelineDayLayoutMode.COMPACT) {
        // Compact: no rail, inline date
        Column(
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
            modifier =
                modifier
                    .fillMaxWidth()
                    .clickable { onOpenDay(item.date) }
                    .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        ) {
            InlineDateHeader(item = item)
            SemanticTimelineDayContent(item = item, style = style, layoutMode = layoutMode)
        }
    } else {
        // Medium/Expanded: rail on the left
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.xl),
            modifier =
                modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.lg)
                    .clip(RoundedCornerShape(32.dp))
                    .clickable { onOpenDay(item.date) }
                    .padding(vertical = Spacing.md),
        ) {
            TimelineDayRail(item = item, style = style, layoutMode = layoutMode)
            Column(
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
                modifier = Modifier.weight(1f),
            ) {
                SemanticTimelineDayHeader(item = item, style = style, layoutMode = layoutMode)
                SemanticTimelineDayContent(item = item, style = style, layoutMode = layoutMode)
            }
        }
    }
}

@Composable
private fun InlineDateHeader(
    item: TimelineDayUiState,
    modifier: Modifier = Modifier,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        modifier = modifier,
    ) {
        Text(
            text = item.date.asRelativeDate(LocalToday.current),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
        )
        item.supportingSummary?.let { summary ->
            Text(
                text = summary,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun defaultDayStyle(): TimelineDayStyle =
    TimelineDayStyle(
        accentColor = MaterialTheme.colorScheme.primary,
        railColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
        softAccentColor = MaterialTheme.colorScheme.primaryContainer,
        chipColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.32f),
        textHighlightColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f),
    )

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
internal fun TimelineMediaTile(
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
            contentDescription =
                stringResource(
                    if (media.isVideo) Res.string.cd_timeline_video else Res.string.cd_timeline_photo,
                ),
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
internal fun AudioWaveBars(
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


internal fun Long.toDurationLabel(): String {
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
private fun TimelineEmptyState(
    onStartWriting: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        modifier =
            modifier
                .fillMaxWidth()
                .padding(vertical = Spacing.xl),
    ) {
        Text(
            text = stringResource(Res.string.timeline_empty_title),
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            text = stringResource(Res.string.timeline_empty_message),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FilledTonalButton(onClick = onStartWriting) {
            Text(stringResource(Res.string.timeline_empty_action))
        }
    }
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
