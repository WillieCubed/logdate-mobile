package app.logdate.feature.timeline.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import app.logdate.feature.timeline.ui.TimelineRoute

const val TIMELINE_ROUTE = "timeline"

//fun NavController.navigateToTimeline(navOptions: NavOptions) = navigate(TIMELINE_ROUTE, navOptions)
//
//fun NavController.navigateToTimelineItem(itemId: String) = navigate(TIMELINE_ROUTE)

// TODO: Remove this once we have a proper navigation system
fun NavGraphBuilder.timelineRoute(onOpenTimelineItem: (id: String) -> Unit) {
    composable(
        route = TIMELINE_ROUTE,
        // TODO: Support deep link
    ) {
        TimelineRoute(onOpenTimelineItem = onOpenTimelineItem)
    }
}