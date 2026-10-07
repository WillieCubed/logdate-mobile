package app.logdate.ui.workspace

import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.logdate.ui.foldable.FoldableHingeBounds
import app.logdate.ui.foldable.FoldableHingeInfo
import app.logdate.ui.foldable.FoldableHingeOrientation
import app.logdate.ui.foldable.FoldableHingeState
import app.logdate.ui.foldable.FoldableLayoutInfo
import app.logdate.ui.foldable.FoldableOcclusionType
import kotlin.test.Test
import kotlin.test.assertEquals

class WorkspacePlaybackBoundsTest {
    @Test fun phoneUsesTheWholeViewport() {
        assertEquals(PanelBounds(0.dp, 0.dp, 411.dp, 891.dp), resolveWorkspacePlaybackBounds(411.dp, 891.dp))
    }

    @Test fun tabletCentersTheStoryWithoutWorkspaceGutters() {
        assertEquals(PanelBounds(40.dp, 0.dp, 1200.dp, 800.dp), resolveWorkspacePlaybackBounds(1280.dp, 800.dp))
    }

    @Test fun landscapeKeepsAllAvailableHeight() {
        assertEquals(PanelBounds(0.dp, 0.dp, 891.dp, 411.dp), resolveWorkspacePlaybackBounds(891.dp, 411.dp))
    }

    @Test fun bookChoosesTheLargerPhysicalRegionEvenInRtl() {
        val fold = fold(FoldableHingeOrientation.Vertical, 300, 0, 320, 800)
        assertEquals(
            PanelBounds(320.dp, 0.dp, 880.dp, 800.dp),
            resolveWorkspacePlaybackBounds(1200.dp, 800.dp, fold, layoutDirection = LayoutDirection.Rtl),
        )
    }

    @Test fun equalBookRegionsChooseTheLeadingSide() {
        val fold = fold(FoldableHingeOrientation.Vertical, 590, 0, 610, 800)
        assertEquals(PanelBounds(0.dp, 0.dp, 590.dp, 800.dp), resolveWorkspacePlaybackBounds(1200.dp, 800.dp, fold))
        assertEquals(
            PanelBounds(610.dp, 0.dp, 590.dp, 800.dp),
            resolveWorkspacePlaybackBounds(1200.dp, 800.dp, fold, layoutDirection = LayoutDirection.Rtl),
        )
    }

    @Test fun narrowBookUsesTheWholeWidthInBothDirections() {
        val fold = fold(FoldableHingeOrientation.Vertical, 200, 0, 220, 900)
        for (direction in listOf(LayoutDirection.Ltr, LayoutDirection.Rtl)) {
            assertEquals(
                PanelBounds(0.dp, 0.dp, 420.dp, 900.dp),
                resolveWorkspacePlaybackBounds(420.dp, 900.dp, fold, layoutDirection = direction),
            )
        }
    }

    @Test fun mobileBookDoesNotReducePlaybackBelowAReadablePhoneWidth() {
        val fold = fold(FoldableHingeOrientation.Vertical, 327, 0, 347, 900)
        assertEquals(PanelBounds(0.dp, 0.dp, 674.dp, 900.dp), resolveWorkspacePlaybackBounds(674.dp, 900.dp, fold))
    }

    @Test fun unfoldedMobileBookRetainsItsImmersiveWidth() {
        val fold = fold(FoldableHingeOrientation.Vertical, 410, 0, 430, 900)
        assertEquals(PanelBounds(0.dp, 0.dp, 840.dp, 900.dp), resolveWorkspacePlaybackBounds(840.dp, 900.dp, fold))
    }

    @Test fun tabletopUsesTheUpperRegionWhenItCanFitPlayback() {
        val fold = fold(FoldableHingeOrientation.Horizontal, 0, 300, 1000, 320)
        assertEquals(PanelBounds(0.dp, 0.dp, 1000.dp, 300.dp), resolveWorkspacePlaybackBounds(1000.dp, 900.dp, fold))
    }

    @Test fun shortTabletopUsesTheLargerLowerRegion() {
        val fold = fold(FoldableHingeOrientation.Horizontal, 0, 180, 1000, 200)
        assertEquals(PanelBounds(0.dp, 200.dp, 1000.dp, 700.dp), resolveWorkspacePlaybackBounds(1000.dp, 900.dp, fold))
    }

    @Test fun hingeCoordinatesAreTranslatedToThePlaybackOrigin() {
        val fold = fold(FoldableHingeOrientation.Vertical, 400, 0, 420, 1000)
        assertEquals(
            PanelBounds(320.dp, 0.dp, 680.dp, 800.dp),
            resolveWorkspacePlaybackBounds(1000.dp, 800.dp, fold, origin = DpOffset(100.dp, 100.dp)),
        )
    }

    @Test fun hingeOutsideTheViewportDoesNotShrinkPlayback() {
        val fold = fold(FoldableHingeOrientation.Vertical, 1200, 0, 1220, 800)
        assertEquals(PanelBounds(0.dp, 0.dp, 1000.dp, 800.dp), resolveWorkspacePlaybackBounds(1000.dp, 800.dp, fold))
    }

    @Test fun aHingeOutsideThePerpendicularAxisDoesNotSplitPlayback() {
        val vertical = fold(FoldableHingeOrientation.Vertical, 490, 900, 510, 1000)
        val horizontal = fold(FoldableHingeOrientation.Horizontal, 1100, 390, 1200, 410)
        assertEquals(PanelBounds(0.dp, 0.dp, 1000.dp, 800.dp), resolveWorkspacePlaybackBounds(1000.dp, 800.dp, vertical))
        assertEquals(PanelBounds(0.dp, 0.dp, 1000.dp, 800.dp), resolveWorkspacePlaybackBounds(1000.dp, 800.dp, horizontal))
    }

    @Test fun hingeClippedAtTheViewportEdgeKeepsTheRemainingRegion() {
        val fold = fold(FoldableHingeOrientation.Vertical, -20, 0, 20, 800)
        assertEquals(PanelBounds(20.dp, 0.dp, 980.dp, 800.dp), resolveWorkspacePlaybackBounds(1000.dp, 800.dp, fold))
    }

    @Test fun aNonSeparatingCreaseKeepsContinuousPlayback() {
        val fold = fold(FoldableHingeOrientation.Vertical, 590, 0, 610, 800)
        val crease = fold.copy(hinge = fold.hinge!!.copy(isSeparating = false, occlusionType = FoldableOcclusionType.None))
        assertEquals(PanelBounds(40.dp, 0.dp, 1200.dp, 800.dp), resolveWorkspacePlaybackBounds(1280.dp, 800.dp, crease))
    }

    private fun fold(
        orientation: FoldableHingeOrientation,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
    ) = FoldableLayoutInfo(
        isFoldable = true,
        hinge =
            FoldableHingeInfo(
                orientation,
                FoldableHingeState.HalfOpened,
                FoldableOcclusionType.Full,
                FoldableHingeBounds(left.dp, top.dp, right.dp, bottom.dp, (right - left).dp, (bottom - top).dp),
                true,
            ),
    )
}
