@file:OptIn(ExperimentalSharedTransitionApi::class, ExperimentalAnimationApi::class)

package app.logdate.feature.editor.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.SeekableTransitionState
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.logdate.feature.editor.ui.audio.AudioBlockEditor
import app.logdate.feature.editor.ui.blocks.EntryMemorySequence
import app.logdate.feature.editor.ui.camera.CameraBlockEditor
import app.logdate.feature.editor.ui.content.EmptyEditorStateContent
import app.logdate.feature.editor.ui.content.matchingPickerTileIdsFor
import app.logdate.feature.editor.ui.editor.AudioBlockUiState
import app.logdate.feature.editor.ui.editor.BlockType
import app.logdate.feature.editor.ui.editor.CameraBlockUiState
import app.logdate.feature.editor.ui.editor.EntryBlockUiState
import app.logdate.feature.editor.ui.editor.ImageBlockUiState
import app.logdate.feature.editor.ui.editor.TextBlockUiState
import app.logdate.feature.editor.ui.editor.VideoBlockUiState
import app.logdate.feature.editor.ui.editor.delegate.PendingAudioResolver
import app.logdate.feature.editor.ui.image.ImageBlockPreview
import app.logdate.feature.editor.ui.layout.EditorFocus
import app.logdate.feature.editor.ui.layout.FocusedBlockLayout
import app.logdate.feature.editor.ui.layout.editorFocus
import app.logdate.feature.editor.ui.state.BlocksUiState
import app.logdate.feature.editor.ui.text.TextBlockContent
import app.logdate.feature.editor.ui.video.VideoBlockEditor
import app.logdate.ui.platform.PlatformPredictiveBackHandler
import app.logdate.ui.theme.Spacing
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.uuid.Uuid

private sealed interface EditorDisplay {
    data object Empty : EditorDisplay

    data class Expanded(
        val block: EntryBlockUiState,
    ) : EditorDisplay

    data object List : EditorDisplay
}

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
    onAudioResolverReady: (Uuid, PendingAudioResolver) -> Unit = { _, _ -> },
    focusState: EditorFocus = editorFocus(uiState.blocks, uiState.expandedBlockId),
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

    // Single flow for seek progress — ensures seeks are processed sequentially via collectLatest,
    // dropping intermediate values if a new one arrives before the previous seekTo completes.
    val seekFlow = remember { MutableStateFlow<Float?>(null) }
    LaunchedEffect(backTarget) {
        seekFlow.collectLatest { fraction ->
            if (fraction != null) transitionState.seekTo(fraction, targetState = backTarget)
        }
    }

    PlatformPredictiveBackHandler(
        enabled = displayState is EditorDisplay.Expanded,
        onProgress = { fraction ->
            seekFlow.value = fraction
            onBackProgress(fraction)
        },
        onBack = {
            scope.launch {
                seekFlow.value = null
                onBackCommit() // start chrome return concurrently with the block animation
                transitionState.animateTo(backTarget)
                onDismissExpanded()
            }
        },
        onCancel = {
            // currentState is the state we were animating from (Expanded), safe without !!
            scope.launch {
                seekFlow.value = null
                onBackCancel() // start chrome return concurrently with the block snap-back
                transitionState.animateTo(transitionState.currentState)
            }
        },
    )

    val transition = rememberTransition(transitionState, label = "editorDisplay")

    EditorSharedTransitionContainer(modifier = modifier.fillMaxSize()) {
        val sts = this
        CompositionLocalProvider(LocalSharedTransitionScope provides sts) {
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
                            EmptyEditorStateContent(
                                onStartTextBlock = { id -> uiState.onCreateBlock(BlockType.TEXT, id) },
                                onStartPhotoBlock = { id -> uiState.onCreateBlock(BlockType.IMAGE, id) },
                                onStartAudioBlock = { id -> uiState.onCreateBlock(BlockType.AUDIO, id) },
                                onStartCameraBlock = { id -> uiState.onCreateBlock(BlockType.CAMERA, id) },
                                retainConsumedTileIds = true,
                                textTileId = pickerTileIds.textId,
                                photoTileId = pickerTileIds.photoId,
                                audioTileId = pickerTileIds.audioId,
                                cameraTileId = pickerTileIds.cameraId,
                                modifier = Modifier.fillMaxSize().padding(start = Spacing.sm, top = Spacing.sm, end = Spacing.sm),
                            )
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
                                            .sharedBounds(
                                                rememberSharedContentState("block_surface_${liveBlock.id}"),
                                                animatedVisibilityScope = avs,
                                            ),
                                ) {
                                    val editBlock: @Composable () -> Unit = {
                                        BlockContentInner(
                                            block = liveBlock,
                                            isExpanded = true,
                                            onBlockFocused = uiState.onBlockFocused,
                                            onBlockUpdated = uiState.onUpdateBlock,
                                            onBlockDeleted = uiState.onDeleteBlock,
                                            onAudioResolverReady = onAudioResolverReady,
                                            modifier = Modifier.fillMaxSize(),
                                        )
                                    }
                                    if (liveBlock is TextBlockUiState) {
                                        val photo = uiState.blocks.filterIsInstance<ImageBlockUiState>().firstOrNull { it.uri != null }
                                        FocusedBlockLayout(
                                            textEditor = true,
                                            preview =
                                                if (photo == null) {
                                                    null
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
                            EntryMemorySequence(
                                uiState = renderedContent,
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

@Suppress("ktlint:standard:function-naming")
@Composable
private fun BlockContentInner(
    block: EntryBlockUiState,
    isExpanded: Boolean,
    onBlockFocused: (Uuid) -> Unit,
    onBlockUpdated: (EntryBlockUiState) -> Unit,
    onBlockDeleted: (Uuid) -> Unit,
    onAudioResolverReady: (Uuid, PendingAudioResolver) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    when (block) {
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
                modifier = modifier,
            )

        is CameraBlockUiState ->
            CameraBlockEditor(
                block = block,
                onBlockUpdated = onBlockUpdated,
                onDeleteRequested = { onBlockDeleted(block.id) },
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
