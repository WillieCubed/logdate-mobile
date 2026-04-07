package app.logdate.navigation.routes

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import app.logdate.feature.events.ui.EventDetailScreen
import app.logdate.navigation.MainAppNavigator
import app.logdate.navigation.routes.core.EventDetailRoute
import kotlin.uuid.Uuid

/**
 * Pushes the event detail screen onto the navigation back stack.
 *
 * Use this from places that need to open an event by id — most commonly from the timeline day
 * detail panel when the user taps an event card. The corresponding route entry registered by
 * [eventRoutes] will pick it up and render [EventDetailScreen].
 *
 * @param eventId The id of the event to open.
 */
fun MainAppNavigator.openEventDetail(eventId: Uuid) {
    backStack.add(EventDetailRoute(eventId))
}

/**
 * Registers the route entry for [EventDetailRoute] inside an [EntryProviderScope].
 *
 * Call this once from the root navigation graph alongside the other feature `…Routes` builders
 * (e.g. `postcardRoutes`, `journalsRoutes`). It is idempotent within a given scope — call it
 * exactly once.
 *
 * @param onBack Invoked when the user taps the back button or after a successful delete.
 *   Typically wired to `mainAppNavigator::goBack`.
 */
@Suppress("ktlint:standard:function-naming")
fun EntryProviderScope<NavKey>.eventRoutes(onBack: () -> Unit) {
    routeEntry<EventDetailRoute> { route ->
        EventDetailScreen(
            eventId = route.eventId,
            onGoBack = onBack,
        )
    }
}
