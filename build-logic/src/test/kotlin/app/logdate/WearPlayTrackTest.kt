package app.logdate

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Tests [WearPlayTrack], which turns the track the phone publishes to into Google Play's name for the
 * same track of the Wear OS form factor.
 */
class WearPlayTrackTest {
    @Test
    fun `internal testing uses the registered Wear internal track`() {
        assertEquals("wear:internal", WearPlayTrack.forTrack("internal"))
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
