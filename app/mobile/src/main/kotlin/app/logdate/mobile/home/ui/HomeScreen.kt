package app.logdate.mobile.home.ui

import android.annotation.SuppressLint
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.LibraryAdd
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFloatingActionButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import app.logdate.feature.journals.ui.JournalOpenCallback
import app.logdate.feature.journals.ui.JournalsRoute
import app.logdate.feature.rewind.ui.RewindRoute
import app.logdate.feature.timeline.ui.TimelineRoute
import app.logdate.mobile.R
import app.logdate.ui.theme.LogDateTheme

@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    currentDestination: HomeRouteData,
    onUpdateDestination: (HomeRouteData) -> Unit,
    onNewJournal: () -> Unit,
    onOpenJournal: JournalOpenCallback,
    onCreateEntry: () -> Unit,
    onViewPreviousRewinds: () -> Unit,
    onOpenSettings: () -> Unit,
    shouldShowBottomBar: Boolean = true,
    shouldShowNavRail: Boolean = false,
    isLargeDevice: Boolean = false,
) {
    var showOptionsDropdown by rememberSaveable { mutableStateOf(false) }

    fun handleNavUpdate(newDestination: HomeRouteData) {
        onUpdateDestination(newDestination)
    }

    fun handleFabAction() {
        when (currentDestination) {
            HomeRouteData.Timeline -> {
                onCreateEntry()
            }

            HomeRouteData.Journals -> {
                onNewJournal()
            }

            else -> {
                // Do nothing for now
            }
        }
    }

    val fabPosition: FabPosition = when (currentDestination) {
        HomeRouteData.Timeline -> {
            FabPosition.Center
        }

        else -> {
            FabPosition.End
        }
    }
    val shouldShowFab = currentDestination != HomeRouteData.Rewind

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(currentDestination.label) },
                scrollBehavior = scrollBehavior,
                actions = {
                    IconButton(onClick = {
                        showOptionsDropdown = true
                    }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Options"
                        )
                    }
                    DropdownMenu(
                        expanded = showOptionsDropdown,
                        onDismissRequest = { showOptionsDropdown = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("Settings") },
                            onClick = {
                                showOptionsDropdown = false
                                onOpenSettings()
                            },
                        )
                    }
                }
            )
        },
        floatingActionButtonPosition = fabPosition,
        floatingActionButton = {
            if (isLargeDevice) {
                // No need to render duplicate FAB if the nav rail is showing
                return@Scaffold
            }
            AnimatedVisibility(
                shouldShowFab,
                enter = scaleIn(),
                exit = scaleOut(),
            ) {
                when (currentDestination) {
                    HomeRouteData.Timeline -> {
                        LargeFloatingActionButton(onClick = { handleFabAction() }) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = stringResource(R.string.action_write_in_journal)
                            )
                        }
                    }

                    HomeRouteData.Journals -> {
                        FloatingActionButton(onClick = { handleFabAction() }) {
                            Icon(
                                Icons.Outlined.Add,
                                contentDescription = stringResource(R.string.action_create_journal)
                            )
                        }
                    }

                    HomeRouteData.Library -> {
                        FloatingActionButton(onClick = { handleFabAction() }) {
                            Icon(
                                Icons.Outlined.LibraryAdd,
                                contentDescription = stringResource(R.string.action_add_to_library)
                            )
                        }
                    }

                    else -> {
                        // No FAB on this route
                    }
                }

            }
        },
        bottomBar = {
            if (shouldShowBottomBar) {
                HomeBottomNavigation(currentDestination, ::handleNavUpdate)
            }
        },
    ) {
        Row(
            Modifier
                .fillMaxSize()
                .consumeWindowInsets(it)
                .padding(it)
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(
                        WindowInsetsSides.Horizontal,
                    ),
                ),
        ) {
            if (shouldShowNavRail) {
                val itemPosition = if (isLargeDevice) {
                    NavigationItemsPosition.BOTTOM
                } else {
                    NavigationItemsPosition.TOP
                }
                AppNavigationRail(
                    currentDestination,
                    ::handleNavUpdate,
                    ::handleFabAction,
                    itemsPosition = itemPosition,
                    shouldShowFab = isLargeDevice,
                )
            }
            when (currentDestination) {
                HomeRouteData.Timeline -> {
                    TimelineRoute(
                        onOpenTimelineItem = { /* TODO */ },
                    )
                }

                HomeRouteData.Journals -> {
                    JournalsRoute(
                        onOpenJournal = onOpenJournal,
                    )
                }

                HomeRouteData.Rewind -> {
                    RewindRoute(
                        onOpenRewind = { /* TODO */ },
                        onViewPreviousRewinds = onViewPreviousRewinds,
                    )
                }

                HomeRouteData.Library -> {

                }
            }
        }
    }
}

@Preview
@Composable
fun HomeScreenPreview_Phone() {
    LogDateTheme {
        HomeScreen(
            currentDestination = HomeRouteData.Timeline,
            onUpdateDestination = { },
            onCreateEntry = { },
            onViewPreviousRewinds = { },
            onOpenJournal = { },
            onNewJournal = { },
            onOpenSettings = { },
        )
    }
}

@Preview(device = "spec:parent=pixel_5,orientation=landscape")
@Composable
fun HomeScreenPreview_Phone_Landscape() {
    LogDateTheme {
        HomeScreen(
            currentDestination = HomeRouteData.Timeline,
            onUpdateDestination = { },
            onCreateEntry = { },
            onViewPreviousRewinds = { },
            onOpenJournal = { },
            onNewJournal = { },
            onOpenSettings = { },
            shouldShowBottomBar = false,
            shouldShowNavRail = true,
        )
    }
}

@Preview(device = "spec:id=reference_tablet,shape=Normal,width=1280,height=800,unit=dp,dpi=240")
@Composable
fun HomeScreenPreview_Tablet() {
    LogDateTheme {
        HomeScreen(
            currentDestination = HomeRouteData.Timeline,
            onUpdateDestination = { },
            onCreateEntry = { },
            onViewPreviousRewinds = { },
            shouldShowBottomBar = false,
            shouldShowNavRail = true,
            isLargeDevice = true,
            onOpenJournal = { },
            onNewJournal = { },
            onOpenSettings = { },
        )
    }
}