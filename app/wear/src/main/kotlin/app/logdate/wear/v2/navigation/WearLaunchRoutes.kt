package app.logdate.wear.v2.navigation

import app.logdate.wear.launch.WearLaunchRequest

/**
 * The back stack the new watch app opens for this request. Only Home exists so far, so every
 * request opens Today; the change that adds a screen maps its requests here.
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
        is WearLaunchRequest.Moment,
        -> listOf(WearRoute.Home)
    }
