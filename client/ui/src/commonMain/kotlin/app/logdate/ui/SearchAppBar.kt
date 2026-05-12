@file:Suppress("ktlint:standard:function-naming", "ktlint:standard:no-wildcard-imports")

package app.logdate.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.logdate.ui.common.conditional
import app.logdate.ui.theme.Spacing
import logdate.client.ui.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Stable
class SearchAppBarState(
    initialQuery: String = "",
) {
    var query by mutableStateOf(initialQuery)
}

@Composable
fun rememberSearchAppBarState(initialQuery: String = ""): SearchAppBarState = remember(initialQuery) { SearchAppBarState(initialQuery) }

/**
 * A search bar that can be expanded to show a search field.
 */
@Composable
fun SearchBarBase(
    expanded: Boolean,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit = {},
    onNavigationClick: () -> Unit = {},
    hint: String = "Search",
    query: String = "",
    onQueryChange: (String) -> Unit = {},
    content: @Composable () -> Unit = {},
) {
    val cornerRadius by animateDpAsState(
        targetValue = if (expanded) 0.dp else 48.dp,
        label = "cornerRadius",
    )
    Surface(
        onClick = onExpand,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(cornerRadius),
        modifier =
            modifier
                .widthIn(min = 360.dp, max = 720.dp)
                .heightIn(min = 56.dp),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
            horizontalAlignment = Alignment.Start,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    Modifier
                        .padding(top = 4.dp)
                        .conditional(
                            expanded,
                            modifyIfTrue = {
                                padding(start = 8.dp, end = 16.dp)
                            },
                            modifyIfFalse = {
                                padding(start = 12.dp, end = 16.dp)
                            },
                        ).fillMaxWidth(),
            ) {
                IconButton(
                    onClick = {
                        if (expanded) {
                            onDismiss()
                        } else {
                            onNavigationClick()
                        }
                    },
                ) {
                    val icon =
                        if (expanded) {
                            Icons.AutoMirrored.Default.ArrowBack
                        } else {
                            Icons.Default.Menu
                        }
                    val contentDescription =
                        if (expanded) {
                            "Close search"
                        } else {
                            "Open navigation"
                        }
                    Icon(icon, contentDescription)
                }
                Box(
                    modifier =
                        Modifier
                            .weight(1f)
                            .padding(end = 8.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    BasicTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        singleLine = true,
                        textStyle =
                            TextStyle(
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = MaterialTheme.typography.bodyLarge.fontSize,
                            ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (query.isEmpty()) {
                        Text(
                            text = hint,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Icon(Icons.Default.Search, contentDescription = stringResource(Res.string.search))
            }
            content()
        }
    }
}

@Composable
fun SearchAppBar(
    modifier: Modifier = Modifier,
    hint: String = "Search",
    onExpand: () -> Unit = {},
    onNavigationClick: () -> Unit = {},
    expanded: Boolean = false,
    state: SearchAppBarState = rememberSearchAppBarState(),
    expandedContent: @Composable () -> Unit = {},
) {
    SearchBarBase(
        modifier =
            modifier
                .conditional(!expanded) {
                    padding(vertical = Spacing.sm, horizontal = Spacing.lg)
                },
        expanded = expanded,
        onExpand = onExpand,
        onNavigationClick = onNavigationClick,
        hint = hint,
        query = state.query,
        onQueryChange = { state.query = it },
    ) {
        expandedContent()
    }
}

val AVATAR_SIZE = 30.dp

@Preview
@Composable
private fun SearchBarBasePreview() {
    SearchBarBase(
        expanded = false,
        onExpand = {},
    )
}

@Preview
@Composable
private fun SearchAppBarPreview() {
    SearchAppBar()
}

@Preview
@Composable
private fun SearchAppBarPreview_Expanded() {
    SearchAppBar(expanded = true) {
        Box(Modifier.padding(16.dp)) {
            Text(stringResource(Res.string.expanded_content))
        }
    }
}
