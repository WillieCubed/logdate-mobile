package app.logdate.feature.core.settings.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import app.logdate.feature.core.settings.ui.watch.WatchSettingsScreen
import app.logdate.feature.core.settings.ui.watch.WatchSettingsViewModel
import app.logdate.feature.core.settings.ui.watch.WatchTroubleshootingScreen
import app.logdate.ui.navigation.taggedEntry
import org.koin.compose.viewmodel.koinViewModel

/** Pushes the watch settings overview onto the back stack. */
fun NavBackStack<NavKey>.navigateToWatchSettings() {
    add(WatchSettingsRoute)
}

/**
 * Registers the Watch Settings overview and Troubleshooting entries. Only call this on
 * platforms where the watch DI module ([WatchSettingsViewModel] et al) is included — on iOS
 * and desktop the underlying `WatchSettingsViewModel` Koin binding is absent and resolving
 * it would crash.
 */
fun EntryProviderScope<NavKey>.watchEntries(
    onBack: () -> Unit,
    onNavigateToSync: () -> Unit,
    onNavigateToNotifications: () -> Unit,
    onNavigateToTroubleshooting: () -> Unit,
) {
    taggedEntry<WatchSettingsRoute> {
        WatchSettingsScreen(
            onBack = onBack,
            onNavigateToSync = onNavigateToSync,
            onNavigateToNotifications = onNavigateToNotifications,
            onNavigateToTroubleshooting = onNavigateToTroubleshooting,
        )
    }
    taggedEntry<WatchTroubleshootingRoute> {
        val viewModel: WatchSettingsViewModel = koinViewModel()
        WatchTroubleshootingScreen(
            onBack = onBack,
            viewModel = viewModel,
        )
    }
}
