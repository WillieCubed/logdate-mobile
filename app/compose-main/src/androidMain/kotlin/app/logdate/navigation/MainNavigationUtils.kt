package app.logdate.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation3.runtime.NavKey
import app.logdate.navigation.routes.core.NavigationStart

/**
 * Creates and remembers a [MainAppNavigator] instance that will be preserved across
 * recompositions. This ensures navigation state is maintained through configuration
 * changes and activity recreations.
 *
 * @param initialRoute The initial route to add to the backstack. 
 *                    Defaults to [NavigationStart].
 * @return A remembered [MainAppNavigator] instance
 */
@Composable
fun rememberMainAppNavigator(
    initialRoute: NavKey = NavigationStart
): MainAppNavigator {
    return remember {
        MainAppNavigator(initialRoute)
    }
}