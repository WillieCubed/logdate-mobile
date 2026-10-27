@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.editor.ui.content

import androidx.compose.runtime.Composable
import app.logdate.client.datastore.EntryStartMode
import app.logdate.ui.common.MaterialContainer
import app.logdate.ui.common.SettingsChoiceItem

@Composable
internal fun EntryStartModeChoices(
    selected: EntryStartMode,
    handwritingEnabled: Boolean,
    onSelect: (EntryStartMode) -> Unit,
) {
    val effectiveSelection = if (!handwritingEnabled && selected == EntryStartMode.HANDWRITING) EntryStartMode.TEXT else selected
    MaterialContainer {
        EntryStartMode.entries.filter { handwritingEnabled || it != EntryStartMode.HANDWRITING }.forEach { mode ->
            SettingsChoiceItem(
                title = mode.label(),
                selected = effectiveSelection == mode,
                onClick = { onSelect(mode) },
            )
        }
    }
}
