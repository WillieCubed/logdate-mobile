package app.logdate.mobile.home.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.currentWindowSize
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize

@Composable
internal fun HomeScaffoldWrapper(
    showFab: Boolean,
    onFabClick: (HomeRouteDestination) -> Unit,
    content: @Composable (HomeRouteDestination) -> Unit = {},
) {
    var currentDestination: HomeRouteDestination by rememberSaveable {
        mutableStateOf(
            HomeRouteDestination.Timeline
        )
    }
    val windowSize = with(LocalDensity.current) {
        currentWindowSize().toSize().toDpSize()
    }
    val layoutType = when {
        windowSize.width < 600.dp -> NavigationSuiteType.NavigationBar
        windowSize.width < 1200.dp -> NavigationSuiteType.NavigationRail
        else -> NavigationSuiteType.NavigationDrawer
    }/* if (windowSize.width >= 1200.dp) {
    NavigationSuiteType.NavigationDrawer
} else {*/
    NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(
        currentWindowAdaptiveInfo()
    )/*}*/

    val fabSize by animateDpAsState(
        targetValue = if (showFab) 88.dp else 0.dp,
        label = "fabSize",
    )

    NavigationSuiteScaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        layoutType = layoutType,
        navigationSuiteColors = NavigationSuiteDefaults.colors(
            navigationBarContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            navigationRailContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            navigationDrawerContainerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        navigationSuiteItems = {
            if (layoutType == NavigationSuiteType.NavigationRail) {
                // Put FAB here
            }
            HomeRouteDestination.ALL.forEach {
                item(
                    selected = it == currentDestination,
                    onClick = {
                        currentDestination = it
                    },
                    icon = {
                        Icon(
                            imageVector = if (it == currentDestination) {
                                it.selectedIcon
                            } else {
                                it.unselectedIcon
                            },
                            contentDescription = null,
                        )
                    },
                    label = {
                        Text(it.label)
                    },
                )
            }
        },
    ) {
        Box(
            Modifier.fillMaxSize()
        ) {
            content(currentDestination)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .systemBarsPadding()
                    .offset(y = (-16).dp),
//                enter = fadeIn() + expandIn { IntSize(width = 1, height = 1) },
            ) {
                LargeFloatingActionButton(
                    modifier = Modifier.size(fabSize),
                    onClick = {
                        onFabClick(currentDestination)
                    },
                ) {
                    Icon(
                        Icons.Default.EditNote,
                        contentDescription = "Create new entry",
                    )
                }
            }
        }
    }
}