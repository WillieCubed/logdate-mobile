package app.logdate.feature.core.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

/**
 * The starting route of the app.
 *
 * This route should be used as a starting destination while the client is initializing resources
 * to navigate to the actual starting destination.
 *
 * @see navigateToStart
 * @see landingDestination
 */
@Serializable
data object BaseRoute

/**
 * Navigates to the starting route of the app.
 */
fun NavController.navigateToStart() = navigate(BaseRoute)

/**
 * Stub destination for the base route.
 */
fun NavGraphBuilder.landingDestination() {
    composable<BaseRoute> {
        // No-op
    }
}