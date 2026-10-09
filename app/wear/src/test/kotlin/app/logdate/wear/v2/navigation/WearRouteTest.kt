package app.logdate.wear.v2.navigation

import app.logdate.wear.launch.WearLaunchRequest
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

class WearRouteTest {
    @Test
    fun `every route survives being saved and restored`() {
        val routes =
            listOf(
                WearRoute.Home,
                WearRoute.Moment(id = Uuid.random().toString()),
                WearRoute.CaptureSheet,
                WearRoute.PlayOn,
                WearRoute.Archive,
                WearRoute.PhoneCamera,
            )

        routes.forEach { route ->
            val saved = Json.encodeToString(serializer<WearRoute>(), route)
            assertEquals(route, Json.decodeFromString(serializer<WearRoute>(), saved))
        }
    }

    @Test
    fun `every launch request opens Today until its own screen exists`() {
        val requests =
            listOf(
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
                WearLaunchRequest.Moment(Uuid.random()),
            )

        requests.forEach { request ->
            assertEquals(listOf<WearRoute>(WearRoute.Home), request.toV2BackStack(), "for $request")
        }
    }
}
