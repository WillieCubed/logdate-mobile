package app.logdate.navigation.routes

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import app.logdate.feature.location.timeline.ui.LocationTimelineScreen
import app.logdate.navigation.MainAppNavigator
import app.logdate.navigation.routes.core.LocationRoute
import app.logdate.navigation.routes.core.switchToTab
import app.logdate.navigation.scenes.HomeScene
import app.logdate.navigation.scenes.HomeTab
import kotlin.uuid.Uuid

fun MainAppNavigator.openLocationTimeline() {
    switchToTab(HomeTab.LOCATION)
}

fun EntryProviderScope<NavKey>.locationRoutes(onOpenNote: (Uuid) -> Unit) {
    routeEntry<LocationRoute>(
        metadata = HomeScene.homeScene(),
    ) { _ ->
        LocationTimelineScreen(onOpenNote = onOpenNote)
    }
}
