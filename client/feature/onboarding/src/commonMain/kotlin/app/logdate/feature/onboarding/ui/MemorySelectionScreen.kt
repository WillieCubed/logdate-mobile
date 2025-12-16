@file:OptIn(ExperimentalSharedTransitionApi::class)

package app.logdate.feature.onboarding.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.logdate.client.media.MediaObject
import app.logdate.ui.theme.LogDateTheme
import app.logdate.ui.theme.Spacing
import kotlin.time.Duration
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * UI state for the memory selection screen.
 */
data class MemorySelectionUiState(
    val allMemories: List<MediaObject> = emptyList(),
    val aiCuratedMemories: List<MediaObject> = emptyList(),
    val selectedMemoryIds: Set<String> = emptySet(),
    val isLoading: Boolean = true,
    val hasMoreMemories: Boolean = true,
    val isLoadingMore: Boolean = false,
)

/**
 * Screen for selecting memories to import during onboarding.
 * Features AI-curated high emotional salience content and infinite scroll.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemorySelectionScreen(
    uiState: MemorySelectionUiState,
    onBack: () -> Unit,
    onContinue: () -> Unit,
    onToggleMemorySelection: (String) -> Unit,
    onLoadMoreMemories: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expandedMemory by remember { mutableStateOf<MediaObject?>(null) }

    SharedTransitionLayout {
        val sharedTransitionScope = this

        AnimatedVisibility(
            visible = true,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            val animatedVisibilityScope = this

            Scaffold(
                modifier = modifier,
                topBar = {
                    TopAppBar(
                        title = { Text("Select Memories") },
                        navigationIcon = {
                            IconButton(onClick = onBack) {
                                Icon(
                                    Icons.AutoMirrored.Default.ArrowBack,
                                    contentDescription = "Back"
                                )
                            }
                        },
                    )
                },
            ) { contentPadding ->
                Box {
                    with(sharedTransitionScope) {
                        MemorySelectionContent(
                            uiState = uiState,
                            onToggleMemorySelection = onToggleMemorySelection,
                            onLoadMoreMemories = onLoadMoreMemories,
                            onContinue = onContinue,
                            onMemoryLongPress = { memory -> expandedMemory = memory },
                            onMemoryLongPressEnd = { expandedMemory = null },
                            expandedMemory = expandedMemory,
                            animatedVisibilityScope = animatedVisibilityScope,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(contentPadding),
                        )
                    }

                    // Expanded memory overlay with shared element transition
                    AnimatedVisibility(
                        visible = expandedMemory != null,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        expandedMemory?.let { memory ->
                            with(sharedTransitionScope) {
                                ExpandedMemoryOverlay(
                                    memory = memory,
                                    onDismiss = { expandedMemory = null },
                                    animatedVisibilityScope = this@AnimatedVisibility,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Content for memory selection with scrollable list.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun SharedTransitionScope.MemorySelectionContent(
    uiState: MemorySelectionUiState,
    onToggleMemorySelection: (String) -> Unit,
    onLoadMoreMemories: () -> Unit,
    onContinue: () -> Unit,
    onMemoryLongPress: (MediaObject) -> Unit,
    onMemoryLongPressEnd: () -> Unit,
    expandedMemory: MediaObject?,
    animatedVisibilityScope: AnimatedVisibilityScope,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        // Header
        item {
            Column(
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                Text(
                    text = "Choose memories to import",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Medium,
                )

                Text(
                    text = "We've found some photos and videos that might have special meaning to you. Select the ones you'd like to import.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // AI-curated section
        if (uiState.aiCuratedMemories.isNotEmpty()) {
            item {
                AICuratedMemoriesSection(
                    memories = uiState.aiCuratedMemories,
                    selectedMemoryIds = uiState.selectedMemoryIds,
                    onToggleMemorySelection = onToggleMemorySelection,
                    onMemoryLongPress = onMemoryLongPress,
                    onMemoryLongPressEnd = onMemoryLongPressEnd,
                    expandedMemory = expandedMemory,
                    animatedVisibilityScope = animatedVisibilityScope,
                )
            }
        }

        // All memories section
        if (uiState.allMemories.isNotEmpty()) {
            item {
                Text(
                    text = "All memories",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            item {
                AllMemoriesStaggeredGrid(
                    memories = uiState.allMemories,
                    selectedMemoryIds = uiState.selectedMemoryIds,
                    onToggleMemorySelection = onToggleMemorySelection,
                    onMemoryLongPress = onMemoryLongPress,
                    onMemoryLongPressEnd = onMemoryLongPressEnd,
                    isLoadingMore = uiState.isLoadingMore,
                    hasMoreMemories = uiState.hasMoreMemories,
                    onLoadMoreMemories = onLoadMoreMemories,
                    expandedMemory = expandedMemory,
                    animatedVisibilityScope = animatedVisibilityScope,
                )
            }
        }

        // Continue button
        item {
            Button(
                onClick = onContinue,
                enabled = uiState.selectedMemoryIds.isNotEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.lg),
            ) {
                Text("Continue with ${uiState.selectedMemoryIds.size} memories")
            }
        }
    }
}

/**
 * Section displaying AI-curated memories with high emotional salience.
 */
