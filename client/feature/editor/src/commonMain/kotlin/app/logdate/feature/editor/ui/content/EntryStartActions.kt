@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)
@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.editor.ui.content

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.logdate.client.datastore.EntryStartMode
import app.logdate.feature.editor.ui.editor.BlockType
import app.logdate.feature.editor.ui.handwriting.HandwritingCanvas
import app.logdate.feature.editor.ui.handwriting.HandwritingPage
import app.logdate.feature.editor.ui.handwriting.handwritingSharedElement
import app.logdate.ui.common.handwritingDelegator
import logdate.client.feature.editor.generated.resources.Res
import logdate.client.feature.editor.generated.resources.capture_photo_or_video
import logdate.client.feature.editor.generated.resources.choose_a_photo
import logdate.client.feature.editor.generated.resources.entry_start_handwriting_prompt
import logdate.client.feature.editor.generated.resources.record_audio
import logdate.client.feature.editor.generated.resources.write_something_placeholder
import org.jetbrains.compose.resources.stringResource
import kotlin.uuid.Uuid

@Composable
internal fun EntryStartActions(
    defaultMode: EntryStartMode,
    ids: Map<EntryStartMode, Uuid>,
    onCreate: (BlockType, Uuid) -> Unit,
    handwritingEnabled: Boolean,
    compact: Boolean,
) {
    val mode = if (!handwritingEnabled && defaultMode == EntryStartMode.HANDWRITING) EntryStartMode.TEXT else defaultMode
    val alternative = if (mode == EntryStartMode.TEXT) EntryStartMode.HANDWRITING.takeIf { handwritingEnabled } else EntryStartMode.TEXT
    val create: (EntryStartMode) -> Unit = { onCreate(it.blockType(), ids.getValue(it)) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(
            onClick = { create(mode) },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = if (compact) 120.dp else 200.dp)
                    .then(if (mode == EntryStartMode.HANDWRITING) Modifier else Modifier.startBlockBounds(ids.getValue(mode)))
                    .testTag(if (mode == EntryStartMode.TEXT) "entry_start_writing" else "entry_start_primary")
                    .then(if (mode == EntryStartMode.TEXT) Modifier.handwritingDelegator { create(mode) } else Modifier),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Box {
                if (mode == EntryStartMode.HANDWRITING) StartInkCanvas(ids.getValue(mode), Modifier.matchParentSize())
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 56.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (mode != EntryStartMode.TEXT && mode != EntryStartMode.HANDWRITING) Icon(mode.icon(), null, Modifier.size(24.dp))
                        Text(
                            stringResource(
                                when (mode) {
                                    EntryStartMode.TEXT -> Res.string.write_something_placeholder
                                    EntryStartMode.HANDWRITING -> Res.string.entry_start_handwriting_prompt
                                    EntryStartMode.CAMERA -> Res.string.capture_photo_or_video
                                    EntryStartMode.AUDIO -> Res.string.record_audio
                                    EntryStartMode.GALLERY -> Res.string.choose_a_photo
                                },
                            ),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (alternative != null) {
                        Box(
                            Modifier.align(Alignment.End),
                        ) { StartModeAction(alternative, ids.getValue(alternative)) { create(alternative) } }
                    }
                }
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            EntryStartMode.entries
                .filter { it != mode && it != alternative && (handwritingEnabled || it != EntryStartMode.HANDWRITING) }
                .forEach { secondary -> StartModeAction(secondary, ids.getValue(secondary)) { create(secondary) } }
        }
    }
}

@Composable
private fun StartModeAction(
    mode: EntryStartMode,
    id: Uuid,
    onClick: () -> Unit,
) {
    Box {
        if (mode == EntryStartMode.HANDWRITING) StartInkCanvas(id, Modifier.matchParentSize())
        FilledTonalButton(
            onClick,
            modifier =
                Modifier
                    .heightIn(min = 48.dp)
                    .then(if (mode == EntryStartMode.HANDWRITING) Modifier else Modifier.startBlockBounds(id))
                    .testTag(mode.actionTag()),
            contentPadding = PaddingValues(horizontal = 8.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(mode.icon(), null, Modifier.size(18.dp))
                Text(mode.label(), style = MaterialTheme.typography.labelLarge, maxLines = 1)
            }
        }
    }
}

@Composable
private fun StartInkCanvas(
    id: Uuid,
    modifier: Modifier,
) {
    HandwritingCanvas(
        HandwritingPage(),
        modifier.handwritingSharedElement(id, 0f),
        ruled = false,
        description = "",
        paperColor = Color.Transparent,
    )
}
