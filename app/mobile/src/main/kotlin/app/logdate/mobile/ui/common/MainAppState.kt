package app.logdate.mobile.ui.common

import androidx.compose.material3.windowsizeclass.WindowHeightSizeClass
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.navigation.NavDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.logdate.mobile.ui.navigation.RouteDestination
import kotlinx.coroutines.CoroutineScope


@Composable
fun rememberMainAppState(
    windowSizeClass: WindowSizeClass,
    navController: NavHostController = rememberNavController(),
    coroutineScope: CoroutineScope = rememberCoroutineScope(),
): MainAppState {
//    var isAuthenticationRequired by remember {
//        mutableStateOf(false)
//    }
    return remember(
        navController,
        coroutineScope,
    ) {
        MainAppState(windowSizeClass, navController)
    }
}

@Stable
class MainAppState(
    private val windowSizeClass: WindowSizeClass,
    private val navController: NavHostController,
) {
    /**
     * The raw route for the current navigation destination
     */
    val currentDestination: NavDestination?
        @Composable get() = navController
            .currentBackStackEntryAsState().value?.destination

    private val isAtTopLevelRoute: Boolean
        @Composable get() = when (currentDestination?.route) {
            RouteDestination.Home.route -> true
            else -> false
        }

    val isLargeDevice: Boolean =
        windowSizeClass.heightSizeClass == WindowHeightSizeClass.Medium
                || windowSizeClass.heightSizeClass == WindowHeightSizeClass.Expanded

    val shouldShowMainAppBar: Boolean
        @Composable get() = isAtTopLevelRoute

    val shouldShowBottomBar: Boolean
        get() = windowSizeClass.widthSizeClass == WindowWidthSizeClass.Compact

    val shouldShowNavRail: Boolean
        @Composable get() = !shouldShowBottomBar && isAtTopLevelRoute


}

