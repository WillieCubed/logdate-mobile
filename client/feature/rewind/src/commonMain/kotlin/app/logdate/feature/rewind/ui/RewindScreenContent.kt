package app.logdate.feature.rewind.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import app.logdate.feature.rewind.ui.components.CollapsingRewindAppBar
import app.logdate.feature.rewind.ui.components.RewindCoverCard
import app.logdate.feature.rewind.ui.overview.RewindHistoryUiState
import app.logdate.feature.rewind.ui.overview.RewindOverviewScreenUiState
import app.logdate.feature.rewind.ui.overview.RewindPreviewUiState
import app.logdate.ui.common.AspectRatios
import app.logdate.ui.theme.Spacing
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.ui.tooling.preview.Preview
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * The main rewind screen content that displays a vertically scrollable list of floating cards.
 * 
 * Each card represents a weekly rewind with snap-to-center scrolling behavior. The design ensures
 * there's always at least one card visible, including a placeholder for the current week when no
 * rewind is ready yet.
 * 
 * ## UX Design Principles:
 * - **Always Visible Content**: Never shows empty state - current week placeholder ensures floating
 *   card design is always showcased
 * - **Instagram Story-like Navigation**: Vertical scrolling with cards snapping to screen center
 * - **Visual Hierarchy**: Ready cards are vibrant and clickable, pending cards are muted
 * - **Progressive Disclosure**: Visual affordances guide users to scroll for more content
 * 
 * ## Interaction Model:
 * - **Snap Scrolling**: Cards automatically center when user stops scrolling
 * - **State-Aware Clicks**: Only available rewinds are interactive
 * - **Next Card Indicator**: Floating button shows when more cards are available below
 * - **End Surprise**: Celebration message appears when user reaches the bottom
 * 
 * @param state The current UI state containing rewind data
 * @param onOpenRewind Callback invoked when user taps on an available rewind card
 * @param modifier Modifier for customizing the appearance and behavior
 */
@OptIn(ExperimentalUuidApi::class, ExperimentalMaterial3Api::class)
@Composable
fun RewindScreenContent(
    state: RewindOverviewScreenUiState,
    onOpenRewind: RewindOpenCallback,
    modifier: Modifier = Modifier,
) {
    // Combine current and past rewinds into a single list, always showing at least current week placeholder
    val rewindItems = remember(state) {
        when (state) {
            is RewindOverviewScreenUiState.Ready -> {
                listOf(state.mostRecentRewind) + state.pastRewinds.map { history ->
                    RewindPreviewUiState(
                        message = "A week to remember",
                        rewindId = history.uid,
                        label = history.title,
                        title = history.title,
                        start = LocalDate(2024, 1, 1), // TODO: Get actual dates
                        end = LocalDate(2024, 1, 7),
                        rewindAvailable = true
                    )
                }
            }
            is RewindOverviewScreenUiState.NotReady -> {
                val currentWeekPlaceholder = RewindPreviewUiState(
                    message = "Still working on this week's rewind...",
                    rewindId = Uuid.random(),
                    label = "This Week",
                    title = "Coming Soon",
                    start = LocalDate(2024, 11, 18), // TODO: Calculate actual current week
                    end = LocalDate(2024, 11, 24),
                    rewindAvailable = false
                )
                
                listOf(currentWeekPlaceholder) + state.pastRewinds.map { history ->
                    RewindPreviewUiState(
                        message = "A week to remember",
                        rewindId = history.uid,
                        label = history.title,
                        title = history.title,
                        start = LocalDate(2024, 1, 1),
                        end = LocalDate(2024, 1, 7),
                        rewindAvailable = true
                    )
                }
            }
            RewindOverviewScreenUiState.Loading -> {
                // Show placeholder cards even while loading
                listOf(
                    RewindPreviewUiState(
                        message = "Loading your rewinds...",
                        rewindId = Uuid.random(),
                        label = "This Week",
                        title = "Loading...",
                        start = LocalDate(2024, 11, 18),
                        end = LocalDate(2024, 11, 24),
                        rewindAvailable = false
                    )
                )
            }
        }
    }

    // Create a scroll behavior for the collapsing app bar
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    
    Scaffold(
        topBar = {
            // Use our new collapsing app bar component
            CollapsingRewindAppBar(
                title = "Rewind",
                scrollBehavior = scrollBehavior
            )
        },
        modifier = modifier,
    ) { paddingValues ->
        // Connect the scroll behavior to the list
        FloatingRewindCardList(
            rewinds = rewindItems,
            onOpenRewind = onOpenRewind,
            scrollBehavior = scrollBehavior,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .nestedScroll(scrollBehavior.nestedScrollConnection)
        )
    }
}

