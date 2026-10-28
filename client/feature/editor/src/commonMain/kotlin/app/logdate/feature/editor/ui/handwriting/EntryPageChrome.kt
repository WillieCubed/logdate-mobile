@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.editor.ui.handwriting

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import app.logdate.client.datastore.EntryStartMode
import app.logdate.feature.editor.ui.editor.EditorState
import app.logdate.feature.editor.ui.layout.EditorContentLayer
import app.logdate.feature.editor.ui.layout.LocalEditorContextControlsVisible
import app.logdate.feature.editor.ui.layout.LocalEditorFocusPresentation
import app.logdate.feature.editor.ui.layout.LocalEditorRecordingActive
import app.logdate.feature.editor.ui.layout.LocalEntryChromeHeight
import app.logdate.feature.editor.ui.layout.editorFocus
import app.logdate.feature.editor.ui.layout.editorFocusPresentation
import app.logdate.ui.foldable.FoldableSplitLayout
import app.logdate.ui.foldable.calculateFoldableSplitLayout
import app.logdate.ui.foldable.relativeTo
import app.logdate.ui.foldable.rememberFoldableLayoutInfo
import app.logdate.ui.foldable.rememberWindowOrigin

import logdate.client.feature.editor.generated.resources.Res
import logdate.client.feature.editor.generated.resources.entry_page_back_to_writing
import logdate.client.feature.editor.generated.resources.entry_page_return_to_writing
import org.jetbrains.compose.resources.stringResource

internal val LocalEntryCaptureBarHeight = compositionLocalOf { 0.dp }

