package app.logdate.ui.timeline

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddLink
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.logdate.ui.common.PeopleMetadataChip
import app.logdate.ui.theme.Spacing
import coil3.compose.AsyncImage
import org.jetbrains.compose.ui.tooling.preview.Preview

data class SuggestedEntryBlockUiState(
    val memoryId: String,
    val type: SuggestedEntryBlockType = SuggestedEntryBlockType.UPDATE,
    val message: String,
    val location: String? = null,
    /**
     * First names of people in the suggested entry.
     */
    val people: List<String> = emptyList(),
    val mediaUris: List<MediaObjectUiState> = emptyList(),
)

enum class SuggestedEntryBlockType {

    /**
     * An event has already been documented by someone else, but the user has not yet added memories.
     */
    UPDATE,

    /**
     * An event that the user is associated with is currently happening.
     */
    HAPPENING_NOW,

    /**
     * There is a (calendar) event that the user has not yet documented.
     */
    EVENT,
}

/**
 * A prompt to suggest adding memories to the timeline.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SuggestedEntryBlock(
    state: SuggestedEntryBlockUiState,
    onAddToMemory: (memoryId: String) -> Unit,
    onShare: (memoryId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val suggestedTypeTitle = when (state.type) {
        SuggestedEntryBlockType.UPDATE -> "Update"
        SuggestedEntryBlockType.HAPPENING_NOW -> "Happening Now"
        SuggestedEntryBlockType.EVENT -> "Event"
    }

    fun onMediaClick(uid: String) {
        // TODO: Handle media click, maybe full-screen/modal?
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        modifier = modifier.widthIn(min = 360.dp)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            shape = MaterialTheme.shapes.large,
        ) {
            Column {
                Column(
                    modifier = Modifier
                        .padding(Spacing.lg)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    Text(
                        suggestedTypeTitle,
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = MaterialTheme.colorScheme.primary
                        )
                    )
                    Text(state.message, style = MaterialTheme.typography.titleMedium)
                }
                if (state.mediaUris.isNotEmpty()) {
                    RecentMediaCarousel(state.mediaUris, ::onMediaClick)
                }
                if (state.people.isNotEmpty()) {
                    Row(
                        modifier = Modifier.padding(
                            start = Spacing.lg,
                            end = Spacing.lg,
                            top = Spacing.sm,
                            bottom = Spacing.lg,
                        ),
                    ) {
                        PeopleMetadataChip(state.people)
                    }
                }
            }
        }
        AssistChipsBlock(
            onAddToMemory = {
                onAddToMemory(state.memoryId)
            },
            onShare = {
                onShare(state.memoryId)
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecentMediaCarousel(
    mediaUris: List<MediaObjectUiState>,
    onClick: (uid: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val carouselState = rememberCarouselState(itemCount = mediaUris::size)

    HorizontalMultiBrowseCarousel(
        state = carouselState,
        preferredItemWidth = 200.dp,
        itemSpacing = Spacing.sm,
        contentPadding = PaddingValues(
            horizontal = Spacing.lg,
            vertical = Spacing.sm,
        ),
        modifier = modifier
            .height(142.dp)
            .fillMaxWidth()
    ) {
        mediaUris.forEach { media ->
            key(media.uid) {
                CarouselItem(
                    uri = media.uri,
                    onClick = { onClick(media.uid) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun CarouselItem(
    uri: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AsyncImage(
        model = uri,
        contentDescription = null,
        modifier = modifier
            .clip(MaterialTheme.shapes.extraLarge)
            .clickable(role = Role.Image, onClick = onClick),
        contentScale = ContentScale.Crop,
//        modifier = Modifier.widthIn(200.dp),
    )
}

@Composable
private fun AssistChipsBlock(
    onAddToMemory: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier,
) {
    // TODO: Figure out why there's internal padding here
    Row(
        horizontalArrangement = Arrangement.spacedBy(Spacing.md, Alignment.End),
        modifier = modifier,
    ) {
        AssistChip(
            onClick = onAddToMemory,
            label = {
                Text("Add to Memories")
            },
            leadingIcon = {
                Icon(
                    Icons.Default.Add,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            },
        )
        AssistChip(
            onClick = onShare,
            label = {
                Text("Share link")
            },
            leadingIcon = {
                Icon(
                    Icons.Default.AddLink,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            },
        )
    }
}

@Preview
@Composable
private fun SuggestedEntryBlockPreview() {
    val state = SuggestedEntryBlockUiState(
        memoryId = "1",
        message = "Trip to Six Flags Over Texas",
        mediaUris = listOf(
            MediaObjectUiState(
                uri = "https://picsum.photos/200/300",
                uid = "1",
            ),
            MediaObjectUiState(
                uri = "https://picsum.photos/200/300",
                uid = "2",
            ),
            MediaObjectUiState(
                uri = "https://picsum.photos/200/300",
                uid = "3",
            ),
            MediaObjectUiState(
                uri = "https://picsum.photos/200/300",
                uid = "4",
            ),
        ),
        people = listOf("Lane", "Anna", "Willie", "Jake"),
    )
    SuggestedEntryBlock(
        state,
        onAddToMemory = {},
        onShare = {},
    )
}

@Preview
@Composable
private fun SuggestedEntryBlockPreview_HappeningNow() {
    val state = SuggestedEntryBlockUiState(
        memoryId = "1",
        type = SuggestedEntryBlockType.HAPPENING_NOW,
        message = "Trip to Six Flags Over Texas",
        mediaUris = emptyList(),
    )
    SuggestedEntryBlock(
        state,
        onAddToMemory = {},
        onShare = {},
    )
}