/**
 * A simple vertically scrollable list of floating rewind cards with snap-to-center behavior.
 * 
 * This composable implements a clean scrolling experience with Material 3 design principles:
 * - Cards maintain consistent size and appearance
 * - Snap behavior centers the focused card on scroll
 * - Visual depth created through gradient background
 * - Simple layout with consistent spacing and dimensions
 * 
 * ## Layout Structure:
 * - **Background Gradient**: Creates visual depth from surface to surfaceContainer
 * - **Snap Scrolling**: Uses Compose's built-in snap fling behavior
 * - **Fixed Dimensions**: 360dp width cards with 3:2 aspect ratio
 * - **Simple Spacing**: 32dp between cards, 200dp top padding for centering
 * - **Card Constraints**: 24dp edge padding, fixed max width
 * 
 * @param rewinds List of rewind data to display as cards
 * @param onOpenRewind Callback for card interactions
 * @param modifier Modifier for customizing the list container
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FloatingRewindCardList(
    rewinds: List<RewindPreviewUiState>,
    onOpenRewind: RewindOpenCallback,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val listState = rememberLazyListState()
    val snapFlingBehavior = rememberSnapFlingBehavior(lazyListState = listState)
    
    // Simple fixed card dimensions
    val cardWidth = 360.dp
    val cardHeight = cardWidth * AspectRatios.RATIO_3_2 // 3:2 aspect ratio for rewind cards
    
    Box(modifier = modifier) {
        // Background gradient for depth
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.surface,
                            MaterialTheme.colorScheme.surfaceContainer
                        )
                    )
                )
        )
        
        LazyColumn(
            state = listState,
            flingBehavior = snapFlingBehavior,
            contentPadding = PaddingValues(
                top = 200.dp, // Simple top padding to center first card
                bottom = 300.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize()
        ) {
            itemsIndexed(rewinds) { index, rewind ->
                FloatingRewindCard(
                    rewind = rewind,
                    onOpenRewind = onOpenRewind,
                    listState = listState,
                    index = index,
                    cardHeight = cardHeight,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .widthIn(max = cardWidth)
                )
            }
            
            // Surprise ending item
            item {
                EndOfListSurprise(
                    listState = listState,
                    itemCount = rewinds.size,
                    cardHeight = cardHeight,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .widthIn(max = cardWidth)
                )
            }
        }
        
        // Next card indicator
        NextCardIndicator(
            listState = listState,
            itemCount = rewinds.size,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(Spacing.lg)
        )
    }
}

/**
 * A simple floating rewind card with consistent size and appearance.
 * 
 * ## Visual States:
 * - **Available Rewind**: Primary container surface, bold typography, fully interactive
 * - **Pending Rewind**: Muted surfaceVariant color, medium weight typography, non-interactive
 * 
 * ## Content Layout:
 * - **Label**: Week identifier with primary/outline color based on availability
 * - **Title**: Main rewind name with bold/medium weight based on state
 * - **Message**: Descriptive text about the rewind contents
 * - **Date Range**: Start and end dates with arrow separator
 * 
 * ## Interaction:
 * - Only available rewinds respond to clicks
 * - Visual feedback through Material 3 card ripple effects
 * - Maintains accessibility for screen readers and keyboard navigation
 * 
 * @param rewind The rewind data to display
 * @param onOpenRewind Callback invoked when an available card is tapped
 * @param listState The scroll state (unused but kept for API consistency)
 * @param index The card's position in the list (unused but kept for API consistency)
 * @param cardHeight Fixed height for consistent card sizing
 * @param modifier Modifier for customizing the card appearance
 */
