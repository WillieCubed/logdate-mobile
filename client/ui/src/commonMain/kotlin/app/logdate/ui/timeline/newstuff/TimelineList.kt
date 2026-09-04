@file:OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@file:Suppress("ktlint:standard:function-naming")

package app.logdate.ui.timeline.newstuff

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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

/** Widest the timeline's reading column is allowed to get, regardless of window size. */
private val TIMELINE_MAX_CONTENT_WIDTH = 720.dp

internal data class TimelineDayStyle(
    val accentColor: Color,
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

    // fillMaxWidth so the capped reading column below has room to centre itself; without it the
    // box shrink-wraps to the column and the feed hugs the left edge on wide windows.
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
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

        // Resolve visible days by item key rather than by list position. Sticky headers mean a
        // day no longer occupies a single predictable slot, and position arithmetic would keep
        // resolving to *some* day rather than failing visibly — silently prefetching
        // transcription for the wrong notes.
        val dayIndicesByKey =
            remember(items) {
                items.withIndex().associate { (index, item) -> item.date.toString() to index }
            }

        LaunchedEffect(listState, items, dayIndicesByKey) {
            snapshotFlow {
                val visibleDayIndices =
                    listState.layoutInfo.visibleItemsInfo
                        .mapNotNull { itemInfo -> dayIndicesByKey[itemInfo.key] }
                        .toSet()

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
            // Cap the reading column. Without this a day stretches to the full window on a
            // tablet, which runs the summary out to ~90 characters per line and lets a single
            // photo — sized by aspect ratio off its own width — grow taller than the viewport.
            modifier =
                Modifier
                    .fillMaxHeight()
                    .widthIn(max = TIMELINE_MAX_CONTENT_WIDTH)
                    .align(Alignment.TopCenter),
            state = listState,
            // No uniform gap: a day's sticky header must sit tight against the content it
            // labels. Day-to-day separation is applied by the day item's own bottom padding.
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
                items.forEachIndexed { index, item ->
                    stickyHeader(key = "header-${item.date}") {
                        TimelineDayHeaderBar(item = item)
                    }

                    item(
                        key = item.date.toString(),
                        contentType = layoutMode.contentType(),
                    ) {
                        TimelineDayListItem(
                            item = item,
                            layoutMode = layoutMode,
                            onOpenDay = onOpenDay,
                            modifier =
                                Modifier
                                    .padding(bottom = Spacing.xl)
                                    .applyPaddingIfLast(
                                        currentIndex = index,
                                        totalItems = items.size,
                                    ),
                        )
                    }

                    if (index < items.lastIndex) {
                        val daysBetween =
                            (item.date.toEpochDays() - items[index + 1].date.toEpochDays()).absoluteValue
                        if (daysBetween > 10) {
                            item(
                                key = "gap-${item.date}",
                                contentType = TimelineListContentType.GAP,
                            ) {
                                TimeGapMessageItem()
                            }
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
                .padding(start = Spacing.lg),
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
    Column(
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
        modifier =
            modifier
                .fillMaxWidth()
                .clickable { onOpenDay(item.date) }
                .padding(horizontal = Spacing.lg, vertical = Spacing.md),
    ) {
        item.supportingSummary?.let { summary ->
            Text(
                text = summary,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        SemanticTimelineDayContent(
            item = item,
            style = item.layout.style(),
            layoutMode = layoutMode,
        )
    }
}

/**
 * The day's date, pinned while that day's content is on screen.
 *
 * This replaces the old left rail, which reserved up to 88dp of every row to show a day number
 * beside a header that already named the same date, and whose connecting spine never drew: it
 * sized itself with [fillMaxHeight] inside a row whose height constraint is unbounded.
 */
@Composable
private fun TimelineDayHeaderBar(
    item: TimelineDayUiState,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(
            text = item.date.asRelativeDate(LocalToday.current),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = item.layout.style().accentColor,
            modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        )
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
                .padding(start = Spacing.lg),
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
                .padding(start = Spacing.lg),
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
    Column(
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg),
    ) {
        PlaceholderLine(width = 96.dp, height = 14.dp)
        PlaceholderLine(width = 280.dp, height = 28.dp)
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
                softAccentColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f),
                chipColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.32f),
                textHighlightColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f),
            )
        TimelineDayCardLayout.VOICE_LED ->
            TimelineDayStyle(
                accentColor = MaterialTheme.colorScheme.secondary,
                softAccentColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.52f),
                chipColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.34f),
                textHighlightColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.22f),
            )
        TimelineDayCardLayout.PLACE_LED ->
            TimelineDayStyle(
                accentColor = MaterialTheme.colorScheme.tertiary,
                softAccentColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.52f),
                chipColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.34f),
                textHighlightColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.22f),
            )
        TimelineDayCardLayout.STORY_LED ->
            TimelineDayStyle(
                accentColor = MaterialTheme.colorScheme.primary,
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
