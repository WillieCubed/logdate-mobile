package app.logdate.wear.presentation.navigation

import app.logdate.wear.launch.WearLaunchRequest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

class WearLaunchRoutesTest {
    @Test
    fun `requests for screens the current app has open those screens`() {
        assertEquals(WearVoiceMemoriesRoute, WearLaunchRequest.Memories.toV1Route())
        assertEquals(WearMoodCheckInRoute, WearLaunchRequest.MoodCheckIn.toV1Route())
        assertEquals(WearQuickTextRoute, WearLaunchRequest.QuickText.toV1Route())
        assertEquals(WearTimelineRoute, WearLaunchRequest.Timeline.toV1Route())
        assertEquals(WearRemoteCameraRoute, WearLaunchRequest.RemoteCamera.toV1Route())
        assertEquals(WearSettingsRoute, WearLaunchRequest.Settings.toV1Route())
    }

    @Test
    fun `requests the current app has no screen for open Home`() {
        assertEquals(WearHomeRoute, WearLaunchRequest.Home.toV1Route())
        assertEquals(WearHomeRoute, WearLaunchRequest.Record.toV1Route())
        assertEquals(WearHomeRoute, WearLaunchRequest.Mark.toV1Route())
        assertEquals(WearHomeRoute, WearLaunchRequest.OnThisDay.toV1Route())
        assertEquals(WearHomeRoute, WearLaunchRequest.Moment(Uuid.random()).toV1Route())
    }

    @Test
    fun `no request opens Home`() {
        val none: WearLaunchRequest? = null

        assertEquals(WearHomeRoute, none.toV1Route())
    }
}
