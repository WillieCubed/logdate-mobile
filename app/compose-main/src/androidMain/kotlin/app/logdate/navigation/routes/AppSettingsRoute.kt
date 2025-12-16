package app.logdate.navigation.routes

import androidx.navigation3.runtime.EntryProviderBuilder
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entry
import app.logdate.feature.core.profile.ui.ProfileScreen
import app.logdate.feature.core.settings.ui.AccountSettingsScreen
import app.logdate.feature.core.settings.ui.BirthdaySettingsScreen
import app.logdate.feature.core.settings.ui.DangerZoneSettingsScreen
import app.logdate.feature.core.settings.ui.DataSettingsScreen
import app.logdate.feature.core.settings.ui.LocationSettingsScreen
import app.logdate.feature.core.settings.ui.PrivacySettingsScreen
import app.logdate.feature.core.settings.ui.SettingsOverviewScreen
import app.logdate.navigation.MainAppNavigator
import app.logdate.navigation.routes.ProfileRoute
import app.logdate.navigation.routes.core.AccountSettingsRoute
import app.logdate.navigation.routes.core.BirthdaySettingsRoute
import app.logdate.navigation.routes.core.DangerZoneSettingsRoute
import app.logdate.navigation.routes.core.DataSettingsRoute
import app.logdate.navigation.routes.core.LocationSettingsRoute
import app.logdate.navigation.routes.core.OnboardingStart
import app.logdate.navigation.routes.core.PrivacySettingsRoute
import app.logdate.navigation.routes.core.SettingsOverviewRoute

/**
 * Resets the app by safely clearing the back stack and navigating to the onboarding start screen.
 * This implementation ensures the backstack is never empty during the operation.
 */
fun MainAppNavigator.resetApp() {
    // Make sure the onboarding start route is in the backstack
    if (!backStack.contains(OnboardingStart)) {
        backStack.add(OnboardingStart)
    }
    
    // Navigate to onboarding start, keeping it as the first (and only) entry in the backstack
    safelyPopBackstackTo(OnboardingStart, keepFirst = true)
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
 * Opens the profile screen with Material 3 Expressive design.
 */
fun MainAppNavigator.openProfile() {
    backStack.add(ProfileRoute)
}

/**
 * Opens the full-screen birthday selector screen.
 */
fun MainAppNavigator.openBirthdaySettings() {
    backStack.add(BirthdaySettingsRoute)
}

/**
 * Opens the privacy and security settings screen.
 */
fun MainAppNavigator.openPrivacySettings() {
    backStack.add(PrivacySettingsRoute)
}

/**
 * Opens the location settings screen.
 */
fun MainAppNavigator.openLocationSettings() {
    backStack.add(LocationSettingsRoute)
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
 * @param onNavigateToProfile Callback to navigate to profile screen
 * @param onNavigateToAccount Callback to navigate to account settings
 * @param onNavigateToPrivacy Callback to navigate to privacy settings
 * @param onNavigateToData Callback to navigate to data settings
 * @param onNavigateToDangerZone Callback to navigate to danger zone settings
 * @param onNavigateToLocation Callback to navigate to location settings
 * @param onNavigateToBirthdaySettings Callback to navigate to the birthday settings screen
 */
fun EntryProviderBuilder<NavKey>.appSettingsRoutes(
    onBack: () -> Unit,
    onAppReset: () -> Unit,
    onNavigateToCloudAccountCreation: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToAccount: () -> Unit,
    onNavigateToPrivacy: () -> Unit,
    onNavigateToData: () -> Unit,
    onNavigateToDangerZone: () -> Unit,
    onNavigateToLocation: () -> Unit,
    onNavigateToBirthdaySettings: () -> Unit,
) {
    // Main settings overview screen
    entry<SettingsOverviewRoute>() { _ ->
        SettingsOverviewScreen(
            onBack = onBack,
            onNavigateToProfile = onNavigateToProfile,
            onNavigateToAccount = onNavigateToAccount,
            onNavigateToPrivacy = onNavigateToPrivacy,
            onNavigateToData = onNavigateToData,
            onNavigateToDangerZone = onNavigateToDangerZone,
            onNavigateToDevices = onNavigateToData, // TODO: Remove unnecessary separate devices screen
            onNavigateToLocation = onNavigateToLocation,
            // TODO: Determine selected detail and two-pane mode based on current navigation context
            selectedDetail = null,
            isInTwoPaneMode = false
        )
    }
    
    // Profile screen with Material 3 Expressive design
    entry<ProfileRoute>() { _ ->
        ProfileScreen(
            onBack = onBack
        )
    }
    
    // Account management settings screen
    entry<AccountSettingsRoute>() { _ ->
        AccountSettingsScreen(
            onBack = onBack,
            onNavigateToCloudAccountCreation = onNavigateToCloudAccountCreation,
            onNavigateToBirthdaySettings = onNavigateToBirthdaySettings
        )
    }
    
    // Birthday settings screen
    entry<BirthdaySettingsRoute>() { _ ->
        BirthdaySettingsScreen(
            onBack = onBack
        )
    }
    
    // Privacy and security settings screen
    entry<PrivacySettingsRoute>() { _ ->
        PrivacySettingsScreen(
            onBack = onBack,
            onNavigateToLocationSettings = onNavigateToLocation
        )
    }
    
    // Data and storage settings screen
    entry<DataSettingsRoute>() { _ ->
        DataSettingsScreen(
            onBack = onBack
        )
    }
    
    // Danger zone settings screen with destructive actions
    entry<DangerZoneSettingsRoute>() { _ ->
        DangerZoneSettingsScreen(
            onBack = onBack,
            onAppReset = onAppReset
        )
    }
    
    // Location settings screen
    entry<LocationSettingsRoute>() { _ ->
        LocationSettingsScreen(
            onBack = onBack,
        )
    }
}