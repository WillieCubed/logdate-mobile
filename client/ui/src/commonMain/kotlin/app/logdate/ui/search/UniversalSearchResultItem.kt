@file:Suppress("ktlint:standard:function-naming")
@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package app.logdate.ui.search

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
    val contentType: SearchContentType,
)

/**
 * Renders a single universal search result.
 *
 * The leading type icon is wrapped in a tonal [Surface] colored by [SearchContentType] so users
 * can scan results visually. Match highlighting in [UniversalSearchResultUiState.contentText] is
 * pre-computed by the caller via [toUniversalSearchResultUiState] using the desired [SpanStyle].
 */
@Composable
fun UniversalSearchResultItem(
    state: UniversalSearchResultUiState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val containerColor: Color
    val contentColor: Color
    when (state.contentType) {
        SearchContentType.JOURNAL,
        SearchContentType.REWIND,
        SearchContentType.PERSON,
        -> {
            containerColor = scheme.primaryContainer
            contentColor = scheme.onPrimaryContainer
        }
        SearchContentType.TRANSCRIPTION,
        SearchContentType.AMBIENT_SOUND,
        SearchContentType.PLACE,
        -> {
            containerColor = scheme.tertiaryContainer
            contentColor = scheme.onTertiaryContainer
        }
        SearchContentType.MEDIA_CAPTION -> {
            containerColor = scheme.surfaceContainerHigh
            contentColor = scheme.onSurface
        }
        SearchContentType.TEXT_NOTE,
        SearchContentType.STICKER,
        SearchContentType.POSTCARD,
        -> {
            containerColor = scheme.secondaryContainer
            contentColor = scheme.onSecondaryContainer
        }
    }
    ListItem(
        headlineContent = {
            Text(
                text = state.contentText,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge,
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
            Surface(
                color = containerColor,
                contentColor = contentColor,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.size(40.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(state.typeIcon, contentDescription = state.typeLabel)
                }
            }
        },
        modifier =
            modifier
                .focusableWithRing()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick,
                ),
    )
}

/**
 * Converts a [SearchResult] into a render-ready [UniversalSearchResultUiState].
 *
 * @param matchStyle The [SpanStyle] to apply to FTS5-matched terms inside [SearchResult.content].
 *   Defaults to the bold-only style; pass a richer style (e.g. with `background`) at the call site
 *   when composable theme colors are available.
 */
fun SearchResult.toUniversalSearchResultUiState(matchStyle: SpanStyle = DefaultMatchStyle): UniversalSearchResultUiState {
    val (icon, typeLabel) = contentType.visualInfo()
    return UniversalSearchResultUiState(
        id = "${contentType.ftsValue}_$uid",
        contentText = parseSnippetMarkers(content, matchStyle),
        supportingText = searchSupportingText(typeLabel),
        typeLabel = typeLabel,
        typeIcon = icon,
        contentType = contentType,
    )
}

private fun SearchResult.searchSupportingText(typeLabel: String): String =
    when (contentType) {
        SearchContentType.PERSON -> typeLabel
        else -> "${created.toReadableDateTimeShort()} · $typeLabel"
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
        SearchContentType.PERSON -> Icons.Default.Person to "Person"
    }

/**
 * Match-highlight style applied to FTS5-snippet matches. Memoized against the current color
 * scheme so the resulting [SpanStyle] is stable as a `remember` key and snippet AnnotatedStrings
 * are not rebuilt on every recomposition.
 */
@Composable
fun rememberSearchHighlightStyle(): SpanStyle {
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer
    val onPrimaryContainer = MaterialTheme.colorScheme.onPrimaryContainer
    return remember(primaryContainer, onPrimaryContainer) {
        SpanStyle(
            background = primaryContainer,
            color = onPrimaryContainer,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
