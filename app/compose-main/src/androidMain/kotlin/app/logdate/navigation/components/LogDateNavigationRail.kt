package app.logdate.navigation.components

import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.logdate.navigation.scenes.HomeTab

/**
 * A vertical navigation rail component that displays a list of tabs with an optional
 * header slot for custom content. Designed for use in the LogDate app's main navigation.
 *
 * This component respects device insets like notches and cutouts to ensure UI elements
 * don't overlap with system areas.
 *
 * @param selectedTab The currently selected tab
 * @param onTabSelected Callback for when a tab is selected
 * @param modifier Modifier for the component
 * @param headerContent Optional composable for header content
 * @param tabs The list of tabs to display, defaults to HomeTab entries
 */
@Composable
fun LogDateNavigationRail(
    selectedTab: HomeTab,
    onTabSelected: (HomeTab) -> Unit,
    modifier: Modifier = Modifier,
    tabs: List<HomeTab> = HomeTab.entries,
    headerContent: (@Composable () -> Unit)? = null,
) {
    NavigationRail(
        // Apply the provided modifier directly to the NavigationRail
        modifier = modifier.safeDrawingPadding(),
        header = headerContent?.let { { headerContent() } },
    ) {
        // Display all tabs
        tabs.forEach { tab ->
            NavigationRailItem(
                selected = tab == selectedTab,
                onClick = { onTabSelected(tab) },
                icon = {
                    Icon(
                        imageVector = if (tab == selectedTab) tab.selectedIcon else tab.unselectedIcon,
                        contentDescription = tab.title,
                        modifier = Modifier.size(24.dp)
                    )
                },
                label = {
                    Text(
                        text = tab.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
            )
        }
    }
}