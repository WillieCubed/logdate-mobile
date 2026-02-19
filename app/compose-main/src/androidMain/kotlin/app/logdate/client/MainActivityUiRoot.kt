package app.logdate.client

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation3.runtime.NavKey
import app.logdate.feature.core.GlobalAppUiLoadedState
import app.logdate.feature.core.requiresUnlock
import app.logdate.navigation.MainAppNavigator
import app.logdate.navigation.MainNavigationRoot
import app.logdate.navigation.rememberMainAppNavigator
import app.logdate.navigation.routes.core.NavigationStart
import app.logdate.navigation.routes.core.navigateHomeFromLaunch
import app.logdate.navigation.routes.startOnboarding
import app.logdate.ui.LocalSharedTransitionScope
import app.logdate.ui.theme.LogDateTheme

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun MainActivityUiRoot(
    appUiState: GlobalAppUiLoadedState,
    onShowUnlockPrompt: () -> Unit,
    pendingNavKey: NavKey? = null,
    onDeepLinkHandled: () -> Unit = {},
    mainAppNavigator: MainAppNavigator = rememberMainAppNavigator(initialRoute = NavigationStart),
) {
    var hasRequestedUnlock by remember { mutableStateOf(false) }
    var hasHandledInitialNavigation by remember { mutableStateOf(false) }

    LaunchedEffect(appUiState.isOnboarded, appUiState.requiresUnlock, pendingNavKey) {
        if (!appUiState.isOnboarded) {
//        // Ensure that onboarding is completed before proceeding
            mainAppNavigator.startOnboarding()
            return@LaunchedEffect
        }
        if (appUiState.requiresUnlock) {
            if (!hasRequestedUnlock) {
                hasRequestedUnlock = true
                onShowUnlockPrompt()
            }
            return@LaunchedEffect
        }
        hasRequestedUnlock = false
        if (pendingNavKey != null) {
            if (!mainAppNavigator.backStack.contains(pendingNavKey)) {
                mainAppNavigator.navigateHomeFromLaunch()
                mainAppNavigator.backStack.add(pendingNavKey)
            }
            onDeepLinkHandled()
            hasHandledInitialNavigation = true
        } else {
            if (!hasHandledInitialNavigation) {
                mainAppNavigator.navigateHomeFromLaunch()
                hasHandledInitialNavigation = true
            }
        }
    }

    LogDateTheme {
        SharedTransitionLayout {
            CompositionLocalProvider(
                LocalSharedTransitionScope provides this,
            ) {
                MainNavigationRoot(mainAppNavigator)
            }
        }
    }
}
