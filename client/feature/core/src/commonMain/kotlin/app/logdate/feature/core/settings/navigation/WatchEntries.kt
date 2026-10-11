package app.logdate.feature.core.settings.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import app.logdate.feature.core.settings.ui.SettingsCategoryNavigationScreen
import app.logdate.feature.core.settings.ui.watch.WatchNotificationSettingsScreen
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
    onNavigateToNotifications: () -> Unit,
    onNavigateToTroubleshooting: () -> Unit,
    onNavigateToCategory: (NavKey) -> Unit = {},
    onNavigateToProfile: () -> Unit = {},
) {
    val categories =
        SettingsActivityCategories { section ->
            SettingsCategoryNavigationScreen(
                selectedSection = section,
                onNavigateTo = onNavigateToCategory,
                onBack = onBack,
                onNavigateToProfile = onNavigateToProfile,
                onNavigateToWatch = {},
            )
        }
    taggedEntry<WatchSettingsRoute>(metadata = settingsDetailMetadata(SettingsCategory.Watch, categories)) {
        WatchSettingsScreen(
            onBack = onBack,
            onNavigateToNotifications = onNavigateToNotifications,
            onNavigateToTroubleshooting = onNavigateToTroubleshooting,
        )
    }
    taggedEntry<WatchNotificationSettingsRoute>(metadata = settingsDetailMetadata(SettingsCategory.Watch, categories)) {
        val viewModel: WatchSettingsViewModel = koinViewModel()
        WatchNotificationSettingsScreen(
            onBack = onBack,
            viewModel = viewModel,
        )
    }
    taggedEntry<WatchTroubleshootingRoute>(metadata = settingsDetailMetadata(SettingsCategory.Watch, categories)) {
        val viewModel: WatchSettingsViewModel = koinViewModel()
        WatchTroubleshootingScreen(
            onBack = onBack,
            viewModel = viewModel,
        )
    }
}
