@file:Suppress("ktlint:standard:function-naming")

package app.logdate.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import app.logdate.client.repository.search.SearchContentType
import app.logdate.client.repository.search.SearchResult
import app.logdate.ui.common.focusableWithRing
import app.logdate.util.toReadableDateTimeShort

data class UniversalSearchResultUiState(
    val id: String,
    val contentText: AnnotatedString,
    val supportingText: String,
    val typeLabel: String,
    val typeIcon: ImageVector,
)

/**
 * Renders a single universal search result with type-specific icon and label.
 *
 * Handles all [SearchContentType] variants with appropriate visual treatment.
 * Search row state is precomputed before rendering to keep recomposition cheap.
 */
@Composable
fun UniversalSearchResultItem(
    state: UniversalSearchResultUiState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ListItem(
        headlineContent = {
            Text(
                text = state.contentText,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        supportingContent = {
            Text(
                text = state.supportingText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        leadingContent = {
            Icon(state.typeIcon, contentDescription = state.typeLabel)
        },
        modifier = modifier.focusableWithRing().clickable(onClick = onClick),
    )
}

fun SearchResult.toUniversalSearchResultUiState(): UniversalSearchResultUiState {
    val (icon, typeLabel) = contentType.visualInfo()
    return UniversalSearchResultUiState(
        id = "${contentType.ftsValue}_$uid",
        contentText = parseSnippetMarkers(content),
        supportingText = "${created.toReadableDateTimeShort()} · $typeLabel",
        typeLabel = typeLabel,
        typeIcon = icon,
    )
}

private fun SearchContentType.visualInfo(): Pair<ImageVector, String> =
    when (this) {
        SearchContentType.TEXT_NOTE -> Icons.AutoMirrored.Default.Notes to "Note"
        SearchContentType.TRANSCRIPTION -> Icons.Default.Mic to "Voice note"
        SearchContentType.JOURNAL -> Icons.Default.Book to "Journal"
        SearchContentType.MEDIA_CAPTION -> Icons.Default.Image to "Photo"
        SearchContentType.PLACE -> Icons.Default.LocationOn to "Place"
        SearchContentType.REWIND -> Icons.Default.Replay to "Rewind"
        SearchContentType.STICKER -> Icons.Default.Star to "Sticker"
        SearchContentType.POSTCARD -> Icons.Default.Mail to "Postcard"
        SearchContentType.AMBIENT_SOUND -> Icons.Default.GraphicEq to "Soundscape"
    }
