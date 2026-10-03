package app.logdate.wear.presentation.navigation

import org.junit.Test
import kotlin.test.assertEquals

class WearLaunchRoutesTest {
    @Test
    fun `routes to the removed Rewind and Health screens open Home`() {
        assertEquals(WearHomeRoute, wearRouteFor("rewind"))
        assertEquals(WearHomeRoute, wearRouteFor("health"))
    }

    @Test
    fun `recording routes open Home, which is the recorder`() {
        assertEquals(WearHomeRoute, wearRouteFor("quick_record"))
        assertEquals(WearHomeRoute, wearRouteFor("voice_note"))
    }

    @Test
    fun `the other tile routes open their screens`() {
        assertEquals(WearVoiceMemoriesRoute, wearRouteFor("memories"))
        assertEquals(WearMoodCheckInRoute, wearRouteFor("mood"))
        assertEquals(WearQuickTextRoute, wearRouteFor("quick_text"))
        assertEquals(WearTimelineRoute, wearRouteFor("timeline"))
        assertEquals(WearRemoteCameraRoute, wearRouteFor("remote_camera"))
        assertEquals(WearSettingsRoute, wearRouteFor("settings"))
    }

    @Test
    fun `a missing or unknown route opens Home`() {
        assertEquals(WearHomeRoute, wearRouteFor(null))
        assertEquals(WearHomeRoute, wearRouteFor("unknown"))
    }
}
