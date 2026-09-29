@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.library.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.logdate.ui.platform.PlatformIcons
import app.logdate.ui.search.searchBarMaxWidth
import logdate.client.feature.library.generated.resources.Res
import logdate.client.feature.library.generated.resources.cd_search
import logdate.client.feature.library.generated.resources.search_library
import org.jetbrains.compose.resources.stringResource

/**
 * Opens the shared search route for Library media.
 */
@Composable
fun LibraryTopBar(
    onOpenSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onOpenSearch,
        modifier = modifier.searchBarMaxWidth().testTag("library_search_action"),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                painter = PlatformIcons.search(),
                contentDescription = stringResource(Res.string.cd_search),
            )
            Text(
                text = stringResource(Res.string.search_library),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
