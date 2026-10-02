package app.logdate.feature.rewind.ui

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RewindCoverPresentationTest {
    @Test fun phoneLeavesRoomToDiscoverTheNextStory() {
        val cover = rewindCoverLayout(411.dp, 650.dp)
        assertTrue(cover.framing + cover.height + 24.dp < 650.dp - 32.dp)
        assertTrue(cover.width <= 411.dp - 32.dp)
        assertTrue(cover.height >= 320.dp)
    }

    @Test fun wideCoverRetainsItsOwnBoundsInsteadOfStretchingAcrossThePanel() {
        val cover = rewindCoverLayout(1200.dp, 700.dp)
        assertTrue(cover.width <= 560.dp)
        assertTrue(cover.height / cover.width < 1.6f)
    }

    @Test fun focusedStoryUsesGentleSeparationAndScrollDepthIsBounded() {
        val focused = rewindCoverElevation(0f, false)
        val distant = rewindCoverElevation(1f, false)
        assertTrue(focused > distant)
        assertTrue(distant >= 1.dp)
        assertEquals(distant, rewindCoverElevation(-5f, false))
    }

    @Test fun reducedMotionKeepsStaticDepthWithoutScrollScaling() {
        val focused = rewindCoverElevation(0f, true)
        assertEquals(focused, rewindCoverElevation(1f, true))
        assertTrue(focused > 0.dp)
    }

    @Test fun depthIsASubtleSeparationRatherThanAnObjectEffect() {
        assertTrue(rewindCoverElevation(0f, false) <= 3.dp)
        assertTrue(rewindCoverElevation(0f, false) - rewindCoverElevation(1f, false) <= 1.dp)
    }
}
