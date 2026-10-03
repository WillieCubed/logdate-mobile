package app.logdate.wear.presentation.navigation

import androidx.navigation3.runtime.NavKey

/**
 * The screen a launch opens, from the `tile_route` extra that tiles and complications send. Home is
 * the recorder, so recording routes, unknown routes and routes to removed screens open it.
 */
internal fun wearRouteFor(tileRoute: String?): NavKey =
    when (tileRoute) {
        "memories" -> WearVoiceMemoriesRoute
        "mood" -> WearMoodCheckInRoute
        "quick_text" -> WearQuickTextRoute
        "timeline" -> WearTimelineRoute
        "remote_camera" -> WearRemoteCameraRoute
        "settings" -> WearSettingsRoute
        else -> WearHomeRoute
    }
