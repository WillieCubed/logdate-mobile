@file:Suppress("ktlint:standard:function-naming")

package app.logdate.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.Book
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
import androidx.compose.ui.text.style.TextOverflow
import app.logdate.client.repository.search.SearchContentType
import app.logdate.client.repository.search.SearchResult
import app.logdate.util.toReadableDateTimeShort

/**
 * Renders a single universal search result with type-specific icon and label.
 *
 * Handles all [SearchContentType] variants with appropriate visual treatment.
 * Snippet text is highlighted via [parseSnippetMarkers] for FTS5 match markers.
 */
@Composable
fun UniversalSearchResultItem(
    result: SearchResult,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val (icon, typeLabel) = result.contentType.visualInfo()

    ListItem(
        headlineContent = {
            Text(
                text = parseSnippetMarkers(result.content),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        supportingContent = {
            Text(
                text = "${result.created.toReadableDateTimeShort()} · $typeLabel",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        leadingContent = {
            Icon(icon, contentDescription = typeLabel)
        },
        modifier = modifier.clickable(onClick = onClick),
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
    }
