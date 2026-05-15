@file:Suppress("ktlint:standard:function-naming", "ktlint:standard:no-wildcard-imports")

package app.logdate.ui.timeline

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.logdate.ui.platform.currentPlatform
import app.logdate.ui.sync.SyncAction
import app.logdate.ui.sync.SyncErrorBanner
import app.logdate.ui.sync.SyncPresentation
import app.logdate.ui.theme.Spacing
import app.logdate.ui.timeline.newstuff.EndOfTimelineUiState
import app.logdate.ui.timeline.newstuff.TimelineList
import app.logdate.util.now
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import logdate.client.ui.generated.resources.*
import logdate.client.ui.generated.resources.Res
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Instant
import kotlin.uuid.Uuid

data class TimelineUiState(
    val items: List<TimelineDayUiState> = emptyList(),
    val loadingState: TimelineLoadingState = TimelineLoadingState.Loaded,
    val isLoadingMore: Boolean = false,
    val hasMoreOlderContent: Boolean = false,
    val appendError: String? = null,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelinePane(
    uiState: TimelineUiState,
    onNewEntry: () -> Unit,
    onOpenDay: (LocalDate) -> Unit,
    onVisibleAudioNoteIdsChanged: (Set<Uuid>) -> Unit = {},
    onLoadMoreOlder: () -> Unit = {},
    timelineSuggestion: TimelineSuggestionBlock? = null,
    listState: LazyListState = rememberLazyListState(),
    onSearchClick: () -> Unit = {},
    onProfileClick: () -> Unit = {},
    onStartWriting: () -> Unit = onNewEntry,
    onOpenDraft: (draftId: String) -> Unit = {},
    onShareMemory: (TimelineSuggestionBlockUiState) -> Unit = {},
    onImportBackup: () -> Unit = {},
    onHistoryClick: () -> Unit = {},
    birthday: Instant? = null,
    syncPresentation: SyncPresentation = SyncPresentation.Hidden,
    onSyncAction: (SyncAction) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val scrollBehavior =
        if (currentPlatform.isApple) {
            TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
        } else {
            TopAppBarDefaults.enterAlwaysScrollBehavior()
        }
    val endOfTimelineState by remember(birthday) {
        derivedStateOf {
            if (birthday != null && birthday != Instant.DISTANT_PAST) {
                val birthDate = birthday.toLocalDateTime(TimeZone.currentSystemDefault()).date
                val daysSinceBirth = (LocalDate.now().toEpochDays() - birthDate.toEpochDays()).toInt()
                EndOfTimelineUiState.BirthdayCelebration(birthDate, daysSinceBirth)
            } else {
                EndOfTimelineUiState.DiscoveryEasterEgg
            }
        }
    }

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TimelineTopAppBar(
                scrollBehavior = scrollBehavior,
                onSearchClick = onSearchClick,
                onSettingsClick = onProfileClick,
                onHistoryClick = onHistoryClick,
                // On iPhone and iPad the floating create button is hidden, so the new-entry
                // affordance moves into the top bar as a trailing action. Other hosts keep
                // the FAB and don't need the duplicate.
                onNewEntry = onNewEntry.takeIf { currentPlatform.isApple },
                syncPresentation = syncPresentation,
                onSyncChipClick = { onSyncAction(SyncAction.Retry) },
            )
        },
    ) { paddingValues ->
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(paddingValues),
        ) {
            // Error-tier sync banner sits *inside* the Scaffold body, below the TopAppBar.
            // It inherits content insets and never collides with the system status bar — the
            // load-bearing fix for the original Pixel 8 collision bug. On medium/expanded
            // windows the banner caps at 560dp wide so it doesn't span the entire content
            // pane; on compact phones it fills width minus margins.
            SyncErrorBanner(
                presentation = syncPresentation,
                onAction = onSyncAction,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .widthIn(max = 560.dp),
            )
            Box(modifier = Modifier.fillMaxWidth()) {
                TimelineList(
                    uiState.items,
                    endOfTimelineState,
                    onOpenDay,
                    Modifier.consumeWindowInsets(paddingValues),
                    uiState.loadingState,
                    uiState.isLoadingMore,
                    uiState.hasMoreOlderContent,
                    uiState.appendError,
                    onLoadMoreOlder,
                    timelineSuggestion,
                    onStartWriting,
                    onOpenDraft,
                    onOpenDay,
                    onShareMemory,
                    onVisibleAudioNoteIdsChanged,
                    onImportBackup,
                    listState,
                )

                ScrollToTopButton(
                    listState = listState,
                    modifier =
                        Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = Spacing.lg),
                )
            }
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
                contentDescription = stringResource(Res.string.scroll_to_top),
                modifier = Modifier.padding(end = Spacing.sm),
            )
            Text(stringResource(Res.string.back_to_today))
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
        onVisibleAudioNoteIdsChanged = {},
        timelineSuggestion =
            TimelineSuggestionBlock.EmptyDay(
                message = "What's going on?",
                locationName = "Dallas",
            ),
        birthday = Instant.fromEpochMilliseconds(1655342400000),
    )
}
