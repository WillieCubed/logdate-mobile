@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.editor.ui.handwriting

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
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
import app.logdate.shared.model.Journal
import app.logdate.ui.adaptive.FoldableBookLayout
import app.logdate.ui.common.DropdownMenu
import logdate.client.feature.editor.generated.resources.Res
import logdate.client.feature.editor.generated.resources.entry_start_default_mode
import logdate.client.feature.editor.generated.resources.saving
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
    var journalOpen by remember { mutableStateOf(false) }
    var optionsOpen by remember { mutableStateOf(false) }
    val focused = block != null
    val savingDescription = stringResource(Res.string.saving)
    val largeText = LocalDensity.current.fontScale >= 1.5f
    val header: @Composable () -> Unit = {
        Row(
            Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = 12.dp)
                .then(if (recording || canvasOnly) Modifier else Modifier.captureModeSwipes(captureModeForBlock(block), onCaptureMode))
                .testTag(if (focused) "entry_mode_focus" else "entry_mode_overview")
                .semantics { paneTitle = if (focused) "Block focus" else "Entry" },
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
                    modifier = Modifier.semantics { contentDescription = "Return to entry" },
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
                        if (!largeText) Text("Focus", style = MaterialTheme.typography.labelSmall, color = StoryMuted)
                    }
                }
            } else {
                FilledTonalIconButton(
                    onClick = onExit,
                    colors =
                        IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = StoryChrome,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        ),
                ) { Icon(Icons.Outlined.Close, "Close") }
                Box(Modifier.weight(1f).height(48.dp)) {
                    Column(
                        (
                            if (floating) {
                                Modifier
                                    .align(
                                        Alignment.Center,
                                    ).background(StoryChrome, RoundedCornerShape(24.dp))
                                    .padding(horizontal = 12.dp)
                            } else {
                                Modifier.fillMaxWidth()
                            }
                        ).height(48.dp).clickable { journalOpen = true },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        if (!largeText && !floating) {
                            Text(
                                "Entry",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                if (journalSelected) journal?.title.orEmpty() else "Choose journal",
                                style = MaterialTheme.typography.labelMedium,
                                color = StoryMuted,
                            )
                            Icon(Icons.Outlined.ExpandMore, null, Modifier.size(16.dp), tint = StoryMuted)
                        }
                    }
                    DropdownMenu(journalOpen, onDismissRequest = { journalOpen = false }) {
                        availableJournals.forEach { item ->
                            DropdownMenuItem(
                                text = { Text(item.title) },
                                trailingIcon = { if (journalSelected && item.id == journal?.id) Icon(Icons.Outlined.Check, null) },
                                onClick = {
                                    onSelectJournal?.invoke(item) ?: onJournalSelection()
                                    journalOpen = false
                                },
                            )
                        }
                    }
                }
                Button(
                    onClick = onSave,
                    enabled = saveEnabled && !saveInProgress,
                    modifier =
                        Modifier.height(40.dp).semantics {
                            contentDescription = "Save entry"
                            if (saveInProgress) stateDescription = savingDescription
                            liveRegion = LiveRegionMode.Polite
                        },
                    contentPadding = PaddingValues(horizontal = 12.dp),
                ) {
                    if (saveInProgress) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else if (largeText) {
                        Icon(Icons.Outlined.Check, null, Modifier.size(20.dp))
                    } else {
                        Text("Save", maxLines = 1)
                    }
                }
                if (onShowDrafts != null) {
                    Box {
                        FilledTonalIconButton(
                            onClick = { optionsOpen = true },
                            modifier = Modifier.size(40.dp),
                        ) { Icon(Icons.Outlined.MoreVert, "Entry options") }
                        DropdownMenu(optionsOpen, { optionsOpen = false }) {
                            onChooseDefaultMode?.let { chooseDefault ->
                                DropdownMenuItem(
                                    text = { Text(stringResource(Res.string.entry_start_default_mode)) },
                                    onClick = {
                                        optionsOpen = false
                                        chooseDefault()
                                    },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Drafts ($draftCount)") },
                                enabled = draftCount > 0,
                                modifier = Modifier.testTag("entry_open_drafts"),
                                onClick = {
                                    optionsOpen = false
                                    onShowDrafts()
                                },
                            )
                        }
                    }
                }
            }
        }
    }
    FoldableBookLayout(
        modifier = Modifier.fillMaxWidth().height(64.dp),
        minPaneWidth = 320.dp,
        startPane = { header() },
        endPane = {},
        standardContent = { header() },
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
