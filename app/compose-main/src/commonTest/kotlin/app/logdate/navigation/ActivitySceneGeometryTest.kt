package app.logdate.navigation

import androidx.compose.ui.unit.dp
import app.logdate.ui.foldable.FoldableHingeBounds
import app.logdate.ui.foldable.FoldableHingeInfo
import app.logdate.ui.foldable.FoldableHingeOrientation
import app.logdate.ui.foldable.FoldableHingeState
import app.logdate.ui.foldable.FoldableLayoutInfo
import app.logdate.ui.foldable.FoldableOcclusionType
import app.logdate.ui.foldable.FoldableSplitLayout
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ActivitySceneGeometryTest {
    @Test
    fun narrowBookRetainsPhysicalHingeWhenTwoPanesCannotFit() {
        val geometry = activitySceneGeometry(800.dp, 900.dp, book(), 1f)
        assertIs<FoldableSplitLayout.Vertical>(geometry.split)
        assertFalse(geometry.supportsRelatedPanes)
    }

    @Test
    fun largeTextRetainsPhysicalHingeForSingleRegionFallback() {
        val geometry = activitySceneGeometry(800.dp, 900.dp, book(), 2f)
        assertIs<FoldableSplitLayout.Vertical>(geometry.split)
        assertFalse(geometry.supportsRelatedPanes)
    }

    @Test
    fun shortLandscapeDoesNotForceRelatedPanes() {
        assertFalse(activitySceneGeometry(900.dp, 400.dp, FoldableLayoutInfo(), 1f).supportsRelatedPanes)
    }

    @Test
    fun usefulWideBoundsSupportRelatedPanes() {
        assertTrue(activitySceneGeometry(1280.dp, 900.dp, FoldableLayoutInfo(), 1f).supportsRelatedPanes)
    }

    private fun book() =
        FoldableLayoutInfo(
            isFoldable = true,
            hinge =
                FoldableHingeInfo(
                    orientation = FoldableHingeOrientation.Vertical,
                    state = FoldableHingeState.HalfOpened,
                    occlusionType = FoldableOcclusionType.Full,
                    bounds = FoldableHingeBounds(500.dp, 0.dp, 520.dp, 900.dp, 20.dp, 900.dp),
                    isSeparating = true,
                ),
        )
}
