@file:Suppress("ktlint:standard:function-naming", "ktlint:standard:no-wildcard-imports")

package app.logdate.ui.timeline

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.logdate.ui.common.MetadataChip
import app.logdate.ui.common.PeopleMetadataChip
import app.logdate.ui.theme.Spacing
import coil3.compose.AsyncImage
import kotlinx.datetime.LocalDate
import logdate.client.ui.generated.resources.*
import logdate.client.ui.generated.resources.Res
import org.jetbrains.compose.resources.stringResource

/**
 * Represents different types of timeline suggestion blocks that can be shown to users.
 */
sealed interface TimelineSuggestionBlock {
    /**
     * The user has an unfinished draft to complete.
     */
    data class CompleteDraft(
        val draftId: String,
    ) : TimelineSuggestionBlock

    /**
     * Encourage writing when the user has no entries today.
     */
    data class EmptyDay(
        val message: String,
        val locationName: String? = null,
    ) : TimelineSuggestionBlock

    /**
     * "On this day" memory recall — past entries worth revisiting.
     */
    data class MemoryRecall(
        val memoryDate: LocalDate,
        val title: String,
        val people: List<String> = emptyList(),
        val mediaUris: List<MediaObjectUiState> = emptyList(),
        val isAiGenerated: Boolean = false,
    ) : TimelineSuggestionBlock
}

data class TimelineSuggestionBlockUiState(
    val type: TimelineSuggestionBlockType,
    val message: String,
    val draftId: String? = null,
    val memoryDate: LocalDate? = null,
    val location: String? = null,
    val people: List<String> = emptyList(),
    val mediaUris: List<MediaObjectUiState> = emptyList(),
    val isAiGenerated: Boolean = false,
)

enum class TimelineSuggestionBlockType {
    /**
     * The user has an unfinished draft.
     */
    COMPLETE_DRAFT,

    /**
     * The user has no entries for today.
     */
    EMPTY_DAY,

    /**
     * A past memory worth revisiting — "on this day" style.
     */
    MEMORY_RECALL,
}

/**
 * A prompt to suggest adding memories to the timeline.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelineSuggestionBlock(
    state: TimelineSuggestionBlockUiState,
    onStartWriting: () -> Unit,
    onOpenDraft: (draftId: String) -> Unit,
    onViewMemoryDay: (LocalDate) -> Unit,
    onShareMemory: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val suggestedTypeTitle =
        when (state.type) {
            TimelineSuggestionBlockType.COMPLETE_DRAFT -> stringResource(Res.string.suggestion_label_draft)
            TimelineSuggestionBlockType.EMPTY_DAY -> stringResource(Res.string.suggestion_label_today)
            TimelineSuggestionBlockType.MEMORY_RECALL -> stringResource(Res.string.suggestion_label_memory)
        }

    val containerColor =
        when (state.type) {
            TimelineSuggestionBlockType.EMPTY_DAY -> MaterialTheme.colorScheme.tertiaryContainer
            else -> MaterialTheme.colorScheme.primaryContainer
        }

    val contentColor =
        when (state.type) {
            TimelineSuggestionBlockType.EMPTY_DAY -> MaterialTheme.colorScheme.onTertiaryContainer
            else -> MaterialTheme.colorScheme.onPrimaryContainer
        }

    val labelColor =
        when (state.type) {
            TimelineSuggestionBlockType.EMPTY_DAY -> MaterialTheme.colorScheme.tertiary
            else -> MaterialTheme.colorScheme.primary
        }

    Column(
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        modifier = modifier.widthIn(min = 360.dp),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = containerColor,
            contentColor = contentColor,
            shape = MaterialTheme.shapes.large,
        ) {
            Column {
                Column(
                    modifier =
                        Modifier
                            .padding(Spacing.lg)
                            .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    Text(
                        suggestedTypeTitle,
                        style =
                            MaterialTheme.typography.labelMedium.copy(
                                color = labelColor,
                            ),
                    )
                    Text(state.message, style = MaterialTheme.typography.titleMedium)
                }
                if (state.mediaUris.isNotEmpty()) {
                    RecentMediaCarousel(state.mediaUris)
                }
                SuggestionMetadata(state)
            }
        }

        SuggestionActions(
            state = state,
            onStartWriting = onStartWriting,
            onOpenDraft = onOpenDraft,
            onViewMemoryDay = onViewMemoryDay,
            onShareMemory = onShareMemory,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun SuggestionMetadata(
    state: TimelineSuggestionBlockUiState,
    modifier: Modifier = Modifier,
) {
    val hasMetadata = state.people.isNotEmpty() || state.location != null

    if (!hasMetadata) return

    Row(
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        modifier =
            modifier.padding(
                start = Spacing.lg,
                end = Spacing.lg,
                top = Spacing.sm,
                bottom = Spacing.lg,
            ),
    ) {
        if (state.people.isNotEmpty()) {
            PeopleMetadataChip(state.people)
        }
        if (state.location != null) {
            MetadataChip(
                label = stringResource(Res.string.suggestion_near_location, state.location),
                icon = {
                    Icon(
                        Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecentMediaCarousel(
    mediaUris: List<MediaObjectUiState>,
    modifier: Modifier = Modifier,
) {
    val carouselState = rememberCarouselState(itemCount = mediaUris::size)

    HorizontalMultiBrowseCarousel(
        state = carouselState,
        preferredItemWidth = 200.dp,
        itemSpacing = Spacing.sm,
        contentPadding =
            PaddingValues(
                horizontal = Spacing.lg,
                vertical = Spacing.sm,
            ),
        modifier =
            modifier
                .height(142.dp)
                .fillMaxWidth(),
    ) {
        mediaUris.forEach { media ->
            key(media.uid) {
                AsyncImage(
                    model = media.uri,
                    contentDescription = stringResource(Res.string.suggestion_media_item),
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.extraLarge),
                    contentScale = ContentScale.Crop,
                )
            }
        }
    }
}

/**
 * Actions section for timeline suggestions that adapts based on suggestion type.
 */
