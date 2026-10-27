@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.editor.ui.content

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.logdate.client.datastore.EntryStartMode
import app.logdate.feature.editor.ui.editor.EntryEditorViewModel
import app.logdate.ui.common.AlertDialog
import logdate.client.feature.editor.generated.resources.Res
import logdate.client.feature.editor.generated.resources.entry_start_default_description
import logdate.client.feature.editor.generated.resources.entry_start_default_mode
import logdate.client.feature.editor.generated.resources.entry_start_preference_failed
import logdate.client.ui.generated.resources.common_done
import org.jetbrains.compose.resources.stringResource
import logdate.client.ui.generated.resources.Res as UiRes

internal data class EntryStartPreferences(
    val mode: EntryStartMode,
    val onShow: (() -> Unit)?,
)

@Composable
internal fun rememberEntryStartPreferences(
    viewModel: EntryEditorViewModel,
    enabled: Boolean,
    handwritingEnabled: Boolean,
): EntryStartPreferences {
    val mode by viewModel.defaultEntryMode.collectAsState()
    var show by remember { mutableStateOf(false) }
    if (enabled && show) {
        val writeFailed by viewModel.defaultEntryModeWriteFailed.collectAsState()
        EntryStartModeDialog(
            mode,
            handwritingEnabled,
            viewModel::setDefaultEntryMode,
            writeFailed = writeFailed,
        ) { show = false }
    }
    return EntryStartPreferences(mode, if (enabled) ({ show = true }) else null)
}

@Composable
internal fun EntryStartModeDialog(
    mode: EntryStartMode,
    handwritingEnabled: Boolean,
    onSelect: (EntryStartMode) -> Unit,
    writeFailed: Boolean = false,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.entry_start_default_mode)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(stringResource(Res.string.entry_start_default_description))
                if (writeFailed) {
                    Text(stringResource(Res.string.entry_start_preference_failed), color = MaterialTheme.colorScheme.error)
                }
                Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                    EntryStartModeChoices(mode, handwritingEnabled, onSelect)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(UiRes.string.common_done)) } },
    )
}
