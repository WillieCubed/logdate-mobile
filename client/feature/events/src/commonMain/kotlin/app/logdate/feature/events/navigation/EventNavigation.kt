package app.logdate.feature.events.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import androidx.navigation3.runtime.NavKey
import app.logdate.feature.events.ui.EventDetailScreen
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

/**
 * Route for viewing or editing an event by id.
 */
@Serializable
data class EventDetailRoute(
    val eventId: String,
) : NavKey {
    constructor(eventId: Uuid) : this(eventId.toString())
}

fun NavController.navigateToEventDetail(eventId: Uuid) {
    navigate(EventDetailRoute(eventId))
}

fun NavGraphBuilder.eventDetailRoute(onGoBack: () -> Unit) {
    composable<EventDetailRoute> { backStackEntry ->
        val route = backStackEntry.toRoute<EventDetailRoute>()
        val eventId = Uuid.parse(route.eventId)
        EventDetailScreen(
            eventId = eventId,
            onGoBack = onGoBack,
        )
    }
}
