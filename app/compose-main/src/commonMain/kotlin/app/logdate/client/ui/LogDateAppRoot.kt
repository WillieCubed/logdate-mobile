@file:OptIn(ExperimentalSharedTransitionApi::class)

package app.logdate.client.ui

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.compose.rememberNavController
import app.logdate.client.ui.navigation.LogDateNavHost
import app.logdate.feature.core.GlobalAppUiLoadedState
import app.logdate.feature.core.main.navigateHome
import app.logdate.feature.core.requiresUnlock
import app.logdate.feature.onboarding.navigation.startOnboarding
import app.logdate.ui.LocalSharedTransitionScope
import app.logdate.ui.audio.AudioPlaybackProvider
import app.logdate.ui.theme.LogDateTheme


@Composable
fun LogDateAppRoot(
    appUiState: GlobalAppUiLoadedState,
    onShowUnlockPrompt: () -> Unit,
) {
    val navController = rememberNavController()
    LaunchedEffect(appUiState) {
        if (!appUiState.isOnboarded) {
//        // Ensure that onboarding is completed before proceeding
            navController.startOnboarding()
            return@LaunchedEffect
        }
        if (appUiState.requiresUnlock) {
            onShowUnlockPrompt()
            return@LaunchedEffect
        }
        navController.navigateHome()
    }

    LogDateTheme {
        SharedTransitionLayout {
            CompositionLocalProvider(
                LocalSharedTransitionScope provides this,
            ) {
                LogDateNavHost(
                    navController = navController,
               )
            }
        }
    }
}