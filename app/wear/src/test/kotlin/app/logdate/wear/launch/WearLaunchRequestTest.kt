package app.logdate.wear.launch

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.uuid.Uuid

class WearLaunchRequestTest {
    @Test
    fun `routes that today's tiles and complications send still parse`() {
        assertEquals(WearLaunchRequest.Record, WearLaunchRequest.parse("quick_record"))
        assertEquals(WearLaunchRequest.Record, WearLaunchRequest.parse("voice_note"))
        assertEquals(WearLaunchRequest.Memories, WearLaunchRequest.parse("memories"))
        assertEquals(WearLaunchRequest.MoodCheckIn, WearLaunchRequest.parse("mood"))
        assertEquals(WearLaunchRequest.QuickText, WearLaunchRequest.parse("quick_text"))
        assertEquals(WearLaunchRequest.Timeline, WearLaunchRequest.parse("timeline"))
        assertEquals(WearLaunchRequest.RemoteCamera, WearLaunchRequest.parse("remote_camera"))
        assertEquals(WearLaunchRequest.Settings, WearLaunchRequest.parse("settings"))
    }

    @Test
    fun `routes to the removed Rewind and Health screens open Home`() {
        assertEquals(WearLaunchRequest.Home, WearLaunchRequest.parse("rewind"))
        assertEquals(WearLaunchRequest.Home, WearLaunchRequest.parse("health"))
    }

    @Test
    fun `the new actions parse`() {
        assertEquals(WearLaunchRequest.Record, WearLaunchRequest.parse("record"))
        assertEquals(WearLaunchRequest.Mark, WearLaunchRequest.parse("mark"))
        assertEquals(WearLaunchRequest.OnThisDay, WearLaunchRequest.parse("on_this_day"))
    }

    @Test
    fun `a moment request carries its note`() {
        val noteId = Uuid.random()

        assertEquals(WearLaunchRequest.Moment(noteId), WearLaunchRequest.parse("moment", noteId.toString()))
    }

    @Test
    fun `a moment request without a valid note is no request`() {
        assertNull(WearLaunchRequest.parse("moment"))
        assertNull(WearLaunchRequest.parse("moment", "not-a-note-id"))
    }

    @Test
    fun `a missing or unknown route is no request`() {
        assertNull(WearLaunchRequest.parse(null))
        assertNull(WearLaunchRequest.parse("unknown"))
    }
}
