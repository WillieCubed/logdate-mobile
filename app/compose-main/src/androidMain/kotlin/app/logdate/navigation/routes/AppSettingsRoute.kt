package app.logdate.navigation

import androidx.navigation3.runtime.EntryProviderBuilder
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entry
import app.logdate.feature.core.settings.ui.AccountSettingsScreen
import app.logdate.feature.core.settings.ui.DangerZoneSettingsScreen
import app.logdate.feature.core.settings.ui.DataSettingsScreen
import app.logdate.feature.core.settings.ui.PrivacySettingsScreen
import app.logdate.feature.core.settings.ui.SettingsOverviewScreen
import app.logdate.feature.core.settings.ui.SettingsScreen
import kotlin.uuid.Uuid

/**
 * Resets the app by safely clearing the back stack and navigating to the onboarding start screen.
 * This implementation ensures the backstack is never empty during the operation.
 */
fun MainAppNavigator.resetApp() {
    // Use the safelyClearBackstack method to ensure the backstack is never empty
    // Set OnboardingStart as the destination
    safelyClearBackstack(OnboardingStart)
    
    // Double-check that we have the onboarding start screen in the backstack
    if (!backStack.contains(OnboardingStart)) {
        backStack.add(OnboardingStart)
    }
}

/**
 * Extension function to open the main settings overview screen.
 * 
 * This provides a consistent way to navigate to the settings screen from anywhere in the app.
 */
fun MainAppNavigator.openSettings() {
    backStack.add(SettingsOverviewRoute)
}

/**
 * Opens the account management settings screen.
 */
fun MainAppNavigator.openAccountSettings() {
    backStack.add(AccountSettingsRoute)
}

/**
 * Opens the privacy and security settings screen.
 */
fun MainAppNavigator.openPrivacySettings() {
    backStack.add(PrivacySettingsRoute)
}

/**
 * Opens the data and storage settings screen.
 */
fun MainAppNavigator.openDataSettings() {
    backStack.add(DataSettingsRoute)
}

/**
 * Opens the danger zone settings screen with destructive actions.
 */
fun MainAppNavigator.openDangerZoneSettings() {
    backStack.add(DangerZoneSettingsRoute)
}


/**
 * Provides the navigation routes for app settings-related screens.
 *
 * @param onBack Callback to handle back navigation
 * @param onAppReset Callback to reset the app. This should clear the back stack and navigate to the onboarding start screen.
 * @param onNavigateToCloudAccountCreation Callback to navigate to cloud account creation screen
 * @param navigator The MainAppNavigator instance for navigating between settings screens
 */
fun EntryProviderBuilder<NavKey>.appSettingsRoutes(
    onBack: () -> Unit,
    onAppReset: () -> Unit,
    onNavigateToCloudAccountCreation: () -> Unit,
    navigator: MainAppNavigator,
) {
    // Main settings overview screen
    entry<SettingsOverviewRoute> { _ ->
        SettingsOverviewScreen(
            onBack = onBack,
            onNavigateToAccount = navigator::openAccountSettings,
            onNavigateToPrivacy = navigator::openPrivacySettings,
            onNavigateToData = navigator::openDataSettings,
            onNavigateToDangerZone = navigator::openDangerZoneSettings
        )
    }
    
    // Account management settings screen
    entry<AccountSettingsRoute> { _ ->
        AccountSettingsScreen(
            onBack = onBack,
            onNavigateToCloudAccountCreation = onNavigateToCloudAccountCreation
        )
    }
    
    // Privacy and security settings screen
    entry<PrivacySettingsRoute> { _ ->
        PrivacySettingsScreen(
            onBack = onBack
        )
    }
    
    // Data and storage settings screen
    entry<DataSettingsRoute> { _ ->
        DataSettingsScreen(
            onBack = onBack
        )
    }
    
    // Danger zone settings screen with destructive actions
    entry<DangerZoneSettingsRoute> { _ ->
        DangerZoneSettingsScreen(
            onBack = onBack,
            onAppReset = onAppReset
        )
    }
}