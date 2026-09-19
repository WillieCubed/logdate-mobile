package app.logdate.wear.complication

import app.logdate.client.domain.streak.CampfireState
import app.logdate.client.domain.streak.FirePhase
import app.logdate.client.domain.streak.FireSize
import app.logdate.wear.R
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CampfireComplicationContentTest {
    private val fire =
        CampfireState(
            phase = FirePhase.BURNING,
            loggedToday = true,
            runDays = 12,
            size = FireSize.CAMPFIRE,
            longestRunDays = 30,
            totalDaysJournaled = 90,
            isRekindled = false,
        )

    @Test
    fun `a burning fire shows a flame and its day count`() {
        val content = fire.toComplicationContent()

        assertEquals(R.drawable.ic_campfire_flame, content.iconRes)
        assertEquals(12, content.runDays)
    }

    @Test
    fun `embers show the day count of the fire they can rekindle`() {
        val content = fire.copy(phase = FirePhase.EMBERS, loggedToday = false).toComplicationContent()

        assertEquals(R.drawable.ic_campfire_embers, content.iconRes)
        assertEquals(12, content.runDays)
    }

    @Test
    fun `a fire that went out shows bare logs and no count`() {
        val content = fire.copy(phase = FirePhase.OUT, runDays = 0, size = null).toComplicationContent()

        assertEquals(R.drawable.ic_campfire_logs, content.iconRes)
        assertNull(content.runDays)
    }

    @Test
    fun `an unlit fire shows bare logs and no count`() {
        val content = fire.copy(phase = FirePhase.UNLIT, runDays = 0, size = null).toComplicationContent()

        assertEquals(R.drawable.ic_campfire_logs, content.iconRes)
        assertNull(content.runDays)
    }
}
