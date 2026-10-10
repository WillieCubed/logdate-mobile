package app.logdate

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Tests [WearPlayTrack], which turns the track the phone publishes to into Google Play's name for the
 * same track of the Wear OS form factor.
 */
class WearPlayTrackTest {
    @Test
    fun `internal testing is the qa track of the Wear form factor`() {
        assertEquals("wear:qa", WearPlayTrack.forTrack("internal"))
    }

    @Test
    fun `beta and production keep their names`() {
        assertEquals("wear:beta", WearPlayTrack.forTrack("beta"))
        assertEquals("wear:production", WearPlayTrack.forTrack("production"))
    }

    @Test
    fun `a custom closed testing track keeps its own name`() {
        assertEquals("wear:friends", WearPlayTrack.forTrack("friends"))
    }
}
