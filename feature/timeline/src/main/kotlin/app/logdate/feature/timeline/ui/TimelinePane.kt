package app.logdate.feature.timeline.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.logdate.feature.timeline.ui.blocks.SuggestedEntryBlock
import app.logdate.feature.timeline.ui.blocks.SuggestedEntryBlockUiState
import app.logdate.ui.theme.Spacing
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

data class TimelineUiState(
    val items: List<TimelineDayUiState> = emptyList(),
)

@Composable
fun TimelinePane(
    uiState: TimelineUiState,
    onNewEntry: () -> Unit,
    onShareMemory: (memoryId: String) -> Unit,
    onOpenDay: (LocalDate) -> Unit,
    showSuggestedEntryBlock: Boolean = false,
    listState: LazyListState = rememberLazyListState(),
    modifier: Modifier = Modifier,
) {
    val panelCoroutineScope = rememberCoroutineScope()
    val showScrollToTop by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0
        }
    }

    Box(modifier = modifier.background(MaterialTheme.colorScheme.surface)) {
        if (showSuggestedEntryBlock) {
            SuggestedEntryBlock(
                state = SuggestedEntryBlockUiState(
                    memoryId = "1",
                    message = "You haven't added any memories today.",
                ),
                onAddToMemory = {},
                onShare = onShareMemory,
            )
        }
        TimelineList(
            items = uiState.items,
            onOpenDay = onOpenDay,
            modifier = Modifier
                .fillMaxWidth()
                // TODO: Ensure status bar is translucent
                .systemBarsPadding(),
        )
        AnimatedVisibility(
            visible = showScrollToTop,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 128.dp),
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it }
        ) {
            FloatingActionButton(
                onClick = {
                    panelCoroutineScope.launch {
                        listState.animateScrollToItem(0)
                    }
                }
            ) {
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowUp,
                    contentDescription = "Scroll to top"
                )
            }
        }
    }
}

@Composable
fun TimelineList(
    items: List<TimelineDayUiState>,
    onOpenDay: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
) {
    LazyColumn(
        contentPadding = PaddingValues(vertical = Spacing.sm),
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(Spacing.xl),
        state = listState,
    ) {
        items(items) { item ->
            TimelineListItem(
                item = item,
                onOpenDay = onOpenDay,
            )
        }
    }
}

//@Composable
//private fun LazyListScope.constructTimeline(
//) {

//}

@Composable
fun TimelineListItem(
    item: TimelineDayUiState,
    onOpenDay: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        modifier = modifier
            .fillMaxWidth()
            .clickable {
                onOpenDay(item.date)
            }
            .padding(Spacing.lg),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text(item.date.toString(), style = MaterialTheme.typography.titleLarge)
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(
                "In summary",
                style = MaterialTheme.typography.labelLarge.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
            )
            Text(item.summary, style = MaterialTheme.typography.bodyMedium)
        }
    }
}


@Preview
@Composable
private fun TimelinePanePreview() {
    TimelinePane(
        uiState = TimelineUiState(),
        onOpenDay = {},
        onNewEntry = {},
        onShareMemory = {},
        showSuggestedEntryBlock = true,
    )
}