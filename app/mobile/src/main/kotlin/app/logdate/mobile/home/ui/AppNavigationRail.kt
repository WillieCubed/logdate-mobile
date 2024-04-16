package app.logdate.mobile.home.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.LibraryAdd
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import app.logdate.mobile.R

enum class NavigationItemsPosition {
    TOP, CENTER, BOTTOM,
}

/**
 * A top-level navigation rail meant for tablets (medium width and higher window size classes).
 */
@Composable
fun AppNavigationRail(
    selectedItem: HomeRouteData,
    onNavigationUpdate: (route: HomeRouteData) -> Unit,
    onFabAction: () -> Unit,
    modifier: Modifier = Modifier,
    itemsPosition: NavigationItemsPosition = NavigationItemsPosition.TOP,
    shouldShowFab: Boolean = true,
) {
    NavigationRail(
        modifier = modifier.fillMaxHeight(),
        header = {
            if (shouldShowFab) {
                when (selectedItem) {
                    HomeRouteData.Timeline -> {
                        FloatingActionButton(onClick = { onFabAction() }) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = stringResource(R.string.action_write_in_journal)
                            )
                        }
                    }

                    HomeRouteData.Journals -> {
                        FloatingActionButton(onClick = { onFabAction() }) {
                            Icon(
                                Icons.Outlined.Add,
                                contentDescription = stringResource(R.string.action_create_journal)
                            )
                        }
                    }

                    HomeRouteData.Library -> {
                        FloatingActionButton(onClick = { onFabAction() }) {
                            Icon(
                                Icons.Outlined.LibraryAdd,
                                contentDescription = stringResource(R.string.action_create_journal)
                            )
                        }
                    }

                    else -> {
                        // No FAB on this route
                    }
                }
            }
        },
    ) {
        val itemsArrangement = when (itemsPosition) {
            NavigationItemsPosition.TOP -> Arrangement.Top
            NavigationItemsPosition.CENTER -> Arrangement.Center
            NavigationItemsPosition.BOTTOM -> Arrangement.Bottom
        }
        Column(
            Modifier.fillMaxHeight(),
            verticalArrangement = itemsArrangement,
        ) {
            HomeRouteData.ALL.forEach { route ->
                NavigationRailItem(
                    selected = selectedItem == route,
                    onClick = { onNavigationUpdate(route) },
                    icon = {
                        Icon(
                            if (route == selectedItem) route.selectedIcon else route.unselectedIcon,
                            contentDescription = route.label
                        )
                    },
                    label = { Text(route.label) },
                )
            }
        }
    }
}

@Preview(
    device = "id:pixel_tablet",
    showBackground = true,
    showSystemUi = true,
    name = "Navigation Landscape",
)
@Composable
fun AppNavigationRailPreview_Landscape() {
    AppNavigationRail(
        selectedItem = HomeRouteData.Journals,
        onNavigationUpdate = { },
        onFabAction = { },
    )
}

@Preview(
    device = "spec:parent=pixel_tablet,orientation=portrait",
    showBackground = true,
    showSystemUi = true,
    name = "Navigation Portrait",
)
@Composable
fun AppNavigationRailPreview_Portrait() {
    AppNavigationRail(
        selectedItem = HomeRouteData.Journals,
        onNavigationUpdate = { },
        onFabAction = { },
    )
}