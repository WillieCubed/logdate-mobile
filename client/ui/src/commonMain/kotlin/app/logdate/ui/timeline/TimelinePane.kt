package app.logdate.ui.timeline

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import app.logdate.ui.theme.Spacing
import app.logdate.ui.timeline.newstuff.EndOfTimelineUiState
import app.logdate.ui.timeline.newstuff.TimelineList
import app.logdate.util.now
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.ui.tooling.preview.Preview

data class TimelineUiState(
    val items: List<TimelineDayUiState> = emptyList(),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelinePane(
    uiState: TimelineUiState,
    onNewEntry: () -> Unit,
    onShareMemory: (memoryId: String) -> Unit,
    onOpenDay: (LocalDate) -> Unit,
    showSuggestedEntryBlock: Boolean = true,
    listState: LazyListState = rememberLazyListState(),
    onSearchClick: () -> Unit = {},
    onProfileClick: () -> Unit = {},
    onAddToMemory: (memoryId: String) -> Unit = {},
    birthday: Instant? = null,
    modifier: Modifier = Modifier,
) {
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val endOfTimelineState by remember(birthday) {
        derivedStateOf {
            // TODO: Likely fix this redundancy
            if (birthday != null && birthday != Instant.DISTANT_PAST) {
                val birthDate = birthday.toLocalDateTime(TimeZone.currentSystemDefault()).date
                val daysSinceBirth = LocalDate.now().toEpochDays() - birthDate.toEpochDays()
                EndOfTimelineUiState.BirthdayCelebration(birthDate, daysSinceBirth)
            } else {
                EndOfTimelineUiState.DiscoveryEasterEgg
            }
        }
    }

    // This is likely causing nested scaffold padding issues.
    // We need to either avoid the nested Scaffold or avoid applying the padding
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TimelineTopAppBar(
                scrollBehavior = scrollBehavior,
                onSearchClick = onSearchClick,
                onSettingsClick = onProfileClick
            )
        }
    ) { paddingValues ->
        // Only apply horizontal padding to avoid compounding top padding with parent scaffolds
        Box(
            modifier = Modifier.fillMaxWidth()
                .padding(paddingValues)
        ) {

            TimelineList(
                items = uiState.items,
                onOpenDay = onOpenDay,
                showSuggestedEntryBlock = showSuggestedEntryBlock,
                onAddToMemory = onAddToMemory,
                onShare = onShareMemory,
                modifier = Modifier.consumeWindowInsets(paddingValues),
                endOfTimelineState = endOfTimelineState,
                listState = listState,
            )

            ScrollToTopButton(
                listState = listState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = Spacing.lg),
            )
        }
    }
}

@Composable
internal fun ScrollToTopButton(
    listState: LazyListState,
    modifier: Modifier = Modifier,
) {
    val panelCoroutineScope = rememberCoroutineScope()
    val showScrollToTop by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0
        }
    }

    fun scrollToTop() {
        panelCoroutineScope.launch {
            listState.animateScrollToItem(0)
        }
    }

    AnimatedVisibility(
        visible = showScrollToTop,
        modifier = modifier,
        enter = fadeIn() + slideInVertically { it },
        exit = fadeOut() + slideOutVertically { it },
    ) {
        ElevatedButton(
            onClick = ::scrollToTop,
        ) {
            Icon(
                imageVector = Icons.Filled.KeyboardArrowUp,
                contentDescription = "Scroll to top",
                modifier = Modifier.padding(end = Spacing.sm),
            )
            Text("Back to today")
        }
    }
}

//@Composable
//private fun LazyListScope.constructTimeline(
//) {

//}


@Preview
@Composable
private fun TimelinePanePreview() {
    TimelinePane(
        uiState = TimelineUiState(),
        onOpenDay = {},
        onNewEntry = {},
        onShareMemory = {},
        showSuggestedEntryBlock = true,
        // Use a sample birthday for preview
        birthday = Instant.fromEpochMilliseconds(1655342400000), // June 16, 2022
    )
}