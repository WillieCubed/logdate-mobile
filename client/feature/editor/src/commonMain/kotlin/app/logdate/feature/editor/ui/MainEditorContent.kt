@file:OptIn(ExperimentalSharedTransitionApi::class, ExperimentalAnimationApi::class)

package app.logdate.feature.editor.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.SeekableTransitionState
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.logdate.client.datastore.EntryStartMode
import app.logdate.feature.editor.ui.audio.AudioBlockEditor
import app.logdate.feature.editor.ui.blocks.EntryContextPreview
import app.logdate.feature.editor.ui.blocks.EntryMemorySequence
import app.logdate.feature.editor.ui.camera.CameraBlockEditor
import app.logdate.feature.editor.ui.camera.CapturedMediaType
import app.logdate.feature.editor.ui.content.EditorStartContextLayout
import app.logdate.feature.editor.ui.content.EmptyEditorStateContent
import app.logdate.feature.editor.ui.content.EntryStartContent
import app.logdate.feature.editor.ui.content.matchingPickerTileIdsFor
import app.logdate.feature.editor.ui.editor.AudioBlockUiState
import app.logdate.feature.editor.ui.editor.BlockType
import app.logdate.feature.editor.ui.editor.CameraBlockUiState
import app.logdate.feature.editor.ui.editor.EntryBlockUiState
import app.logdate.feature.editor.ui.editor.HandwritingBlockUiState
import app.logdate.feature.editor.ui.editor.ImageBlockUiState
import app.logdate.feature.editor.ui.editor.TextBlockUiState
import app.logdate.feature.editor.ui.editor.VideoBlockUiState
import app.logdate.feature.editor.ui.editor.delegate.PendingAudioResolver
import app.logdate.feature.editor.ui.handwriting.HandwritingCanvas
import app.logdate.feature.editor.ui.image.ImageBlockPreview
import app.logdate.feature.editor.ui.layout.EditorBlockCornerRadius
import app.logdate.feature.editor.ui.layout.EditorFocus
import app.logdate.feature.editor.ui.layout.FocusedBlockLayout
import app.logdate.feature.editor.ui.layout.LocalEntryChromeHeight
import app.logdate.feature.editor.ui.layout.editorFocus
import app.logdate.feature.editor.ui.layout.entryContentPadding
import app.logdate.feature.editor.ui.state.BlocksUiState
import app.logdate.feature.editor.ui.text.TextBlockContent
import app.logdate.feature.editor.ui.video.VideoBlockEditor
import app.logdate.ui.foldable.rememberFoldableLayoutInfo
import app.logdate.ui.theme.Spacing
import logdate.client.feature.editor.generated.resources.Res
import logdate.client.feature.editor.generated.resources.ink_preview_description
import org.jetbrains.compose.resources.stringResource
import kotlin.uuid.Uuid

/**
 * The main rendering container for the entry editor.
 *
 * Three display states — empty, expanded block, scrollable list — are driven by a
 * [SeekableTransitionState] so that the predictive back gesture can scrub the
 * shared-bounds morph in real time before committing or cancelling.
 *
 * @param onDismissExpanded Called when the expanded block should be dismissed (back committed)
 */
