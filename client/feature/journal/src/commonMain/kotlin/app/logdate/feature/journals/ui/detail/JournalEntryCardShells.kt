@file:Suppress("ktlint:standard:function-naming")
@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)

package app.logdate.feature.journals.ui.detail

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.logdate.ui.theme.Spacing
import app.logdate.util.localTime
import logdate.client.feature.journal.generated.resources.Res
import logdate.client.feature.journal.generated.resources.journal_settings_label
import logdate.client.feature.journal.generated.resources.remove_from_journal
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Instant

@Composable
internal fun VerticalEntryCardShell(
    timestamp: Instant,
    onClick: () -> Unit,
    onRemoveFromJournal: () -> Unit,
    modifier: Modifier = Modifier,
    cardModifier: Modifier = Modifier,
    contentPadding: Dp = Spacing.md,
    content: @Composable () -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        Text(
            text = timestamp.localTime,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp),
        )

        Card(
            onClick = onClick,
            modifier = cardModifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(contentPadding)) {
                content()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    EntryOverflowMenu(
                        showMenu = showMenu,
                        onShowMenu = { showMenu = true },
                        onDismiss = { showMenu = false },
                        onRemoveFromJournal = {
                            showMenu = false
                            onRemoveFromJournal()
                        },
                    )
                }
            }
        }
    }
}

/**
 * Card wrapper for inline entry types (text, audio) displayed in a row
 * alongside the overflow menu.
 */
@Composable
internal fun InlineEntryCardShell(
    timestamp: Instant,
    onClick: () -> Unit,
    onRemoveFromJournal: () -> Unit,
    modifier: Modifier = Modifier,
    cardModifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        Text(
            text = timestamp.localTime,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp),
        )

        Card(
            onClick = onClick,
            modifier = cardModifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 16.dp, end = 4.dp),
                verticalAlignment = Alignment.Top,
            ) {
                content()
                EntryOverflowMenu(
                    showMenu = showMenu,
                    onShowMenu = { showMenu = true },
                    onDismiss = { showMenu = false },
                    onRemoveFromJournal = {
                        showMenu = false
                        onRemoveFromJournal()
                    },
                )
            }
        }
    }
}

@Composable
private fun EntryOverflowMenu(
    showMenu: Boolean,
    onShowMenu: () -> Unit,
    onDismiss: () -> Unit,
    onRemoveFromJournal: () -> Unit,
) {
    Box {
        IconButton(onClick = onShowMenu) {
            Icon(
                Icons.Rounded.MoreVert,
                contentDescription = stringResource(Res.string.journal_settings_label),
            )
        }
        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = onDismiss,
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.remove_from_journal)) },
                onClick = onRemoveFromJournal,
                leadingIcon = {
                    Icon(Icons.Rounded.RemoveCircleOutline, contentDescription = null)
                },
            )
        }
    }
}

// endregion

// region Dialogs
