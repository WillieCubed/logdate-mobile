package app.logdate.feature.editor.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class RetainedEditorDimensionTest {
    @Test
    fun ordinaryFocusChangesUseTheSharedFractionWithoutRebasing() {
        val height = RetainedEditorDimension()
        assertEquals(120f, height.approach(120f, 800f, 0f))
        assertEquals(460f, height.approach(120f, 800f, 0.5f))
        assertEquals(290f, height.approach(120f, 800f, 0.25f))
    }

    @Test
    fun predictiveScrubCanReachTheOppositeEndpointBeforeCancellation() {
        val height = RetainedEditorDimension()
        height.approach(120f, 800f, 0.5f)
        assertEquals(460f, height.approach(1200f, 800f, 0.5f))
        assertEquals(800f, height.approach(1200f, 800f, 1f))
        assertEquals(800f, height.approach(1200f, 800f, 1f))
    }

    @Test
    fun resizeRetargetsFromDisplayedWidthAndReachesTheNewViewport() {
        val width = RetainedEditorDimension()
        assertEquals(344f, width.approach(328f, 360f, 0.5f))
        assertEquals(344f, width.approach(328f, 600f, 0.5f))
        assertEquals(472f, width.approach(328f, 600f, 0.75f))
        assertEquals(600f, width.approach(328f, 600f, 1f))
    }

    @Test
    fun multilineGrowthDuringReversalKeepsCurrentHeightAndEndsAtItsRealSourceHeight() {
        val height = RetainedEditorDimension()
        assertEquals(460f, height.approach(120f, 800f, 0.5f))
        assertEquals(460f, height.approach(1200f, 800f, 0.5f))
        assertEquals(830f, height.approach(1200f, 800f, 0.25f))
        assertEquals(1200f, height.approach(1200f, 800f, 0f))
    }

    @Test
    fun retargetedMotionCanReverseAgainWithoutAJump() {
        val position = RetainedEditorDimension()
        position.approach(160f, 0f, 0.5f)
        val visible = position.approach(240f, 0f, 0.5f)
        assertEquals(visible, position.approach(240f, 0f, 0.5f))
        assertEquals(240f, position.approach(240f, 0f, 0f))
    }
}
