package app.logdate.ui.watch

import androidx.compose.runtime.Composable

enum class WatchSetupProvider(
    val packageName: String,
) {
    PIXEL("com.google.android.apps.wear.companion"),
    GALAXY("com.samsung.android.app.watchmanager"),
}

/** Opens the selected manufacturer's companion, or its official store listing. */
@Composable
expect fun rememberWatchSetupLauncher(): ((WatchSetupProvider) -> Boolean)?