@Composable
fun FloatingRewindCard(
    rewind: RewindPreviewUiState,
    onOpenRewind: RewindOpenCallback,
    listState: LazyListState,
    index: Int,
    cardHeight: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    
    // No scaling or visual effects - keep cards at consistent size
    
    Card(
        onClick = { 
            if (rewind.rewindAvailable) {
                onOpenRewind(rewind.rewindId) 
            }
        },
        modifier = modifier
            .height(cardHeight),
        colors = CardDefaults.cardColors(
            containerColor = if (rewind.rewindAvailable) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
            contentColor = if (rewind.rewindAvailable) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    ) {
        RewindCoverCard(
            rewind = rewind,
            modifier = Modifier
                .fillMaxSize()
                .padding(Spacing.lg)
        )
    }
}


/**
 * A floating indicator that appears when more cards are available below the current view.
 * 
 * This affordance helps users discover that they can scroll to see more rewinds. The indicator:
 * - Appears only when there are more cards below the currently centered one
 * - Uses a subtle circular design with Material 3 primaryContainer color
 * - Positioned in the bottom-right corner with appropriate padding
 * - Automatically disappears when user reaches the last card
 * 
 * ## UX Rationale:
 * The indicator solves the "mystery meat navigation" problem by clearly signaling that more
 * content is available. This is especially important in snap-scrolling interfaces where
 * users might not realize there's more content below.
 * 
 * ## Visual Design:
 * - **Size**: 48dp circular surface (meets minimum touch target)
 * - **Icon**: Simple downward arrow indicating scroll direction
 * - **Transparency**: 70% opacity to avoid overwhelming the main content
 * - **Color**: primaryContainer for subtle brand consistency
 * 
 * @param listState The scroll state for determining current position
 * @param itemCount Total number of items to check if more are available
 * @param modifier Modifier for positioning and styling the indicator
 */
@Composable
fun NextCardIndicator(
    listState: LazyListState,
    itemCount: Int,
    modifier: Modifier = Modifier,
) {
    val currentIndex by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val visibleItems = layoutInfo.visibleItemsInfo
            
            if (visibleItems.isEmpty()) return@derivedStateOf 0
            
            val viewportHeight = layoutInfo.viewportSize.height
            if (viewportHeight == 0) return@derivedStateOf 0
            
            val center = viewportHeight / 2f
            
            // Find item closest to center with optimized calculation
            var closestIndex = 0
            var minDistance = Float.MAX_VALUE
            
            for (item in visibleItems) {
                val itemCenter = item.offset + item.size / 2f
                val distance = abs(itemCenter - center)
                if (distance < minDistance) {
                    minDistance = distance
                    closestIndex = item.index
                }
            }
            
            closestIndex
        }
    }
    
    val hasNext = currentIndex < itemCount - 1
    
    if (hasNext) {
        Surface(
            modifier = modifier
                .size(48.dp)
                .alpha(0.7f),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ) {
            Box(
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "↓",
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }
}

/**
 * A delightful surprise message that appears when users reach the end of their rewind history.
 * 
 * This hidden gem celebrates users who take the time to scroll through all their past rewinds.
 * The element uses careful z-index positioning to appear behind the floating cards, creating
 * a layered reveal effect as users scroll past the final card.
 * 
 * ## UX Philosophy:
 * Small moments of delight like this help create emotional connection with the app. Users who
 * engage deeply with their content (scrolling through all rewinds) deserve recognition and
 * positive reinforcement for their engagement.
 * 
 * ## Technical Implementation:
 * - **Z-Index**: Positioned at -1 to appear behind cards
 * - **Visibility**: Only appears when last item is visible
 * - **Content**: Celebration emoji + encouraging message
 * - **Timing**: Perfectly aligned with the last card's scroll position
 * 
 * ## Content Strategy:
 * - **Emoji**: Universal celebration symbol (🎉)
 * - **Primary Message**: Achievement-focused ("Congrats, you've reached the end!")
 * - **Secondary Message**: Contextual explanation of the accomplishment
 * - **Tone**: Warm and encouraging, matching app personality
 * 
 * @param listState The scroll state for detecting when to show the surprise
 * @param itemCount Total number of cards to determine if we're at the end
 * @param cardHeight Height matching the cards for proper visual alignment
 * @param modifier Modifier for positioning and sizing the surprise content
 */
@Composable
fun EndOfListSurprise(
    listState: LazyListState,
    itemCount: Int,
    cardHeight: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
) {
    val isNearEnd by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull()
            lastVisibleItem?.index == itemCount // This is the surprise item index
        }
    }
    
    Box(
        modifier = modifier
            .height(cardHeight)
            .alpha(if (isNearEnd) 1f else 0f)
            .zIndex(-1f), // Behind the cards
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Text(
                text = "🎉",
                style = MaterialTheme.typography.displayLarge
            )
            Text(
                text = "Congrats, you've reached the end!",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "You've reviewed all your weekly rewinds",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Fallback content for the rare case when no rewind data is available.
 * 
 * **Note**: This composable is currently unused in the new floating card design, as the screen
 * now always shows at least a placeholder card for the current week. However, it's retained
 * for potential future use cases or as a safety fallback.
 * 
 * ## Original UX Intent:
 * - Encourages user engagement when no content is available
 * - Uses friendly, conversational tone ("Go live a little, will ya?")
 * - Provides clear call-to-action to create content
 * 
 * ## Design Principles:
 * - **Encouraging tone**: Motivates rather than frustrates users
 * - **Clear action**: "Share something" button provides immediate next step
 * - **Centered layout**: Focuses attention on the message and action
 * 
 * @param onTouchGrass Callback for the action button to start content creation
 * @param modifier Modifier for customizing the empty state container
 */
@Composable
fun EmptyRewindContent(
    onTouchGrass: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.xl)
        ) {
            Text(
                text = "Go live a little, will ya?",
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center
            )
            Text(
                text = "Still working on the Rewind. Go touch some grass in the meantime.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(onClick = onTouchGrass) {
                Text("Share something")
            }
        }
    }
}


