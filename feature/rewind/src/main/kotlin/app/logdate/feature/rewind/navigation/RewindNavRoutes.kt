package app.logdate.feature.rewind.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import app.logdate.feature.rewind.ui.RewindOpenCallback
import app.logdate.feature.rewind.ui.RewindOverviewScreen
import app.logdate.feature.rewind.ui.detail.RewindDetailScreen
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

/**
 * A route corresponding to the Rewind overview screen.
 *
 * Here, a user can view a list of past rewinds and select one to view.
 *
 * @see rewindOverviewRoute
 */
@Serializable
data object RewindOverviewRoute

/**
 * A route corresponding to the Rewind detail screen.
 *
 * Here, a user can interact with a Rewind.
 *
 * @see rewindDetailRoute
 */
@Serializable
data class RewindDetailRoute(
    val id: Uuid,
)

/**
 * Exposes the Rewind routes.
 */
fun NavGraphBuilder.rewindRoutes(
    onOpenRewind: RewindOpenCallback,
    onGoBack: () -> Unit,
) {
    rewindOverviewRoute(
        onOpenRewind = onOpenRewind
    )
    rewindDetailRoute(
        onExitRewind = onGoBack
    )
}


/**
 * Exposes a route for the Rewind overview screen.
 *
 * @param onOpenRewind A callback to be invoked when the user opens a Rewind.
 */
fun NavGraphBuilder.rewindOverviewRoute(
    onOpenRewind: RewindOpenCallback,
) {
    composable<RewindOverviewRoute> {
        RewindOverviewScreen(
            onOpenRewind = onOpenRewind,
        )
    }
}

/**
 * Exposes a route for the Rewind detail screen.
 *
 * This exposes the main Rewind experience.
 *
 * @param onExitRewind A callback to be invoked when the user exits the Rewind.
 */
fun NavGraphBuilder.rewindDetailRoute(
    onExitRewind: () -> Unit,
) {
    composable<RewindDetailRoute> {
        val routeData: RewindDetailRoute = it.toRoute()
        RewindDetailScreen(
            rewindId = routeData.id,
            onExitRewind = onExitRewind
        )
    }
}

/**
 * Navigates to the Rewind viewer.
 *
 * @param uid The unique identifier of the rewind to navigate to.
 */
fun NavController.navigateToRewind(uid: Uuid) {
    // Only open one Rewind at a time
    navigate(RewindDetailRoute(uid)) {
        launchSingleTop = true
    }
}

/**
 * Navigates to the Rewind overview screen.
 */
fun NavController.navigateToRewindsOverview() {
    navigate(RewindOverviewRoute)
}