@file:Suppress("ktlint:standard:function-naming")
@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)

package app.logdate.feature.journals.ui.detail

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.logdate.ui.platform.rememberLogDateHaptics
import app.logdate.ui.theme.Spacing
import logdate.client.feature.journal.generated.resources.Res
import logdate.client.feature.journal.generated.resources.action_delete
import logdate.client.feature.journal.generated.resources.action_remove
import logdate.client.feature.journal.generated.resources.delete_journal_description
import logdate.client.feature.journal.generated.resources.delete_journal_title
import logdate.client.feature.journal.generated.resources.journal_delete_label
import logdate.client.feature.journal.generated.resources.journal_settings_label
import logdate.client.feature.journal.generated.resources.journal_share_label
import logdate.client.feature.journal.generated.resources.remove_from_journal_description
import logdate.client.feature.journal.generated.resources.remove_from_journal_title
import logdate.client.feature.journal.generated.resources.sort_newest_first
import logdate.client.feature.journal.generated.resources.sort_oldest_first
import logdate.client.ui.generated.resources.common_cancel
import org.jetbrains.compose.resources.stringResource
import kotlin.uuid.Uuid
import logdate.client.ui.generated.resources.Res as UiRes

@Composable
internal fun JournalAddMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onCreateEntry: () -> Unit,
    onAddExistingContent: () -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text("Create entry") },
            onClick = {
                onDismiss()
                onCreateEntry()
            },
            leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
        )
        DropdownMenuItem(
            text = { Text("Add existing content") },
            onClick = {
                onDismiss()
                onAddExistingContent()
            },
            leadingIcon = { Icon(Icons.Rounded.Add, contentDescription = null) },
        )
    }
}

@Composable
internal fun JournalDetailBookSummaryPane(
    uiState: JournalDetailUiState.Success,
    onToggleSortOrder: () -> Unit,
    onNavigateToShare: (journalId: Uuid) -> Unit,
    onNavigateToSettings: (journalId: Uuid) -> Unit,
    onRequestDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text(
            text = uiState.title,
            style = MaterialTheme.typography.headlineMedium,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = "${uiState.entries.size} entries",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text =
                if (uiState.sortOrder == SortOrder.NEWEST_FIRST) {
                    stringResource(Res.string.sort_newest_first)
                } else {
                    stringResource(Res.string.sort_oldest_first)
                },
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            JournalDetailPaneAction(
                icon = sortIcon(uiState.sortOrder),
                label =
                    if (uiState.sortOrder == SortOrder.NEWEST_FIRST) {
                        "Show oldest first"
                    } else {
                        "Show newest first"
                    },
                onClick = onToggleSortOrder,
            )
            JournalDetailPaneAction(
                icon = Icons.Rounded.Share,
                label = stringResource(Res.string.journal_share_label),
                onClick = { onNavigateToShare(uiState.journalId) },
            )
            JournalDetailPaneAction(
                icon = Icons.Rounded.Settings,
                label = stringResource(Res.string.journal_settings_label),
                onClick = { onNavigateToSettings(uiState.journalId) },
            )
            JournalDetailPaneAction(
                icon = Icons.Rounded.DeleteOutline,
                label = stringResource(Res.string.journal_delete_label),
                onClick = onRequestDelete,
            )
        }
    }
}

@Composable
private fun JournalDetailPaneAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 1.dp,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null)
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun sortIcon(sortOrder: SortOrder): ImageVector =
    if (sortOrder == SortOrder.NEWEST_FIRST) {
        Icons.Rounded.ArrowDownward
    } else {
        Icons.Rounded.ArrowUpward
    }

@Composable
fun DeleteConfirmationDialog(
    onDismissRequest: () -> Unit,
    onConfirmation: () -> Unit,
    icon: ImageVector = Icons.Rounded.Warning,
) {
    val haptics = rememberLogDateHaptics()
    AlertDialog(
        icon = {
            Icon(icon, contentDescription = null)
        },
        title = {
            Text(text = stringResource(Res.string.delete_journal_title))
        },
        text = {
            Text(text = stringResource(Res.string.delete_journal_description))
        },
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(
                onClick = {
                    haptics.confirmDestruction()
                    onConfirmation()
                },
            ) {
                Text(stringResource(Res.string.action_delete))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismissRequest,
            ) {
                Text(stringResource(UiRes.string.common_cancel))
            }
        },
    )
}

@Composable
internal fun JournalDetailPlaceholder() {
    Row(
        modifier =
            Modifier
                .padding(Spacing.lg)
                .fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Loading...",
        )
    }
}

@Composable
internal fun RemoveNoteFromJournalDialog(
    onDismissRequest: () -> Unit,
    onConfirmation: () -> Unit,
) {
    AlertDialog(
        icon = {
            Icon(Icons.Rounded.Warning, contentDescription = null)
        },
        title = {
            Text(text = stringResource(Res.string.remove_from_journal_title))
        },
        text = {
            Text(text = stringResource(Res.string.remove_from_journal_description))
        },
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(onClick = onConfirmation) {
                Text(stringResource(Res.string.action_remove))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(UiRes.string.common_cancel))
            }
        },
    )
}

// endregion