@Suppress("ktlint:standard:function-naming")
@Composable
fun MainEditorContent(
    uiState: BlocksUiState,
    shouldReturnToPickerOnBack: Boolean,
    onDismissExpanded: () -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    onBackProgress: (Float) -> Unit = {},
    onBackCommit: () -> Unit = {},
    onBackCancel: () -> Unit = {},
    onBlockFocusTransitionProgress: (Float) -> Unit = {},
    onBlockFocusTargetChanged: (Boolean) -> Unit = {},
    onAudioResolverReady: (Uuid, PendingAudioResolver) -> Unit = { _, _ -> },
    focusState: EditorFocus = editorFocus(uiState.blocks, uiState.expandedBlockId),
    entryContext: (@Composable () -> Unit)? = null,
    expandAnySelectedBlock: Boolean = false,
    todayNotes: List<app.logdate.client.repository.journals.JournalNote> = emptyList(),
    onOpenEntry: (Uuid) -> Unit = {},
    defaultStartMode: EntryStartMode = EntryStartMode.TEXT,
    showBlockFocusActions: Boolean = expandAnySelectedBlock,
    overviewContent: (@Composable (BlocksUiState) -> Unit)? = null,
    floatingChrome: Boolean = false,
) {
    var lastContent by remember { mutableStateOf(uiState) }
    SideEffect { if (uiState.blocks.isNotEmpty()) lastContent = uiState }
    val renderedContent = if (uiState.blocks.isEmpty()) lastContent else uiState
    val scope = rememberCoroutineScope()

    val expandedBlock =
        remember(uiState.expandedBlockId, uiState.blocks) {
            uiState.blocks.find { it.id == uiState.expandedBlockId }
        }

    val displayState: EditorDisplay =
        when {
            uiState.blocks.isEmpty() -> EditorDisplay.Empty
            expandAnySelectedBlock && expandedBlock != null -> EditorDisplay.Expanded(expandedBlock)
            focusState is EditorFocus.Drawing &&
                expandedBlock != null &&
                uiState.blockPresentations[expandedBlock.id]?.immersive == true -> EditorDisplay.Expanded(expandedBlock)
            focusState is EditorFocus.Camera && expandedBlock is CameraBlockUiState && expandedBlock.uri == null ->
                EditorDisplay.Expanded(
                    expandedBlock,
                )
            else -> EditorDisplay.List
        }

    // SeekableTransitionState lets the predictive back gesture scrub the transition
    val transitionState = remember { SeekableTransitionState(displayState) }

    // Capture completion changes the display mode without changing the block ID.
    LaunchedEffect(displayState) {
        if (transitionState.targetState != displayState) {
            transitionState.animateTo(displayState)
        }
    }

    val backTarget: EditorDisplay =
        if (shouldReturnToPickerOnBack) {
            EditorDisplay.Empty
        } else {
            EditorDisplay.List
        }
    val pickerTileIds =
        remember(expandedBlock, shouldReturnToPickerOnBack, uiState.blocks.isEmpty(), lastContent) {
            if (shouldReturnToPickerOnBack || uiState.blocks.isEmpty()) {
                matchingPickerTileIdsFor(expandedBlock ?: renderedContent.blocks.singleOrNull())
            } else {
                matchingPickerTileIdsFor(null)
            }
        }

    val focusMotion = remember { Animatable(if (displayState is EditorDisplay.Expanded) 1f else 0f) }
    val back =
        rememberEditorDisplayBack(
            transitionState,
            displayState,
            backTarget,
            onDismissExpanded,
            { focusMotion.value },
            onBackProgress,
            onBackCommit,
            onBackCancel,
        )

    val transition = rememberTransition(transitionState, label = "editorDisplay")
    val seekProgress by back.progress.collectAsState()
    val focusTarget = transition.targetState is EditorDisplay.Expanded
    LaunchedEffect(focusTarget, seekProgress) {
        val gesture = seekProgress
        if (gesture != null) {
            focusMotion.snapTo(back.surfaceProgressAtStart * (1f - gesture))
        } else {
            focusMotion.animateTo(if (focusTarget) 1f else 0f, blockFocusSpring())
        }
    }
    val focusProgress = focusMotion.value
    SideEffect {
        onBlockFocusTransitionProgress(focusProgress)
        onBlockFocusTargetChanged(focusTarget)
    }

    EditorSharedTransitionContainer(modifier = modifier.fillMaxSize().semantics { this[BlockFocusProgressKey] = focusProgress }) {
        val sts = this
        CompositionLocalProvider(
            LocalSharedTransitionScope provides sts,
            LocalBlockFocusProgress provides focusProgress,
            LocalBlockFocusId provides
                (
                    (transition.targetState as? EditorDisplay.Expanded)?.block?.id
                        ?: (transition.currentState as? EditorDisplay.Expanded)?.block?.id
                ),
        ) {
            transition.AnimatedContent(
                contentKey = { it::class },
                transitionSpec = {
                    EnterTransition.None togetherWith fadeOut(tween(220))
                },
            ) { target ->
                val avs = this
                CompositionLocalProvider(LocalAnimatedVisibilityScope provides avs) {
                    when (target) {
                        EditorDisplay.Empty -> {
                            if (overviewContent !=
                                null
                            ) {
                                overviewContent(uiState)
                            } else {
                                EditorStartContextLayout(entryContext) { pickerModifier ->
                                    if (expandAnySelectedBlock) {
                                        EntryStartContent(
                                            notes = todayNotes,
                                            onCreateBlock = { type, id -> uiState.onCreateBlock(type, id) },
                                            onOpenEntry = onOpenEntry,
                                            tileIds = pickerTileIds,
                                            handwritingEnabled = uiState.allowHandwritingCreation,
                                            defaultMode = defaultStartMode,
                                            modifier = pickerModifier,
                                        )
                                    } else {
                                        EmptyEditorStateContent(
                                            onStartTextBlock = { id -> uiState.onCreateBlock(BlockType.TEXT, id) },
                                            onStartPhotoBlock = { id -> uiState.onCreateBlock(BlockType.IMAGE, id) },
                                            onStartAudioBlock = { id -> uiState.onCreateBlock(BlockType.AUDIO, id) },
                                            onStartCameraBlock = { id -> uiState.onCreateBlock(BlockType.CAMERA, id) },
                                            onStartHandwritingBlock =
                                                if (uiState.allowHandwritingCreation) {
                                                    { id -> uiState.onCreateBlock(BlockType.HANDWRITING, id) }
                                                } else {
                                                    null
                                                },
                                            retainConsumedTileIds = true,
                                            textTileId = pickerTileIds.textId,
                                            photoTileId = pickerTileIds.photoId,
                                            audioTileId = pickerTileIds.audioId,
                                            cameraTileId = pickerTileIds.cameraId,
                                            modifier = pickerModifier.padding(start = Spacing.sm, top = Spacing.sm, end = Spacing.sm),
                                        )
                                    }
                                }
                            }
                        }

                        is EditorDisplay.Expanded -> {
                            // Read the live block from uiState rather than the
                            // transition's target snapshot so that edits (typing)
                            // are reflected immediately without waiting for the
                            // SeekableTransitionState to update.
                            val liveBlock =
                                uiState.blocks.find { it.id == target.block.id }
                                    ?: target.block
                            with(sts) {
                                Box(
                                    modifier =
                                        Modifier
                                            .fillMaxSize()
                                            .testTag("focused_block_${liveBlock.id}")
                                            .then(
                                                if (liveBlock.id in uiState.blockPresentations) {
                                                    Modifier
                                                } else if (floatingChrome) {
                                                    Modifier.editorBlockFocusElement(
                                                        sts,
                                                        avs,
                                                        liveBlock.id,
                                                        EditorBlockCornerRadius,
                                                        focusProgress,
                                                    )
                                                } else {
                                                    Modifier.sharedBounds(
                                                        rememberSharedContentState("block_surface_${liveBlock.id}"),
                                                        animatedVisibilityScope = avs,
                                                    )
                                                },
                                            ).then(
                                                if ((floatingChrome && liveBlock.id !in uiState.blockPresentations) ||
                                                    liveBlock is TextBlockUiState
                                                ) {
                                                    Modifier
                                                        .clip(
                                                            RoundedCornerShape(
                                                                if (floatingChrome) {
                                                                    EditorBlockCornerRadius *
                                                                        (1f - focusProgress)
                                                                } else {
                                                                    0.dp
                                                                },
                                                            ),
                                                        ).background(MaterialTheme.colorScheme.surfaceContainerLow)
                                                } else {
                                                    Modifier
                                                },
                                            ),
                                ) {
                                    val editBlock: @Composable () -> Unit = {
                                        val custom = uiState.blockPresentations[liveBlock.id]
                                        if (custom != null && floatingChrome) {
                                            FocusedBlockLayout(
                                                textEditor = true,
                                                contentPadding = 0.dp,
                                                preview =
                                                    if (rememberFoldableLayoutInfo().hinge?.isSeparating == true) {
                                                        (
                                                            {
                                                                EntryContextPreview(
                                                                    uiState.blocks.filter { it.id != liveBlock.id },
                                                                    Modifier.padding(top = 80.dp).graphicsLayer {
                                                                        alpha =
                                                                            focusProgress
                                                                    },
                                                                )
                                                            }
                                                        )
                                                    } else {
                                                        null
                                                    },
                                                editor = custom.focused,
                                            )
                                        } else if (custom != null) {
                                            custom.focused()
                                        } else {
                                            BlockContentInner(
                                                block = liveBlock,
                                                isExpanded = true,
                                                onBlockFocused = uiState.onBlockFocused,
                                                onBlockUpdated = uiState.onUpdateBlock,
                                                onBlockDeleted = uiState.onDeleteBlock,
                                                onAudioResolverReady = onAudioResolverReady,
                                                onPersistAudioRecordingTarget = uiState.onPersistAudioRecordingTarget,
                                                onPersistCameraCaptureTarget = uiState.onPersistCameraCaptureTarget,
                                                modifier =
                                                    Modifier.fillMaxSize().then(
                                                        if (floatingChrome &&
                                                            !(liveBlock is CameraBlockUiState && liveBlock.uri == null)
                                                        ) {
                                                            Modifier
                                                                .entryContentPadding(
                                                                    top =
                                                                        if (liveBlock is TextBlockUiState ||
                                                                            liveBlock is AudioBlockUiState
                                                                        ) {
                                                                            LocalEntryChromeHeight.current
                                                                        } else {
                                                                            0.dp
                                                                        },
                                                                    bottom =
                                                                        if (liveBlock is TextBlockUiState) {
                                                                            LocalEntryChromeHeight.current +
                                                                                Spacing.lg
                                                                        } else {
                                                                            0.dp
                                                                        },
                                                                )
                                                        } else {
                                                            Modifier
                                                        },
                                                    ),
                                            )
                                        }
                                    }
                                    if (liveBlock is TextBlockUiState && liveBlock.id !in uiState.blockPresentations) {
                                        val photo = uiState.blocks.filterIsInstance<ImageBlockUiState>().firstOrNull { it.uri != null }
                                        val showEntryContext = floatingChrome && rememberFoldableLayoutInfo().hinge?.isSeparating == true
                                        FocusedBlockLayout(
                                            textEditor = true,
                                            contentPadding = if (floatingChrome) 0.dp else 16.dp,
                                            preview =
                                                if (photo == null) {
                                                    if (showEntryContext) {
                                                        {
                                                            EntryContextPreview(
                                                                uiState.blocks.filter { it.id != liveBlock.id },
                                                                Modifier.padding(top = 80.dp),
                                                            )
                                                        }
                                                    } else {
                                                        null
                                                    }
                                                } else {
                                                    { ImageBlockPreview(photo, modifier = Modifier.fillMaxSize()) }
                                                },
                                            editor = editBlock,
                                        )
                                    } else {
                                        editBlock()
                                    }
                                }
                            }
                        }

                        EditorDisplay.List -> {
                            if (overviewContent != null) {
                                overviewContent(renderedContent)
                            } else {
                                EntryMemorySequence(
                                    uiState = renderedContent,
                                    entryContext = entryContext,
                                    autoFocusInitialText = !expandAnySelectedBlock,
                                    showFocusActions = showBlockFocusActions,
                                    listState = listState,
                                    onAudioResolverReady = onAudioResolverReady,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Suppress("ktlint:standard:function-naming")
@Composable
private fun BlockContentInner(
    block: EntryBlockUiState,
    isExpanded: Boolean,
    onBlockFocused: (Uuid) -> Unit,
    onBlockUpdated: (EntryBlockUiState) -> Unit,
    onBlockDeleted: (Uuid) -> Unit,
    onAudioResolverReady: (Uuid, PendingAudioResolver) -> Unit = { _, _ -> },
    onPersistAudioRecordingTarget: suspend (Uuid, String, String?) -> Boolean = { _, _, _ -> true },
    onPersistCameraCaptureTarget: suspend (Uuid, String, CapturedMediaType) -> Boolean = { _, _, _ -> false },
    modifier: Modifier = Modifier,
) {
    when (block) {
        is HandwritingBlockUiState ->
            HandwritingCanvas(
                block.page,
                Modifier.fillMaxWidth().height(240.dp),
                description = stringResource(Res.string.ink_preview_description),
            )
        is TextBlockUiState ->
            TextBlockContent(
                block = block,
                isExpanded = isExpanded,
                onTextChanged = { newText -> onBlockUpdated(block.copy(content = newText)) },
                onFocused = { onBlockFocused(block.id) },
                modifier = modifier,
            )

        is ImageBlockUiState ->
            app.logdate.feature.editor.ui.image.ImageBlockEditor(
                block = block,
                onBlockUpdated = onBlockUpdated,
                onDeleteRequested = { onBlockDeleted(block.id) },
                isExpanded = isExpanded,
                modifier = modifier,
            )

        is AudioBlockUiState ->
            AudioBlockEditor(
                block = block,
                onBlockUpdated = onBlockUpdated,
                onDeleteRequested = { onBlockDeleted(block.id) },
                onResolverReady = onAudioResolverReady,
                onPersistRecordingTarget = onPersistAudioRecordingTarget,
                modifier = modifier,
            )

        is CameraBlockUiState ->
            CameraBlockEditor(
                block = block,
                onBlockUpdated = onBlockUpdated,
                onDeleteRequested = { onBlockDeleted(block.id) },
                onPersistCaptureTarget = onPersistCameraCaptureTarget,
                modifier = modifier,
            )

        is VideoBlockUiState ->
            VideoBlockEditor(
                block = block,
                onBlockUpdated = onBlockUpdated,
                onDeleteRequested = { onBlockDeleted(block.id) },
                modifier = modifier,
            )
    }
}

@Suppress("ktlint:standard:function-naming")
@Composable
private fun EditorSharedTransitionContainer(
    modifier: Modifier,
    content: @Composable SharedTransitionScope.() -> Unit,
) {
    val providedScope = LocalSharedTransitionScope.current
    if (providedScope == null) {
        SharedTransitionLayout(modifier, content = content)
    } else {
        Box(modifier) { content(providedScope) }
    }
}
