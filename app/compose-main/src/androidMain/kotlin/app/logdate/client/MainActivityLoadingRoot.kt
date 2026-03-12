@file:Suppress("ktlint:standard:function-naming")

package app.logdate.client

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import app.logdate.client.sharing.NoOpSharingLauncher
import app.logdate.navigation.LocalSharedTransitionScope
import app.logdate.navigation.MainNavigationRoot
import app.logdate.navigation.rememberMainAppNavigator
import app.logdate.navigation.routes.core.NavigationStart
import app.logdate.ui.theme.LogDateTheme

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun MainActivityLoadingRoot() {
    LogDateTheme {
        SharedTransitionLayout {
            CompositionLocalProvider(
                LocalSharedTransitionScope provides this,
            ) {
                MainNavigationRoot(
                    mainAppNavigator = rememberMainAppNavigator(initialRoute = NavigationStart),
                    sharingLauncher = NoOpSharingLauncher,
                )
            }
        }
    }
}
