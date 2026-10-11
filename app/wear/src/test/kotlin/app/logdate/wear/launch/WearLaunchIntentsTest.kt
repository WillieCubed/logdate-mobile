package app.logdate.wear.launch

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

class WearLaunchIntentsTest {
    @Test
    fun `the routes in the intents open the recorder and a moment`() {
        val noteId = Uuid.random()

        assertEquals(WearLaunchRequest.Record, WearLaunchRequest.parse(WearLaunchIntents.ROUTE_RECORD))
        assertEquals(WearLaunchRequest.Moment(noteId), WearLaunchRequest.parse(WearLaunchIntents.ROUTE_MOMENT, noteId.toString()))
    }
}
