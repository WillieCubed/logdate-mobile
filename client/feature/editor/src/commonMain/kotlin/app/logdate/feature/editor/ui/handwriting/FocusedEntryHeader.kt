@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.editor.ui.handwriting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.logdate.feature.editor.ui.LocalEditorInputFocusBookmarks
import app.logdate.feature.editor.ui.blocks.memoryBlockLabel
import app.logdate.feature.editor.ui.editor.AudioBlockUiState
import app.logdate.feature.editor.ui.editor.AutoSaveStatus
import app.logdate.feature.editor.ui.editor.EntryBlockUiState
import app.logdate.feature.editor.ui.editor.TextBlockUiState
import app.logdate.feature.editor.ui.layout.LocalEntryChromeHeight
import logdate.client.feature.editor.generated.resources.Res
import logdate.client.feature.editor.generated.resources.entry_page_block_focus
import logdate.client.feature.editor.generated.resources.entry_page_draft_error
import logdate.client.feature.editor.generated.resources.entry_page_draft_saved
import logdate.client.feature.editor.generated.resources.entry_page_draft_saving
import logdate.client.feature.editor.generated.resources.entry_page_editing_memory
import logdate.client.feature.editor.generated.resources.entry_page_return_to_entry
import logdate.client.feature.editor.generated.resources.save_entry
import logdate.client.ui.generated.resources.common_done
import org.jetbrains.compose.resources.stringResource
import logdate.client.ui.generated.resources.Res as UiRes

@Composable
internal fun FocusedEntryHeader(
    block: EntryBlockUiState,
    recording: Boolean,
    onDone: () -> Unit,
    onSave: () -> Unit,
    saveEnabled: Boolean,
    saving: Boolean,
    draftStatus: AutoSaveStatus,
) {
    val focusBookmarks = LocalEditorInputFocusBookmarks.current
    val returnDescription = stringResource(Res.string.entry_page_return_to_entry)
    val saveDescription = stringResource(Res.string.save_entry)
    val focusTitle = stringResource(Res.string.entry_page_block_focus)
    val largeText = LocalDensity.current.fontScale >= 1.5f
    val showEntrySave = block is TextBlockUiState || block is AudioBlockUiState
    Row(
        Modifier
            .fillMaxWidth()
            .height(LocalEntryChromeHeight.current)
            .padding(horizontal = 12.dp)
            .testTag("entry_mode_focus")
            .semantics { paneTitle = focusTitle },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilledTonalButton(
            onClick = {
                focusBookmarks?.capture(block.id)
                onDone()
            },
            enabled = !recording && !saving,
            modifier = Modifier.semantics { contentDescription = returnDescription },
        ) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, null, Modifier.size(20.dp))
            Text(stringResource(UiRes.string.common_done), Modifier.padding(start = 4.dp), maxLines = 1)
        }
        Column(Modifier.weight(1f)) {
            Text(
                memoryBlockLabel(block),
                modifier = if (showEntrySave) Modifier else Modifier.widthIn(max = 112.dp),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!largeText) {
                Text(
                    stringResource(
                        when (draftStatus) {
                            AutoSaveStatus.IDLE -> Res.string.entry_page_editing_memory
                            AutoSaveStatus.SAVING -> Res.string.entry_page_draft_saving
                            AutoSaveStatus.SAVED -> Res.string.entry_page_draft_saved
                            AutoSaveStatus.ERROR -> Res.string.entry_page_draft_error
                        },
                    ),
                    Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (draftStatus == AutoSaveStatus.ERROR) MaterialTheme.colorScheme.error else StoryMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (showEntrySave) {
            Button(
                onClick = onSave,
                enabled = saveEnabled && !saving,
                modifier = Modifier.semantics { contentDescription = saveDescription },
            ) {
                when {
                    saving -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    largeText -> Icon(Icons.Outlined.Check, null, Modifier.size(20.dp))
                    else -> Text(saveDescription, maxLines = 1)
                }
            }
        }
    }
}
