package app.logdate.screenshots.root

import androidx.compose.runtime.Composable
import app.logdate.client.MainActivityUiRoot
import app.logdate.client.database.DatabaseStartupState
import app.logdate.feature.core.GlobalAppUiLoadedState
import app.logdate.feature.core.settings.updates.AppUpdateStatus
import app.logdate.feature.core.settings.updates.AppUpdateUiState
import app.logdate.navigation.MainAppNavigator
import app.logdate.navigation.MainNavigationRoot
import app.logdate.navigation.routes.core.NavigationStart
import app.logdate.navigation.routes.core.TimelineListRoute
import app.logdate.screenshots.common.ScreenshotPreviewMatrix
import app.logdate.screenshots.common.ScreenshotTheme
import com.android.tools.screenshot.PreviewTest

@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun NavigationStartRoute() {
    ScreenshotTheme {
        MainNavigationRoot(
            mainAppNavigator = MainAppNavigator(initialRoute = NavigationStart),
        )
    }
}

@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun MainActivityRoot_DatabaseRecovery() {
    ScreenshotTheme {
        MainActivityUiRoot(
            appUiState = GlobalAppUiLoadedState(isLoaded = true, isOnline = true, isOnboarded = true),
            onShowUnlockPrompt = {},
            databaseStartupState =
                DatabaseStartupState.RecoveryRequired(
                    reason = "Encrypted database could not be decrypted with the current key.",
                    detail = "SQLiteException: file is not a database",
                ),
            mainAppNavigator = MainAppNavigator(initialRoute = NavigationStart),
        )
    }
}

@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun MainActivityRoot_HomeLoaded() {
    ScreenshotTheme {
        MainActivityUiRoot(
            appUiState = GlobalAppUiLoadedState(isLoaded = true, isOnline = true, isOnboarded = true),
            onShowUnlockPrompt = {},
            mainAppNavigator = MainAppNavigator(initialRoute = TimelineListRoute),
        )
    }
}

@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun MainActivityRoot_UpdateReadySnackbar() {
    ScreenshotTheme {
        MainActivityUiRoot(
            appUiState = GlobalAppUiLoadedState(isLoaded = true, isOnline = true, isOnboarded = true),
            onShowUnlockPrompt = {},
            appUpdateUiState =
                AppUpdateUiState(
                    status = AppUpdateStatus.Downloaded,
                    currentVersionName = "0.1.0",
                ),
            mainAppNavigator = MainAppNavigator(initialRoute = TimelineListRoute),
        )
    }
}
