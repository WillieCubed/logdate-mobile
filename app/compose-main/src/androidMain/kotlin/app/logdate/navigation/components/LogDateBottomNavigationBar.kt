package app.logdate.navigation.components

import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import app.logdate.navigation.scenes.HomeTab

/**
 * A horizontal bottom navigation bar component for the LogDate app that displays the main
 * navigation tabs. Optimized for compact screen layouts.
 *
 * @param selectedTab The currently selected tab
 * @param onTabSelected Callback for when a tab is selected
 * @param modifier Modifier for the component
 * @param tabs The list of tabs to display, defaults to HomeTab entries
 */
@Composable
fun LogDateBottomNavigationBar(
    selectedTab: HomeTab,
    onTabSelected: (HomeTab) -> Unit,
    modifier: Modifier = Modifier,
    tabs: List<HomeTab> = HomeTab.entries
) {
    NavigationBar(
        modifier = modifier,
//        containerColor = MaterialTheme.colorScheme.surface,
//        tonalElevation = 3.dp
    ) {
        tabs.forEach { tab ->
            NavigationBarItem(
                selected = tab == selectedTab,
                onClick = { onTabSelected(tab) },
                icon = { 
                    Icon(
                        imageVector = if (tab == selectedTab) tab.selectedIcon else tab.unselectedIcon, 
                        contentDescription = tab.title
                    ) 
                },
                label = { 
                    Text(
                        text = tab.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    ) 
                }
            )
        }
    }
}