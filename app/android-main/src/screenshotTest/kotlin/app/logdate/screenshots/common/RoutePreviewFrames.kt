package app.logdate.screenshots.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp

enum class RoutePreviewTab(
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
) {
    TIMELINE("Timeline", Icons.Filled.History, Icons.Outlined.History),
    LOCATION("Locations", Icons.Filled.LocationOn, Icons.Outlined.LocationOn),
    JOURNALS("Journals", Icons.Filled.Book, Icons.Outlined.Book),
    REWIND("Rewind", Icons.Filled.DateRange, Icons.Outlined.DateRange),
}

@Composable
fun HomeTabRouteFrame(
    selectedTab: RoutePreviewTab,
    showFab: Boolean = true,
    content: @Composable () -> Unit,
) {
    val widthDp = LocalConfiguration.current.screenWidthDp
    val useRail = widthDp >= MEDIUM_WIDTH_DP

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        floatingActionButtonPosition = FabPosition.End,
        floatingActionButton = {
            if (showFab) {
                FloatingActionButton(onClick = {}) {
                    Icon(Icons.Default.Add, contentDescription = "New entry")
                }
            }
        },
        bottomBar = {
            if (!useRail) {
                NavigationBar {
                    RoutePreviewTab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = tab == selectedTab,
                            onClick = {},
                            icon = {
                                Icon(
                                    imageVector = if (tab == selectedTab) tab.selectedIcon else tab.unselectedIcon,
                                    contentDescription = tab.title,
                                )
                            },
                            label = { Text(tab.title) },
                        )
                    }
                }
            }
        },
    ) { paddingValues ->
        if (useRail) {
            Row(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
            ) {
                NavigationRail {
                    RoutePreviewTab.entries.forEach { tab ->
                        NavigationRailItem(
                            selected = tab == selectedTab,
                            onClick = {},
                            icon = {
                                Icon(
                                    imageVector = if (tab == selectedTab) tab.selectedIcon else tab.unselectedIcon,
                                    contentDescription = tab.title,
                                )
                            },
                            label = { Text(tab.title) },
                        )
                    }
                }
                Box(modifier = Modifier.fillMaxSize()) {
                    content()
                }
            }
        } else {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
            ) {
                content()
            }
        }
    }
}

@Composable
fun HomeDetailRouteFrame(
    selectedTab: RoutePreviewTab,
    mainContent: @Composable () -> Unit,
    detailContent: @Composable () -> Unit,
) {
    val widthDp = LocalConfiguration.current.screenWidthDp
    val useTwoPane = widthDp >= EXPANDED_WIDTH_DP

    if (!useTwoPane) {
        detailContent()
        return
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) { paddingValues ->
        Row(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
        ) {
            NavigationRail {
                RoutePreviewTab.entries.forEach { tab ->
                    NavigationRailItem(
                        selected = tab == selectedTab,
                        onClick = {},
                        icon = {
                            Icon(
                                imageVector = if (tab == selectedTab) tab.selectedIcon else tab.unselectedIcon,
                                contentDescription = tab.title,
                            )
                        },
                        label = { Text(tab.title) },
                    )
                }
            }
            Column(modifier = Modifier.fillMaxSize()) {
                Row(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier =
                            Modifier
                                .weight(1f)
                                .widthIn(max = 360.dp)
                                .fillMaxHeight(),
                    ) {
                        mainContent()
                    }
                    VerticalDivider()
                    Box(
                        modifier =
                            Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                    ) {
                        detailContent()
                    }
                }
            }
        }
    }
}

@Composable
fun PlaceholderRouteFrame(
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

private const val MEDIUM_WIDTH_DP = 600
private const val EXPANDED_WIDTH_DP = 840
