package app.logdate.mobile.home.ui

import androidx.activity.BackEventCompat
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffold
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.transition.TransitionSeekController
import app.logdate.feature.timeline.ui.HomeTimelineUiState
import app.logdate.feature.timeline.ui.TimelineDaySelection
import app.logdate.feature.timeline.ui.TimelineDayUiState
import app.logdate.feature.timeline.ui.TimelinePane
import app.logdate.feature.timeline.ui.TimelineUiState
import app.logdate.feature.timeline.ui.details.TimelineDayDetailPanel
import app.logdate.feature.timeline.ui.details.TimelineDetailsEmptyPlaceholder
import app.logdate.ui.SearchBarBase
import app.logdate.ui.theme.Spacing
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
internal fun TimelineScreenContent(
    uiState: HomeTimelineUiState,
    onOpenDay: (day: LocalDate) -> Unit,
    onExitDetails: () -> Unit,
    onCreateEntry: () -> Unit,
    onUpdateFabVisibility: (isVisible: Boolean) -> Unit,
    onOpenEvent: (eventId: String) -> Unit,
    timelineListState: LazyListState = rememberLazyListState(),
) {
    var searchBarExpanded by remember { mutableStateOf(false) }
    val homeScreenScope = rememberCoroutineScope()
    val navigator = rememberListDetailPaneScaffoldNavigator<TimelineDaySelection>()
    val animateHorizontalPaddingAsDp by animateDpAsState(
        targetValue = if (searchBarExpanded) 0.dp else Spacing.lg,
        label = "padding",
    )
    val animateVerticalPaddingAsDp by animateDpAsState(
        targetValue = if (searchBarExpanded) 0.dp else Spacing.sm,
        label = "padding",
    )
    var padding by remember { mutableStateOf(Spacing.sm) }
    val searchBarBackgroundColor by animateColorAsState(
        targetValue = if (searchBarExpanded) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.surfaceContainer,
        label = "backgroundColor",
    )

    fun updateSearchBar(visible: Boolean) {
        searchBarExpanded = visible
        onUpdateFabVisibility(!visible)
    }

    val callback = remember {
        object : OnBackPressedCallback(true) {
            var controller: TransitionSeekController? = null
            private val initialHeight = 56.dp
            private val expandedHeight = 112.dp // Adjust as needed

            override fun handleOnBackStarted(backEvent: BackEventCompat) {
//                controller = TransitionManager.controlDelayedTransition(
//                    view,
//                    transitionSet
//                )
            }

            override fun handleOnBackPressed() {
                updateSearchBar(false)
            }

            override fun handleOnBackProgressed(backEvent: BackEventCompat) {
                controller?.let {
                    val progress = backEvent.progress
                    if (it.isReady) {
                        padding = lerp(0.dp, 16.dp, progress)
//                        it.currentFraction = progress
                    }
                }
            }

            override fun handleOnBackCancelled() {
                controller?.animateToEnd()
            }
        }
    }

    val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    DisposableEffect(callback) {
        backDispatcher?.addCallback(callback)
        onDispose { callback.remove() }
    }

    BackHandler(
        if (searchBarExpanded) {
            true
        } else {
            navigator.canNavigateBack()
        }
    ) {
        if (searchBarExpanded) {
            updateSearchBar(false)
            return@BackHandler
        }
        homeScreenScope.launch {
            navigator.navigateBack()
        }
    }

    ListDetailPaneScaffold(
        directive = navigator.scaffoldDirective,
        value = navigator.scaffoldValue,
        listPane = {
            AnimatedPane {
                val searchFieldState = rememberTextFieldState()
                Box(
                ) {
                    TimelinePane(
                        uiState = TimelineUiState(
                            items = uiState.items,
                        ),
                        onNewEntry = onCreateEntry,
                        onOpenDay = {
                            homeScreenScope.launch {
                                navigator.navigateTo(
                                    ListDetailPaneScaffoldRole.Detail,
                                    TimelineDaySelection.Selected(
                                        it.toString(), it
                                    ), // TODO: Properly reconcile date and ID-based indexing
                                )
                                onOpenDay(it)
                            }
                        },
                        onShareMemory = {
                            TODO("Not yet implemented")
                        },
                        listState = timelineListState,
                        modifier = Modifier
//                            .padding(Spacing.lg)
//                            .clip(MaterialTheme.shapes.large)
                            .fillMaxHeight(),
                    )
                    SearchBarBase(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .fillMaxWidth()
                            .padding()
                            .background(searchBarBackgroundColor),
                        hint = "Search timeline",
                        expanded = searchBarExpanded,
                        onExpand = { updateSearchBar(true) },
                        onDismiss = { updateSearchBar(false) },
                    ) {
//                            BasicTextField(searchFieldState)
                    }
                }
            }
        },
        detailPane = {
            AnimatedPane {
                when (uiState.selectedItem) {
                    TimelineDaySelection.NotSelected -> {
                        TimelineDetailsEmptyPlaceholder(
                            modifier = Modifier
                                .systemBarsPadding()
                                .padding(end = Spacing.lg, top = Spacing.sm, bottom = Spacing.sm)
                                .clip(MaterialTheme.shapes.large),
                        )
                    }

                    is TimelineDaySelection.Selected -> {
                        val data = uiState.selectedItem as TimelineDaySelection.Selected
                        val selectedData = uiState.items.find { it.date == data.day }
                        if (selectedData == null) {
                            TimelineDetailsEmptyPlaceholder(
                                modifier = Modifier
                                    .systemBarsPadding()
                                    .padding(
                                        end = Spacing.lg, top = Spacing.sm, bottom = Spacing.sm
                                    )
                                    .clip(MaterialTheme.shapes.large),
                            )
                            return@AnimatedPane
                        }
                        val uiState = TimelineDayUiState(
                            summary = selectedData.summary,
                            date = selectedData.date,
                            people = selectedData.people,
                            notes = selectedData.notes,
                        )
                        TimelineDayDetailPanel(
                            uiState = uiState,
                            onOpenEvent = onOpenEvent,
                            onExit = {
                                homeScreenScope.launch {
                                    navigator.navigateBack()
                                    onExitDetails()
                                }
                            },
                        )
                    }
                }
            }
        },
    )
}


@Preview(showBackground = true)
@Composable
private fun TimelineScreenContentPreview() {
    TimelineScreenContent(
        uiState = HomeTimelineUiState(),
        onOpenDay = {},
        onExitDetails = {},
        onCreateEntry = {},
        onUpdateFabVisibility = {},
        onOpenEvent = {},
    )
}

@Preview(device = "spec:parent=pixel_tablet", showBackground = true)
@Composable
private fun TimelineScreenContentPreview_Tablet() {
    TimelineScreenContent(
        uiState = HomeTimelineUiState(),
        onOpenDay = {},
        onExitDetails = {},
        onCreateEntry = {},
        onUpdateFabVisibility = {},
        onOpenEvent = {},
    )
}