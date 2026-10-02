@file:Suppress("ktlint:standard:function-naming")

package app.logdate.ui.workspace

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.logdate.ui.platform.PlatformIcons
import app.logdate.ui.search.searchBarMaxWidth
import app.logdate.ui.theme.Spacing
import app.logdate.ui.theme.WorkspaceSurface
import app.logdate.ui.theme.workspaceContainer
import logdate.client.ui.generated.resources.Res
import logdate.client.ui.generated.resources.workspace_search_hint
import org.jetbrains.compose.resources.stringResource

val LocalWorkspaceSearchAction = staticCompositionLocalOf<(() -> Unit)?> { null }

/** Search belongs to the workspace, outside independently composed collection and detail panels. */
@Composable
fun WorkspaceAppBar(
    title: String,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    BoxWithConstraints(modifier.fillMaxWidth().padding(Spacing.lg).semantics { paneTitle = title }) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                WorkspaceSearchBar(onSearch)
            }
            actions()
        }
    }
}

@Composable
fun WorkspaceSearchBar(
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val binding = LocalWorkspaceSearchController.current?.binding
    if (binding != null) {
        TextField(
            value = binding.query,
            onValueChange = binding.onQuery,
            placeholder = { Text(binding.hint, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            leadingIcon = { Icon(PlatformIcons.search(), null) },
            singleLine = true,
            modifier = modifier.searchBarMaxWidth().testTag("workspace_search"),
            shape = MaterialTheme.shapes.extraLarge,
            colors =
                TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.workspaceContainer(WorkspaceSurface.Raised),
                    unfocusedContainerColor = MaterialTheme.colorScheme.workspaceContainer(WorkspaceSurface.Raised),
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
        )
        return
    }
    Surface(
        onClick = onSearch,
        modifier = modifier.searchBarMaxWidth().testTag("workspace_search"),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.workspaceContainer(WorkspaceSurface.Raised),
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Row(
            Modifier.heightIn(min = 56.dp).padding(horizontal = Spacing.lg, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Icon(PlatformIcons.search(), contentDescription = null)
            Text(
                stringResource(Res.string.workspace_search_hint),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
