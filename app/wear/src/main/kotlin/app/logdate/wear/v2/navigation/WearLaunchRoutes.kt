package app.logdate.wear.v2.navigation

import app.logdate.wear.launch.WearLaunchRequest

/**
 * The back stack the new watch app opens for this request: the screen the request names above
 * Home, so going back lands on Today. A request whose screen does not exist yet opens Today.
 */
internal fun WearLaunchRequest?.toV2BackStack(): List<WearRoute> =
    when (this) {
        null,
        WearLaunchRequest.Home,
        WearLaunchRequest.Record,
        WearLaunchRequest.Memories,
        WearLaunchRequest.MoodCheckIn,
        WearLaunchRequest.QuickText,
        WearLaunchRequest.Timeline,
        WearLaunchRequest.RemoteCamera,
        WearLaunchRequest.Settings,
        WearLaunchRequest.Mark,
        WearLaunchRequest.OnThisDay,
        -> listOf(WearRoute.Home)
        is WearLaunchRequest.Moment -> listOf(WearRoute.Home, WearRoute.Moment(noteId.toString()))
    }
