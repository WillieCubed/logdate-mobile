package app.logdate.mobile.home.ui

import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

@Composable
fun HomeBottomNavigation(
    selectedItem: HomeRouteDestination,
    onNavigationUpdate: (route: HomeRouteDestination) -> Unit,
) {
    NavigationBar {
        HomeRouteDestination.ALL.forEach { route ->
            NavigationBarItem(
                icon = {
                    Icon(
                        if (route == selectedItem) route.selectedIcon else route.unselectedIcon,
                        contentDescription = route.label
                    )
                },
                label = { Text(route.label) },
                selected = selectedItem == route,
                onClick = { onNavigationUpdate(route) }
            )
        }
    }
}