@OptIn(ExperimentalUuidApi::class, ExperimentalMaterial3Api::class)
@Composable
@Preview
private fun RewindScreenPreview() {
    val lastId = Uuid.random()
    RewindScreenContent(
        state = RewindOverviewScreenUiState.NotReady(
            pastRewinds = listOf(
                RewindHistoryUiState(Uuid.random(), "Adventures in Barcelona"),
                RewindHistoryUiState(Uuid.random(), "A Week in Tokyo"),
                RewindHistoryUiState(Uuid.random(), "Mountain Hiking Week"),
                RewindHistoryUiState(lastId, "City Life Chronicles"),
            ),
        ),
        onOpenRewind = {},
    )
}

@OptIn(ExperimentalUuidApi::class, ExperimentalMaterial3Api::class)
@Composable
@Preview
private fun FloatingCardListPreview() {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val rewinds = listOf(
        RewindPreviewUiState(
            message = "Still working on this week's rewind...",
            rewindId = Uuid.random(),
            label = "This Week",
            title = "Coming Soon",
            start = LocalDate(2024, 11, 18),
            end = LocalDate(2024, 11, 24),
            rewindAvailable = false,
        ),
        RewindPreviewUiState(
            message = "Quite the adventurer, aren't you?",
            rewindId = Uuid.random(),
            label = "Week of November 1-7",
            title = "Five Cities in a Week",
            start = LocalDate(2024, 11, 1),
            end = LocalDate(2024, 11, 7),
            rewindAvailable = true,
        ),
        RewindPreviewUiState(
            message = "A journey through time and space",
            rewindId = Uuid.random(),
            label = "Week of October 25-31",
            title = "Adventures in Barcelona",
            start = LocalDate(2024, 10, 25),
            end = LocalDate(2024, 10, 31),
            rewindAvailable = true,
        ),
        RewindPreviewUiState(
            message = "Memories that last forever",
            rewindId = Uuid.random(),
            label = "Week of October 18-24",
            title = "A Week in Tokyo",
            start = LocalDate(2024, 10, 18),
            end = LocalDate(2024, 10, 24),
            rewindAvailable = true,
        ),
    )
    
    FloatingRewindCardList(
        rewinds = rewinds,
        onOpenRewind = {},
        scrollBehavior = scrollBehavior,
        modifier = Modifier.fillMaxSize()
    )
}