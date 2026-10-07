package app.logdate.feature.editor.ui.blocks

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import app.logdate.feature.editor.ui.camera.CapturedMediaType
import app.logdate.feature.editor.ui.editor.BlockType
import app.logdate.feature.editor.ui.editor.CameraBlockUiState
import app.logdate.feature.editor.ui.editor.EntryBlockUiState
import app.logdate.feature.editor.ui.editor.ImageBlockUiState
import app.logdate.feature.editor.ui.editor.TextBlockUiState
import app.logdate.feature.editor.ui.editor.delegate.PendingAudioResolver
import app.logdate.feature.editor.ui.layout.EditorColumnMaxWidth
import app.logdate.feature.editor.ui.layout.EditorSurfaceInset
import app.logdate.feature.editor.ui.layout.LocalEditorContextControlsVisible
import app.logdate.feature.editor.ui.state.BlocksUiState
import app.logdate.shared.model.PhotoPresentation
import app.logdate.ui.adaptive.FoldableBookLayout
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlin.uuid.Uuid

@Suppress("ktlint:standard:function-naming")
@Composable
internal fun EntryMemorySequence(
    uiState: BlocksUiState,
    listState: LazyListState,
    onAudioResolverReady: (Uuid, PendingAudioResolver) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val contextControlsVisible = LocalEditorContextControlsVisible.current
    val currentContextControlsVisible by rememberUpdatedState(contextControlsVisible)
    val aspectRatios = remember { mutableStateMapOf<Uuid, Float>() }
    var gesture by remember { mutableStateOf(EntryAddGestureState()) }
    var settleRequest by remember { mutableIntStateOf(0) }
    var revealTravel by remember { mutableStateOf(176.dp) }
    val currentBlockCount by rememberUpdatedState(uiState.blocks.size)
    val settledProgress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val travel = with(density) { revealTravel.toPx() }
    val threshold = minOf(with(density) { 112.dp.toPx() }, travel * 0.72f)
    val addExpanded = gesture.expanded
    val revealProgress = if (gesture.dragging) gesture.progress(travel) else settledProgress.value
    LaunchedEffect(addExpanded, gesture.dragging, settleRequest, contextControlsVisible) {
        if (!contextControlsVisible) {
            settledProgress.snapTo(0f)
            return@LaunchedEffect
        }
        if (!gesture.dragging) {
            settledProgress.animateTo(if (addExpanded) 1f else 0f, spring(dampingRatio = 0.9f, stiffness = 180f))
            if (addExpanded) listState.animateScrollToItem(currentBlockCount)
        }
    }

    suspend fun settleGesture(cancelled: Boolean = false): Boolean {
        if (!gesture.dragging || !currentContextControlsVisible) return false
        settledProgress.snapTo(gesture.progress(travel))
        gesture = if (cancelled) gesture.cancel() else gesture.release(threshold)
        settleRequest++
        return true
    }
    val pullConnection =
        remember(listState, travel, threshold, contextControlsVisible) {
            object : NestedScrollConnection {
                override fun onPostScroll(
                    consumed: Offset,
                    available: Offset,
                    source: NestedScrollSource,
                ): Offset {
                    if (currentContextControlsVisible &&
                        source == NestedScrollSource.UserInput &&
                        available.y < 0f &&
                        !listState.canScrollForward &&
                        !gesture.expanded
                    ) {
                        gesture = gesture.dragBy(available.y, travel)
                        return Offset(0f, available.y)
                    }
                    return Offset.Zero
                }

                override fun onPreScroll(
                    available: Offset,
                    source: NestedScrollSource,
                ): Offset {
                    if (!currentContextControlsVisible ||
                        source != NestedScrollSource.UserInput ||
                        !gesture.dragging ||
                        gesture.expanded
                    ) {
                        return Offset.Zero
                    }
                    if (available.y < 0f) {
                        gesture = gesture.dragBy(available.y, travel)
                        return Offset(0f, available.y)
                    }
                    val consumed = minOf(available.y, gesture.dragDistance)
                    gesture = gesture.dragBy(consumed, travel)
                    return Offset(0f, consumed)
                }

                override suspend fun onPreFling(available: Velocity): Velocity = if (settleGesture()) available else Velocity.Zero

                override suspend fun onPostFling(
                    consumed: Velocity,
                    available: Velocity,
                ): Velocity {
                    settleGesture()
                    return Velocity.Zero
                }
            }
        }
    LaunchedEffect(listState) {
        var previousFooterHeight = 0
        snapshotFlow {
            listState.layoutInfo.visibleItemsInfo
                .firstOrNull { it.key == "add_memory_footer" }
                ?.size
        }.collect { height ->
            if (height != null) {
                if (gesture.dragging && !gesture.expanded && previousFooterHeight > 0 && height > previousFooterHeight) {
                    listState.dispatchRawDelta((height - previousFooterHeight).toFloat())
                }
                previousFooterHeight = height
            }
        }
    }
    var editedBlockId by remember { mutableStateOf<Uuid?>(null) }
    var editRequest by remember { mutableStateOf(0) }
    var newBlockId by remember {
        mutableStateOf(
            uiState.blocks
                .singleOrNull()
                ?.takeIf { it is TextBlockUiState && !it.hasContent() }
                ?.id,
        )
    }
    LaunchedEffect(uiState.blocks.size, newBlockId) {
        val index = uiState.blocks.indexOfFirst { it.id == newBlockId }
        if (index >= 0) listState.animateScrollToItem(index)
    }
    val selectedId = uiState.expandedBlockId
    LaunchedEffect(selectedId, contextControlsVisible) {
        gesture = EntryAddGestureState()
    }
    LaunchedEffect(selectedId, selectedId?.let { aspectRatios[it] }) {
        val index = uiState.blocks.indexOfFirst { it.id == selectedId }
        if (index >= 0 && uiState.blocks[index] is ImageBlockUiState) {
            listState.animateScrollToItem(index)
        }
    }
    val addBlock: (BlockType) -> Unit = { type ->
        gesture = EntryAddGestureState()
        val id = Uuid.random()
        uiState.onCreateBlock(type, id)
        uiState.onBlockFocused(id)
        newBlockId = id
    }
    val sequence: @Composable () -> Unit = {
        Column(Modifier.widthIn(max = EditorColumnMaxWidth).fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().nestedScroll(pullConnection)) {
                val availablePhotoHeight = maxHeight - 16.dp
                val focusedTextHeight = (maxHeight * 0.62f).coerceIn(220.dp, 480.dp)
                val footerHeight = if (contextControlsVisible) 56.dp + revealTravel * revealProgress else 0.dp
                val recordingHeight = unfinishedAudioHeight(maxHeight, maxWidth, footerHeight)

                fun blockWidth(block: EntryBlockUiState): Dp {
                    val photo =
                        block is ImageBlockUiState ||
                            (block is CameraBlockUiState && block.mediaType != CapturedMediaType.VIDEO && block.uri != null)
                    if (!photo) return EditorColumnMaxWidth
                    val framed = block is ImageBlockUiState && block.presentation == PhotoPresentation.Framed
                    val photoHeightLimit = (availablePhotoHeight - if (framed) 108.dp else 0.dp).coerceAtLeast(200.dp)
                    return minOf(EditorColumnMaxWidth, photoHeightLimit * (aspectRatios[block.id] ?: 4f / 3f))
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().testTag("editor_block_list"),
                    contentPadding = PaddingValues(start = EditorSurfaceInset, top = 8.dp, end = EditorSurfaceInset),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    items(uiState.blocks, key = { it.id }) { block ->
                        val animatedWidth by animateDpAsState(blockWidth(block), label = "memoryBlockWidth")
                        Box(Modifier.fillMaxWidth().animateItem(), contentAlignment = Alignment.TopCenter) {
                            MemoryBlockSurface(
                                block = block,
                                isSelected = uiState.expandedBlockId == block.id,
                                onSelect = { uiState.onBlockFocused(block.id) },
                                onEdit = {
                                    uiState.onBlockFocused(block.id)
                                    editedBlockId = block.id
                                    editRequest++
                                },
                                onUpdate = uiState.onUpdateBlock,
                                onRemove = { uiState.onDeleteBlock(block.id) },
                                modifier = Modifier.widthIn(max = animatedWidth),
                            ) {
                                MemoryBlockContent(
                                    block,
                                    newBlockId == block.id,
                                    isSelected = uiState.expandedBlockId == block.id,
                                    editRequest = if (editedBlockId == block.id) editRequest else 0,
                                    onSelect = { uiState.onBlockFocused(block.id) },
                                    onUpdate = uiState.onUpdateBlock,
                                    onRemove = { uiState.onDeleteBlock(block.id) },
                                    onAudioResolverReady = onAudioResolverReady,
                                    onPhotoAspectRatioLoaded = { id, ratio -> aspectRatios[id] = ratio },
                                    focusedTextHeight = focusedTextHeight,
                                    unfinishedRecordingHeight = recordingHeight,
                                )
                            }
                        }
                    }
                    if (contextControlsVisible) {
                        item(key = "add_memory_footer") {
                            val footerWidth by animateDpAsState(
                                uiState.blocks.lastOrNull()?.let(::blockWidth) ?: EditorColumnMaxWidth,
                                label = "addMemoryWidth",
                            )
                            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                                EndOfEntryAddControl(
                                    progress = revealProgress,
                                    expanded = addExpanded,
                                    onExpand = {
                                        gesture = EntryAddGestureState(expanded = true)
                                    },
                                    onCollapse = { gesture = EntryAddGestureState() },
                                    onDrag = { delta ->
                                        gesture = gesture.dragBy(delta, travel)
                                    },
                                    onDragStopped = { cancelled -> scope.launch { settleGesture(cancelled) } },
                                    onRevealHeightChanged = { revealTravel = it },
                                    onAdd = addBlock,
                                    modifier = Modifier.widthIn(max = footerWidth),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    FoldableBookLayout(
        modifier = modifier.fillMaxSize(),
        minPaneWidth = 320.dp,
        startPane = { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) { sequence() } },
        endPane = {},
        standardContent = { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) { sequence() } },
    )
}
