@file:Suppress("ktlint:standard:function-naming")

package app.logdate.ui.workspace

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.absolutePadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.logdate.ui.common.handwritingDelegator
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

/**
 * The workspace app bar in a desktop window's caption bar: Search and [actions] between the system's
 * own controls, at the caption's height. Presses on them go to LogDate; the rest of the bar still
 * drags the window.
 */
@Composable
internal fun WorkspaceCaptionAppBar(
    title: String,
    captionBar: WorkspaceCaptionBar,
    onSearch: () -> Unit,
    actions: @Composable RowScope.() -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(captionBar.height)
            .absolutePadding(left = captionBar.left + Spacing.sm, right = captionBar.right + Spacing.sm)
            .semantics { paneTitle = title },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            WorkspaceSearchBar(onSearch, Modifier.captionBarControl(), height = captionBar.height - Spacing.sm)
        }
        Row(Modifier.captionBarControl(), verticalAlignment = Alignment.CenterVertically, content = actions)
    }
}

/**
 * The workspace search pill. [height] is its height; the default is the standard search bar's, and
 * the caption bar passes a shorter one.
 */
@Composable
fun WorkspaceSearchBar(
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = SEARCH_BAR_HEIGHT,
) {
    val binding = LocalWorkspaceSearchController.current?.binding
    if (binding != null) {
        WorkspaceSearchField(
            query = binding.query,
            hint = binding.hint,
            onQueryChange = binding.onQuery,
            modifier = modifier,
            height = height,
        )
        return
    }
    Surface(
        onClick = onSearch,
        modifier = modifier.searchBarMaxWidth().testTag("workspace_search").handwritingDelegator(onSearch),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.workspaceContainer(WorkspaceSurface.Raised),
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Row(
            Modifier.heightIn(min = height).padding(horizontal = Spacing.lg),
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

/**
 * Shared editable form of the workspace search surface for bounded collection searches. Below the
 * standard [height] the field is drawn compactly, since a Material text field cannot be shorter.
 */
@Composable
fun WorkspaceSearchField(
    query: String,
    hint: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = SEARCH_BAR_HEIGHT,
) {
    if (height < SEARCH_BAR_HEIGHT) {
        CompactSearchField(query, hint, onQueryChange, height, modifier)
        return
    }
    TextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text(hint, maxLines = 1, overflow = TextOverflow.Ellipsis) },
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
}

@Composable
private fun CompactSearchField(
    query: String,
    hint: String,
    onQueryChange: (String) -> Unit,
    height: Dp,
    modifier: Modifier,
) {
    BasicTextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        modifier = modifier.searchBarMaxWidth().testTag("workspace_search"),
        decorationBox = { field ->
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.workspaceContainer(WorkspaceSurface.Raised),
                contentColor = MaterialTheme.colorScheme.onSurface,
            ) {
                Row(
                    Modifier.heightIn(min = height).padding(horizontal = Spacing.lg),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    Icon(PlatformIcons.search(), contentDescription = null)
                    Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        if (query.isEmpty()) {
                            Text(
                                hint,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        field()
                    }
                }
            }
        },
    )
}

/** The standard Material search bar height. */
private val SEARCH_BAR_HEIGHT = 56.dp
