package app.logdate.wear.v2.surfaces

import org.junit.Test
import kotlin.test.assertEquals

class SurfaceComponentsTest {
    private val switch = RecordingSwitch()
    private val components =
        SurfaceComponents(switch, newAppOnly = listOf("NewTile"), currentAppOnly = listOf("OldTile", "OldComplication"))

    @Test
    fun `the new app offers its surfaces and retires the ones it replaces`() {
        components.apply(newAppOn = true)

        assertEquals(mapOf("NewTile" to true, "OldTile" to false, "OldComplication" to false), switch.states)
    }

    @Test
    fun `the current app offers its own surfaces and hides the new ones`() {
        components.apply(newAppOn = false)

        assertEquals(mapOf("NewTile" to false, "OldTile" to true, "OldComplication" to true), switch.states)
    }

    @Test
    fun `the spec retires Entry Count, Mood and the Quick Capture tile`() {
        assertEquals(
            listOf(
                "app.logdate.wear.complication.EntryCountComplicationService",
                "app.logdate.wear.complication.MoodComplicationService",
                "app.logdate.wear.tile.QuickCaptureTileService",
            ),
            SurfaceCatalog.CURRENT_APP_ONLY,
        )
    }
}
