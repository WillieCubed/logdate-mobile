package app.logdate.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import app.logdate.navigation.routes.core.NavigationStart

/**
 * Creates and remembers a [MainAppNavigator] instance whose back stack survives
 * configuration changes and process death via [rememberNavBackStack].
 *
 * @param initialRoute The initial route to add to the backstack.
 *                    Defaults to [NavigationStart].
 * @return A remembered [MainAppNavigator] instance
 */
@Composable
fun rememberMainAppNavigator(initialRoute: NavKey = NavigationStart): MainAppNavigator {
    val backStack = rememberNavBackStack(initialRoute)
    return remember(backStack) {
        MainAppNavigator(backStack)
    }
}