/** The capture selector is one persistent composition whose position follows the scrolling page. */
@Composable
internal fun EntryPageChrome(
    editor: EditorState,
    overview: EntryPageOverviewState,
    defaultMode: EntryStartMode,
    onReturn: () -> Unit,
    onExit: () -> Unit,
    onCapture: (StoryCaptureMode) -> Unit,
    modifier: Modifier = Modifier,
    header: (@Composable () -> Unit)? = null,
    focusProgress: Float? = null,
    content: @Composable () -> Unit,
) {
    val returnToWritingDescription = stringResource(Res.string.entry_page_return_to_writing)
    val density = LocalDensity.current
    val keyboardBottom = WindowInsets.ime.getBottom(density)
    val safeBottom = WindowInsets.safeDrawing.getBottom(density)
    val focused = editor.expandedBlockId != null
    val block = editor.blocks.firstOrNull { it.id == editor.expandedBlockId }
    val journal = editor.availableJournals.firstOrNull()
    val defaultCapture =
        when (defaultMode) {
            EntryStartMode.TEXT -> StoryCaptureMode.Write
            EntryStartMode.HANDWRITING -> StoryCaptureMode.Handwrite
            EntryStartMode.CAMERA -> StoryCaptureMode.Camera
            EntryStartMode.AUDIO -> StoryCaptureMode.Voice
            EntryStartMode.GALLERY -> StoryCaptureMode.Photos
        }
    val (origin, trackOrigin) = rememberWindowOrigin()
    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceDim)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
            .then(trackOrigin)
            .testTag("stories_editor")
            .semantics { isTraversalGroup = true },
    ) {
        val split =
            calculateFoldableSplitLayout(maxWidth, maxHeight, rememberFoldableLayoutInfo().relativeTo(origin), minPaneWidth = 280.dp)
        val safeTop = WindowInsets.safeDrawing.getTop(density)
        val safeHeight = maxHeight - with(density) { (safeBottom + safeTop).toDp() }
        val chromeHeight = if (safeHeight < 240.dp) 48.dp else 64.dp
        val anchor = overview.captureBounds
        val focusLeft = if (split is FoldableSplitLayout.Vertical) split.rightPane.left else (maxWidth - minOf(maxWidth, 640.dp)) / 2
        val focusWidth = if (split is FoldableSplitLayout.Vertical) split.rightPane.width else minOf(maxWidth, 640.dp)
        val focusTop = maxHeight - chromeHeight - with(density) { maxOf(keyboardBottom, safeBottom).toDp() }
        val progress = focusProgress ?: if (focused) 1f else 0f
        val overviewLeft = with(density) { (anchor?.left?.toDp() ?: origin.x) - origin.x }
        val overviewWidth = with(density) { (anchor?.width ?: constraints.maxWidth.toFloat()).toDp() }
        val overviewTop = with(density) { anchor?.top?.toDp()?.minus(origin.y) ?: maxHeight }
        val barLeft = lerp(overviewLeft, focusLeft, progress)
        val barWidth = lerp(overviewWidth, focusWidth, progress)
        val writingTop = lerp(overviewTop, focusTop, progress)
        val barTop = lerp(writingTop, maxHeight + 80.dp, overview.browsingProgress)
        val barAlpha = 1f - overview.browsingProgress
        val pullProgress = (-overview.pull / with(density) { 112.dp.toPx() }).coerceIn(0f, 1f)
        CompositionLocalProvider(LocalEntryChromeHeight provides chromeHeight) {
            CompositionLocalProvider(
                LocalEditorFocusPresentation provides
                    editorFocusPresentation(editor.editorFocus(), maxWidth, split != FoldableSplitLayout.None, keyboardBottom > 0),
                LocalEditorContextControlsVisible provides false,
                LocalEditorRecordingActive provides editor.isRecordingAudio,
                LocalEntryCaptureBarHeight provides if (editor.isRecordingAudio) 0.dp else chromeHeight,
            ) {
                EditorContentLayer(
                    Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical))
                        .clipToBounds()
                        .then(
                            if (editor.isRecordingAudio) {
                                Modifier
                                    .windowInsetsPadding(
                                        WindowInsets.safeDrawing.only(WindowInsetsSides.Top),
                                    ).padding(top = chromeHeight)
                            } else {
                                Modifier
                            },
                        ).semantics {
                            isTraversalGroup = true
                            traversalIndex = 1f
                        },
                ) { content() }
            }
            Box(
                Modifier.align(Alignment.TopStart).windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top)).semantics {
                    isTraversalGroup = true
                    traversalIndex = 0f
                },
            ) {
                if (!overview.earlierExpanded) {
                    Box(Modifier.graphicsLayer { alpha = 1f - overview.browsingProgress }) {
                        if (header != null) {
                            header()
                        } else {
                            EntryFocusPrototypeHeader(
                                block,
                                canvasOnly = true,
                                recording = editor.isRecordingAudio,
                                onReturnToEntry = onReturn,
                                onExit = onExit,
                                journal = journal,
                                journalSelected = true,
                                onJournalSelection = {},
                                onCaptureMode = onCapture,
                                floating = true,
                            )
                        }
                    }
                }
                if (overview.browsingProgress > 0f || overview.earlierExpanded) {
                    FilledTonalButton(
                        onClick = { overview.earlierExpanded = false },
                        enabled = overview.earlierExpanded,
                        modifier =
                            Modifier
                                .padding(16.dp)
                                .graphicsLayer { alpha = overview.browsingProgress }
                                .semantics { contentDescription = returnToWritingDescription },
                        colors =
                            ButtonDefaults.filledTonalButtonColors(
                                containerColor = StoryChrome,
                                contentColor = MaterialTheme.colorScheme.onSurface,
                            ),
                    ) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, null)
                        Text(stringResource(Res.string.entry_page_back_to_writing), Modifier.padding(start = 8.dp))
                    }
                }
            }
            if (!editor.isRecordingAudio) {
                Row(
                    Modifier
                        .align(AbsoluteAlignment.TopLeft)
                        .absoluteOffset(barLeft, barTop)
                        .width(barWidth)
                        .height(chromeHeight)
                        .padding(horizontal = 12.dp)
                        .captureBarPullGestures(overview, enabled = !focused && !overview.earlierExpanded)
                        .then(
                            if (!editor.isEditingLocked && !editor.isSaving && !editor.shouldExit && !editor.isRecordingAudio) {
                                Modifier.captureModeSwipes(captureModeForBlock(block) ?: defaultCapture, onCapture)
                            } else {
                                Modifier
                            },
                        ).graphicsLayer {
                            alpha = barAlpha
                            scaleX = 1f + pullProgress * 0.025f
                            scaleY = 1f + pullProgress * 0.025f
                        }.testTag("entry_capture_tools")
                        .semantics {
                            isTraversalGroup = true
                            traversalIndex = 2f
                            progressBarRangeInfo = ProgressBarRangeInfo(pullProgress, 0f..1f)
                        },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StoriesCaptureModes(
                        captureModeForBlock(block) ?: defaultCapture,
                        onCapture,
                        compact = true,
                        horizontalInset = 0.dp,
                        enabled =
                            !editor.isEditingLocked && !editor.isSaving && !editor.shouldExit && !editor.isRecordingAudio,
                    )
                }
            }
        }
    }
}
