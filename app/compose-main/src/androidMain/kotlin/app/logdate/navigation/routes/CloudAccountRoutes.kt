package app.logdate.navigation.routes

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Navigation routes for the LogDate Cloud account setup flow.
 * 
 * These routes define the different screens in the account setup process.
 */

/**
 * Route for the initial cloud account introduction screen.
 * 
 * This screen explains the benefits of LogDate Cloud and initiates the setup process.
 * 
 * @property isFromOnboarding Whether this flow was started from the onboarding flow.
 * This affects whether a "Skip" option is shown.
 */
@Serializable
data class CloudAccountIntroRoute(
    val isFromOnboarding: Boolean = false
) : NavKey

/**
 * Route for the username selection screen.
 * 
 * This screen allows users to choose a unique username for their cloud account.
 */
@Serializable
data object UsernameSelectionRoute : NavKey

/**
 * Route for the display name selection screen.
 * 
 * This screen allows users to set their display name for their cloud account.
 */
@Serializable
data object DisplayNameSelectionRoute : NavKey

/**
 * Route for the passkey creation screen.
 * 
 * This screen guides the user through creating a passkey for secure authentication.
 */
@Serializable
data object PasskeyCreationRoute : NavKey

/**
 * Route for the account creation completion screen.
 * 
 * This screen shows when the account has been successfully created.
 */
@Serializable
data object AccountCreationCompletionRoute : NavKey