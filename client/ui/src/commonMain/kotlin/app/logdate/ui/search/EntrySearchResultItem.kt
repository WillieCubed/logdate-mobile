@file:Suppress("ktlint:standard:function-naming")

package app.logdate.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow

/**
 * Shared composable for rendering a single entry search result.
 *
 * Used by both the journals expanded search and the global search screen.
 */
@Composable
fun EntrySearchResultItem(
    state: EntrySearchResultUiState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ListItem(
        headlineContent = {
            Text(
                text = parseSnippetMarkers(state.content),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        supportingContent = {
            Text(
                text = "${state.dateLabel} · ${state.typeLabel}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        leadingContent = {
            Icon(state.typeIcon, contentDescription = state.typeLabel)
        },
        modifier = modifier.clickable(onClick = onClick),
    )
}
