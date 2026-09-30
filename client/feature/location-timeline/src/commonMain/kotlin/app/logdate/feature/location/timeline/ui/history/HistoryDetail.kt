@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.location.timeline.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import logdate.client.feature.location.timeline.generated.resources.Res
import logdate.client.feature.location.timeline.generated.resources.history_add_memory
import logdate.client.feature.location.timeline.generated.resources.history_add_visit
import logdate.client.feature.location.timeline.generated.resources.history_cancel
import logdate.client.feature.location.timeline.generated.resources.history_change_activity
import logdate.client.feature.location.timeline.generated.resources.history_change_place
import logdate.client.feature.location.timeline.generated.resources.history_change_time
import logdate.client.feature.location.timeline.generated.resources.history_close
import logdate.client.feature.location.timeline.generated.resources.history_confirm_delete
import logdate.client.feature.location.timeline.generated.resources.history_delete
import logdate.client.feature.location.timeline.generated.resources.history_delete_body
import logdate.client.feature.location.timeline.generated.resources.history_delete_title
import logdate.client.feature.location.timeline.generated.resources.history_link_memory
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HistoryDetail(
    item: HistoryItemUi,
    actions: HumanLocationHistoryActions,
) {
    var confirmDelete by remember(item.id) { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = actions.onCloseDetail) {
        HumanLocationHistoryDetailContent(item, actions, onRequestDelete = { confirmDelete = true })
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(Res.string.history_delete_title)) },
            text = { Text(stringResource(Res.string.history_delete_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    actions.onEdit(item.id, HistoryEditAction.Delete)
                }) { Text(stringResource(Res.string.history_confirm_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(Res.string.history_cancel)) }
            },
        )
    }
}

@Composable
fun HumanLocationHistoryDetailContent(
    item: HistoryItemUi,
    actions: HumanLocationHistoryActions,
    onRequestDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(item.title, style = MaterialTheme.typography.headlineSmall)
        Text(item.timeLabel, style = MaterialTheme.typography.labelLarge)
        if (item.supportingText.isNotBlank()) Text(item.supportingText)
        item.memories.forEach { HistoryMemoryPreview(it, actions.onOpenMemory) }
        val edits =
            when (item.kind) {
                HistoryItemKind.Visit ->
                    listOf(
                        HistoryEditAction.ChangePlace,
                        HistoryEditAction.ChangeTime,
                        HistoryEditAction.AddMemory,
                        HistoryEditAction.LinkMemory,
                    )
                HistoryItemKind.Journey ->
                    listOf(
                        HistoryEditAction.ChangeActivity,
                        HistoryEditAction.ChangeTime,
                    )
                HistoryItemKind.Gap -> listOf(HistoryEditAction.AddVisit)
            }
        edits.forEach { action ->
            TextButton(onClick = { actions.onEdit(item.id, action) }) { Text(stringResource(action.label())) }
        }
        if (item.kind == HistoryItemKind.Visit) {
            TextButton(onClick = onRequestDelete) {
                Text(stringResource(Res.string.history_delete), color = MaterialTheme.colorScheme.error)
            }
        }
        TextButton(onClick = actions.onCloseDetail) { Text(stringResource(Res.string.history_close)) }
    }
}

private fun HistoryEditAction.label() =
    when (this) {
        HistoryEditAction.ChangePlace -> Res.string.history_change_place
        HistoryEditAction.ChangeActivity -> Res.string.history_change_activity
        HistoryEditAction.ChangeTime -> Res.string.history_change_time
        HistoryEditAction.AddVisit -> Res.string.history_add_visit
        HistoryEditAction.AddMemory -> Res.string.history_add_memory
        HistoryEditAction.LinkMemory -> Res.string.history_link_memory
        HistoryEditAction.Delete -> Res.string.history_delete
    }