@Composable
private fun SharedTransitionScope.AICuratedMemoriesSection(
    memories: List<MediaObject>,
    selectedMemoryIds: Set<String>,
    onToggleMemorySelection: (String) -> Unit,
    onMemoryLongPress: (MediaObject) -> Unit,
    onMemoryLongPressEnd: () -> Unit,
    expandedMemory: MediaObject?,
    animatedVisibilityScope: AnimatedVisibilityScope,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(
                        MaterialTheme.colorScheme.primary,
                        CircleShape
                    )
            )
            Text(
                text = "Moments that might matter most",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }

        Text(
            text = "Our AI identified these as potentially meaningful memories based on visual content, timing, and context.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // Fixed height staggered grid for AI curated content
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp),
        ) {
            LazyVerticalStaggeredGrid(
                columns = StaggeredGridCells.Fixed(3),
                contentPadding = PaddingValues(0.dp),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalItemSpacing = Spacing.sm,
                modifier = Modifier.fillMaxSize(),
            ) {
                items(memories.take(6)) { memory ->
                    MemoryThumbnail(
                        memory = memory,
                        isSelected = memory.uri in selectedMemoryIds,
                        onToggleSelection = { onToggleMemorySelection(memory.uri) },
                        onLongPress = { onMemoryLongPress(memory) },
                        onLongPressEnd = onMemoryLongPressEnd,
                        isExpanded = expandedMemory == memory,
                        animatedVisibilityScope = animatedVisibilityScope,
                    )
                }
            }
        }
    }
}

/**
 * Staggered grid for all memories with infinite scroll.
 */
@Composable
private fun SharedTransitionScope.AllMemoriesStaggeredGrid(
    memories: List<MediaObject>,
    selectedMemoryIds: Set<String>,
    onToggleMemorySelection: (String) -> Unit,
    onMemoryLongPress: (MediaObject) -> Unit,
    onMemoryLongPressEnd: () -> Unit,
    isLoadingMore: Boolean,
    hasMoreMemories: Boolean,
    onLoadMoreMemories: () -> Unit,
    expandedMemory: MediaObject?,
    animatedVisibilityScope: AnimatedVisibilityScope,
    modifier: Modifier = Modifier,
) {
    // Calculate height based on content to avoid infinite dimensions
    val gridHeight = remember(memories.size) {
        // Estimate height based on number of items and average item height
        val estimatedRows = (memories.size + 2) / 3 // 3 columns
        (estimatedRows * 120).dp // Average item height
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(gridHeight.coerceAtMost(600.dp)), // Max height to prevent infinite dimensions
    ) {
        LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Fixed(3),
            contentPadding = PaddingValues(0.dp),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalItemSpacing = Spacing.sm,
            modifier = Modifier.fillMaxSize(),
        ) {
            items(memories) { memory ->
                MemoryThumbnail(
                    memory = memory,
                    isSelected = memory.uri in selectedMemoryIds,
                    onToggleSelection = { onToggleMemorySelection(memory.uri) },
                    onLongPress = { onMemoryLongPress(memory) },
                    onLongPressEnd = onMemoryLongPressEnd,
                    isExpanded = expandedMemory == memory,
                    animatedVisibilityScope = animatedVisibilityScope,
                )
            }

            // Loading indicator
            if (isLoadingMore) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(Spacing.lg),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }

            // Load more trigger
            if (hasMoreMemories && !isLoadingMore) {
                item {
                    LaunchedEffect(Unit) {
                        onLoadMoreMemories()
                    }
                }
            }
        }
    }
}

/**
 * Calculates the native aspect ratio of a media object, constrained to max 3:2.
 */
private fun MediaObject.getNativeAspectRatio(): Float {
    // For now, use placeholder values - in real implementation would get actual dimensions
    // from media metadata or by loading the image/video
    return when (this) {
        is MediaObject.Image -> {
            // Placeholder: varies by image
            listOf(1.0f, 1.1f, 1.2f, 1.3f, 1.4f, 1.5f).random()
        }

        is MediaObject.Video -> {
            // Placeholder: standard video aspect ratios
            listOf(1f, 1.2f, 1.33f, 1.5f).random()
        }
    }
}

