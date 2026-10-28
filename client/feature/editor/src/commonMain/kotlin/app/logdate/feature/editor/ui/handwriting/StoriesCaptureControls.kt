@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.editor.ui.handwriting

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.logdate.feature.editor.ui.blocks.memoryBlockLabel
import app.logdate.feature.editor.ui.editor.AudioBlockUiState
import app.logdate.feature.editor.ui.editor.BlockType
import app.logdate.feature.editor.ui.editor.CameraBlockUiState
import app.logdate.feature.editor.ui.editor.EntryBlockUiState
import app.logdate.feature.editor.ui.editor.HandwritingBlockUiState
import app.logdate.feature.editor.ui.editor.ImageBlockUiState
import app.logdate.feature.editor.ui.editor.TextBlockUiState
import app.logdate.feature.editor.ui.editor.VideoBlockUiState
import app.logdate.feature.editor.ui.layout.LocalEntryChromeHeight
import app.logdate.shared.model.Journal
import app.logdate.ui.adaptive.FoldableBookLayout
import logdate.client.feature.editor.generated.resources.Res
import logdate.client.feature.editor.generated.resources.entry_page_block_focus
import logdate.client.feature.editor.generated.resources.entry_page_entry
import logdate.client.feature.editor.generated.resources.entry_page_focus
import logdate.client.feature.editor.generated.resources.entry_page_return_to_entry
import org.jetbrains.compose.resources.stringResource
import kotlin.math.abs

internal enum class StoryCaptureMode { Write, Handwrite, Camera, Voice, Photos }

internal val StoryChrome: Color
    @Composable get() = MaterialTheme.colorScheme.surfaceContainerHigh
internal val StoryAccent: Color
    @Composable get() = MaterialTheme.colorScheme.primaryContainer
internal val StoryMuted: Color
    @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant

@Composable
internal fun EntryFocusPrototypeHeader(
    block: EntryBlockUiState?,
    canvasOnly: Boolean,
    recording: Boolean,
    onReturnToEntry: () -> Unit,
    onExit: () -> Unit,
    journal: Journal?,
    journalSelected: Boolean,
    onJournalSelection: () -> Unit,
    onCaptureMode: (StoryCaptureMode) -> Unit,
    onSave: () -> Unit = {},
    saveEnabled: Boolean = false,
    saveInProgress: Boolean = false,
    availableJournals: List<Journal> = listOfNotNull(journal),
    onSelectJournal: ((Journal) -> Unit)? = null,
    onShowDrafts: (() -> Unit)? = null,
    draftCount: Int = 0,
    onChooseDefaultMode: (() -> Unit)? = null,
    floating: Boolean = false,
) {
    val focused = block != null
    val blockFocusTitle = stringResource(Res.string.entry_page_block_focus)
    val entryTitle = stringResource(Res.string.entry_page_entry)
    val returnToEntry = stringResource(Res.string.entry_page_return_to_entry)
    val largeText = LocalDensity.current.fontScale >= 1.5f
    val header: @Composable () -> Unit = {
        Row(
            Modifier
                .fillMaxWidth()
                .height(LocalEntryChromeHeight.current)
                .padding(horizontal = 12.dp)
                .then(if (recording || canvasOnly) Modifier else Modifier.captureModeSwipes(captureModeForBlock(block), onCaptureMode))
                .testTag(if (focused) "entry_mode_focus" else "entry_mode_overview")
                .semantics { paneTitle = if (focused) blockFocusTitle else entryTitle },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (block != null) {
                FilledTonalIconButton(
                    onClick = onReturnToEntry,
                    enabled = !recording,
                    colors =
                        IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = StoryChrome,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        ),
                    modifier = Modifier.semantics { contentDescription = returnToEntry },
                ) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, null, Modifier.size(20.dp))
                }
                if (!canvasOnly) {
                    Column {
                        Text(
                            memoryBlockLabel(block),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (!largeText) {
                            Text(
                                stringResource(Res.string.entry_page_focus),
                                style = MaterialTheme.typography.labelSmall,
                                color = StoryMuted,
                            )
                        }
                    }
                }
            }
        }
    }
    val overviewHeader: @Composable () -> Unit = {
        EntryJournalHeader(
            journal,
            journalSelected,
            availableJournals,
            onSelectJournal,
            onJournalSelection,
            onExit,
            onSave,
            saveEnabled,
            saveInProgress,
            onShowDrafts,
            draftCount,
            onChooseDefaultMode,
            floating,
            modifier = if (recording || canvasOnly) Modifier else Modifier.captureModeSwipes(null, onCaptureMode),
        )
    }
    FoldableBookLayout(
        modifier = Modifier.fillMaxWidth().height(LocalEntryChromeHeight.current),
        minPaneWidth = 320.dp,
        startPane = { if (focused) header() else overviewHeader() },
        endPane = {},
        standardContent = { if (focused) header() else overviewHeader() },
    )
}

@Composable
internal fun EntryFocusPrototypeTools(
    mode: StoryCaptureMode?,
    onMode: (StoryCaptureMode) -> Unit,
) {
    Box(Modifier.fillMaxWidth().captureModeSwipes(mode, onMode).testTag("entry_capture_tools")) {
        StoriesCaptureModes(mode, onMode)
    }
}

internal fun captureModeForBlock(block: EntryBlockUiState?): StoryCaptureMode? =
    when (block) {
        is TextBlockUiState -> StoryCaptureMode.Write
        is HandwritingBlockUiState -> StoryCaptureMode.Handwrite
        is CameraBlockUiState -> StoryCaptureMode.Camera
        is AudioBlockUiState -> StoryCaptureMode.Voice
        is ImageBlockUiState, is VideoBlockUiState -> StoryCaptureMode.Photos
        null -> null
    }

internal fun StoryCaptureMode.blockType(): BlockType =
    when (this) {
        StoryCaptureMode.Write -> BlockType.TEXT
        StoryCaptureMode.Handwrite -> BlockType.HANDWRITING
        StoryCaptureMode.Camera -> BlockType.CAMERA
        StoryCaptureMode.Voice -> BlockType.AUDIO
        StoryCaptureMode.Photos -> BlockType.IMAGE
    }

@Composable
internal fun Modifier.captureModeSwipes(
    mode: StoryCaptureMode?,
    onMode: (StoryCaptureMode) -> Unit,
): Modifier {
    val latestOnMode by rememberUpdatedState(onMode)
    val direction = LocalLayoutDirection.current
    return pointerInput(mode, direction) {
        var distance = 0f
        detectHorizontalDragGestures(
            onDragStart = { distance = 0f },
            onHorizontalDrag = { _, amount -> distance += amount },
            onDragEnd = {
                if (abs(distance) > size.width * 0.18f) {
                    val forward = if (direction == LayoutDirection.Rtl) distance > 0 else distance < 0
                    val index = ((mode?.ordinal ?: 0) + if (forward) 1 else -1).coerceIn(StoryCaptureMode.entries.indices)
                    latestOnMode(StoryCaptureMode.entries[index])
                }
            },
        )
    }
}
