package app.logdate.feature.editor.ui.blocks

import androidx.compose.animation.core.Animatable
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import app.logdate.feature.editor.ui.state.BlocksUiState
import app.logdate.shared.model.PhotoPresentation
import app.logdate.ui.adaptive.FoldableBookLayout
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
    val aspectRatios = remember { mutableStateMapOf<Uuid, Float>() }
    var pullDistance by remember { mutableFloatStateOf(0f) }
    var addExpanded by remember { mutableStateOf(false) }
    var isPulling by remember { mutableStateOf(false) }
    var isCollapsing by remember { mutableStateOf(false) }
    var collapseDistance by remember { mutableFloatStateOf(0f) }
    var settleRequest by remember { mutableIntStateOf(0) }
    var creatingBlock by remember { mutableStateOf(false) }
    val settledProgress = remember { Animatable(0f) }
    val pullThreshold = with(density) { 112.dp.toPx() }
    val pullLimit = with(density) { 160.dp.toPx() }
    val collapseThreshold = with(density) { 24.dp.toPx() }
    val revealProgress =
        when {
            isPulling -> (pullDistance / pullThreshold).coerceIn(0f, 1f)
            isCollapsing -> (1f - collapseDistance / collapseThreshold).coerceIn(0f, 1f)
            else -> settledProgress.value
        }
    LaunchedEffect(addExpanded, settleRequest) {
        if (!isPulling && !isCollapsing) {
            settledProgress.animateTo(if (addExpanded) 1f else 0f, spring(dampingRatio = 0.82f, stiffness = 350f))
        }
    }
    val pullConnection =
        remember(listState, pullThreshold, pullLimit, collapseThreshold, settledProgress) {
            object : NestedScrollConnection {
                override fun onPostScroll(
                    consumed: Offset,
                    available: Offset,
                    source: NestedScrollSource,
                ): Offset {
                    if (source == NestedScrollSource.UserInput && available.y < 0f && !listState.canScrollForward && !addExpanded) {
                        creatingBlock = false
                        isPulling = true
                        pullDistance = (pullDistance - available.y * 0.72f).coerceAtMost(pullLimit)
                        return Offset(0f, available.y)
                    }
                    return Offset.Zero
                }

                override fun onPreScroll(
                    available: Offset,
                    source: NestedScrollSource,
                ): Offset {
                    if (source != NestedScrollSource.UserInput) return Offset.Zero
                    if (addExpanded && available.y > 0f) {
                        isCollapsing = true
                        collapseDistance = (collapseDistance + available.y * 0.72f).coerceAtMost(collapseThreshold)
                        return Offset(0f, available.y)
                    }
                    if (isCollapsing && available.y < 0f) {
                        val consumed = minOf(-available.y, collapseDistance / 0.72f)
                        collapseDistance = (collapseDistance - consumed * 0.72f).coerceAtLeast(0f)
                        return Offset(0f, -consumed)
                    }
                    if (available.y <= 0f || !isPulling) return Offset.Zero
                    val consumed = minOf(available.y, pullDistance / 0.72f)
                    pullDistance = (pullDistance - consumed * 0.72f).coerceAtLeast(0f)
                    return Offset(0f, consumed)
                }

                override suspend fun onPostFling(
                    consumed: Velocity,
                    available: Velocity,
                ): Velocity {
                    if (isCollapsing) {
                        settledProgress.snapTo((1f - collapseDistance / collapseThreshold).coerceIn(0f, 1f))
                        addExpanded = collapseDistance < collapseThreshold
                        collapseDistance = 0f
                        isCollapsing = false
                        settleRequest++
                        return Velocity.Zero
                    }
                    if (!isPulling) return Velocity.Zero
                    settledProgress.snapTo((pullDistance / pullThreshold).coerceIn(0f, 1f))
                    addExpanded = pullDistance >= pullThreshold
                    pullDistance = 0f
                    isPulling = false
                    settleRequest++
                    return Velocity.Zero
                }
            }
        }
    LaunchedEffect(revealProgress, uiState.blocks.size, creatingBlock) {
        if (!creatingBlock && (isPulling || isCollapsing || addExpanded || revealProgress > 0f)) {
            listState.scrollToItem(uiState.blocks.size)
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
    LaunchedEffect(selectedId) {
        addExpanded = false
        pullDistance = 0f
        isPulling = false
        collapseDistance = 0f
        isCollapsing = false
    }
    LaunchedEffect(selectedId, selectedId?.let { aspectRatios[it] }) {
        val index = uiState.blocks.indexOfFirst { it.id == selectedId }
        if (index >= 0 && uiState.blocks[index] is ImageBlockUiState) {
            listState.animateScrollToItem(index)
        }
    }
    val addBlock: (BlockType) -> Unit = { type ->
        creatingBlock = true
        addExpanded = false
        pullDistance = 0f
        isPulling = false
        collapseDistance = 0f
        isCollapsing = false
        val id = Uuid.random()
        uiState.onCreateBlock(type, id)
        uiState.onBlockFocused(id)
        newBlockId = id
    }
    val sequence: @Composable () -> Unit = {
        Column(Modifier.widthIn(max = 720.dp).fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().nestedScroll(pullConnection)) {
                val availablePhotoHeight = maxHeight - 16.dp

                fun blockWidth(block: EntryBlockUiState): Dp {
                    val photo =
                        block is ImageBlockUiState ||
                            (block is CameraBlockUiState && block.mediaType != CapturedMediaType.VIDEO && block.uri != null)
                    if (!photo) return 720.dp
                    val framed = block is ImageBlockUiState && block.presentation == PhotoPresentation.Framed
                    val photoHeightLimit = (availablePhotoHeight - if (framed) 108.dp else 0.dp).coerceAtLeast(200.dp)
                    return minOf(720.dp, photoHeightLimit * (aspectRatios[block.id] ?: 4f / 3f))
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().testTag("editor_block_list"),
                    contentPadding = PaddingValues(start = 8.dp, top = 8.dp, end = 8.dp, bottom = if (addExpanded) 0.dp else 8.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    items(uiState.blocks, key = { it.id }) { block ->
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
                                modifier = Modifier.widthIn(max = blockWidth(block)),
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
                                )
                            }
                        }
                    }
                    item(key = "add_memory_footer") {
                        val footerWidth = uiState.blocks.lastOrNull()?.let(::blockWidth) ?: 720.dp
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                            EndOfEntryAddControl(
                                progress = revealProgress,
                                expanded = addExpanded,
                                onExpand = {
                                    creatingBlock = false
                                    pullDistance = 0f
                                    isPulling = false
                                    collapseDistance = 0f
                                    isCollapsing = false
                                    addExpanded = true
                                },
                                onCollapse = {
                                    pullDistance = 0f
                                    isPulling = false
                                    collapseDistance = 0f
                                    isCollapsing = false
                                    addExpanded = false
                                },
                                onAdd = addBlock,
                                modifier = Modifier.widthIn(max = footerWidth),
                            )
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