/**
 * A thumbnail for a memory (photo or video) with selection state and long press support.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun SharedTransitionScope.MemoryThumbnail(
    memory: MediaObject,
    isSelected: Boolean,
    onToggleSelection: () -> Unit,
    onLongPress: () -> Unit,
    onLongPressEnd: () -> Unit,
    isExpanded: Boolean,
    animatedVisibilityScope: AnimatedVisibilityScope,
    modifier: Modifier = Modifier,
) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 1.05f else 1f,
        animationSpec = tween(durationMillis = 100),
        label = "thumbnail_scale"
    )

    // Use native aspect ratio of the content, constrained to max 3:2
    val aspectRatio = remember(memory.uri) { memory.getNativeAspectRatio() }

    val thumbnailScope = rememberCoroutineScope()

    Card(
        modifier = modifier
            .aspectRatio(aspectRatio)
            .scale(scale)
            .then(
                if (!isExpanded) {
                    Modifier.sharedElement(
                        sharedContentState = rememberSharedContentState("memory-${memory.uri}"),
                        animatedVisibilityScope = animatedVisibilityScope
                    )
                } else {
                    Modifier
                }
            )
            .pointerInput(memory.uri) {
                detectTapGestures(
                    onTap = { onToggleSelection() },
                    onPress = {
                        isPressed = true
                        val longPressJob = thumbnailScope.launch {
                            delay(500) // 500ms for long press
                            onLongPress()
                        }
                        tryAwaitRelease()
                        longPressJob.cancel()
                        isPressed = false
                        onLongPressEnd()
                    }
                )
            },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isSelected) 4.dp else 1.dp,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (isSelected) {
                        Modifier.border(
                            2.dp,
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.shapes.medium
                        )
                    } else {
                        Modifier
                    }
                )
        ) {
            // Placeholder for image/video content - in real implementation would load from filesystem
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clip(MaterialTheme.shapes.medium),
                contentAlignment = Alignment.Center,
            ) {
                when (memory) {
                    is MediaObject.Video -> {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = "Video",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    is MediaObject.Image -> {
                        // Placeholder for image - real implementation would use filesystem to load image
                        Text(
                            "IMG",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // Selection indicator
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(Spacing.sm)
                        .size(24.dp)
                        .background(
                            MaterialTheme.colorScheme.primary,
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

/**
 * Expanded memory overlay that appears when long-pressing a memory with shared element transition.
 */
@Composable
private fun SharedTransitionScope.ExpandedMemoryOverlay(
    memory: MediaObject,
    onDismiss: () -> Unit,
    animatedVisibilityScope: AnimatedVisibilityScope,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.7f))
            .clickable { onDismiss() },
        contentAlignment = Alignment.Center,
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .aspectRatio(1f)
                .sharedElement(
                    sharedContentState = rememberSharedContentState("memory-${memory.uri}"),
                    animatedVisibilityScope = animatedVisibilityScope
                )
                .clickable { }, // Prevent dismissing when clicking on the card itself
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
            elevation = CardDefaults.cardElevation(
                defaultElevation = 8.dp,
            ),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                when (memory) {
                    is MediaObject.Video -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(Spacing.md),
                        ) {
                            Icon(
                                Icons.Default.PlayArrow,
                                contentDescription = "Video",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(48.dp),
                            )
                            Text(
                                memory.name,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    is MediaObject.Image -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(Spacing.md),
                        ) {
                            Text(
                                "IMAGE",
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                memory.name,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Preview
@Composable
private fun MemorySelectionScreenPreview() {
    LogDateTheme {
        val sampleMemories = (1..20).map { index ->
            if (index % 3 == 0) {
                MediaObject.Video(
                    uri = "sample$index",
                    size = 2048,
                    name = "VID_$index.mp4",
                    timestamp = Clock.System.now(),
                    duration = kotlin.time.Duration.parse("30s"),
                )
            } else {
                MediaObject.Image(
                    uri = "sample$index",
                    size = 1024,
                    name = "IMG_$index.jpg",
                    timestamp = Clock.System.now(),
                )
            }
        }

        MemorySelectionScreen(
            uiState = MemorySelectionUiState(
                allMemories = sampleMemories,
                aiCuratedMemories = sampleMemories.take(6),
                selectedMemoryIds = setOf("sample1", "sample5"),
                isLoading = false,
                hasMoreMemories = true,
                isLoadingMore = false,
            ),
            onBack = {},
            onContinue = {},
            onToggleMemorySelection = {},
            onLoadMoreMemories = {},
        )
    }
}