@Composable
private fun SuggestionActions(
    state: TimelineSuggestionBlockUiState,
    onStartWriting: () -> Unit,
    onOpenDraft: (String) -> Unit,
    onViewMemoryDay: (LocalDate) -> Unit,
    onShareMemory: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(Spacing.md, Alignment.End),
        modifier = modifier,
    ) {
        when (state.type) {
            TimelineSuggestionBlockType.COMPLETE_DRAFT -> {
                AssistChip(
                    onClick = { state.draftId?.let(onOpenDraft) },
                    label = { Text(stringResource(Res.string.finish_draft)) },
                    leadingIcon = {
                        Icon(
                            Icons.Default.EditNote,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    },
                )
            }
            TimelineSuggestionBlockType.EMPTY_DAY -> {
                AssistChip(
                    onClick = onStartWriting,
                    label = { Text(stringResource(Res.string.start_writing)) },
                    leadingIcon = {
                        Icon(
                            Icons.Default.EditNote,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                        )
                    },
                )
            }
            TimelineSuggestionBlockType.MEMORY_RECALL -> {
                AssistChip(
                    onClick = { state.memoryDate?.let(onViewMemoryDay) },
                    label = { Text(stringResource(Res.string.view_memory)) },
                    leadingIcon = {
                        Icon(
                            Icons.Default.History,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    },
                )
                AssistChip(
                    onClick = { state.memoryDate?.let(onShareMemory) },
                    label = { Text(stringResource(Res.string.share_link)) },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Share,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    },
                )
            }
        }
    }
}

@Preview
@Composable
private fun TimelineSuggestionBlockPreview_MemoryRecall() {
    val state =
        TimelineSuggestionBlockUiState(
            type = TimelineSuggestionBlockType.MEMORY_RECALL,
            message = "Trip to Six Flags Over Texas",
            mediaUris =
                listOf(
                    MediaObjectUiState(uri = "https://picsum.photos/200/300", uid = "1"),
                    MediaObjectUiState(uri = "https://picsum.photos/200/300", uid = "2"),
                    MediaObjectUiState(uri = "https://picsum.photos/200/300", uid = "3"),
                    MediaObjectUiState(uri = "https://picsum.photos/200/300", uid = "4"),
                ),
            people = listOf("Lane", "Anna", "Willie", "Jake"),
        )
    TimelineSuggestionBlock(
        state,
        onStartWriting = {},
        onOpenDraft = {},
        onViewMemoryDay = {},
        onShareMemory = {},
    )
}

@Preview
@Composable
private fun TimelineSuggestionBlockPreview_CompleteDraft() {
    val state =
        TimelineSuggestionBlockUiState(
            type = TimelineSuggestionBlockType.COMPLETE_DRAFT,
            message = "Finish your draft while it's still fresh.",
            draftId = "draft-1",
        )
    TimelineSuggestionBlock(
        state,
        onStartWriting = {},
        onOpenDraft = {},
        onViewMemoryDay = {},
        onShareMemory = {},
    )
}

@Preview
@Composable
private fun TimelineSuggestionBlockPreview_EmptyDay() {
    val state =
        TimelineSuggestionBlockUiState(
            type = TimelineSuggestionBlockType.EMPTY_DAY,
            message = "What's going on?",
            location = "Dallas",
        )
    TimelineSuggestionBlock(
        state,
        onStartWriting = {},
        onOpenDraft = {},
        onViewMemoryDay = {},
        onShareMemory = {},
    )
}
