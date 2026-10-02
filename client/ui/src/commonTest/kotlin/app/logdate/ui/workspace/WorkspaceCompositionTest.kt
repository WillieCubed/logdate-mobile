package app.logdate.ui.workspace

import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import app.logdate.ui.foldable.FoldableHingeBounds
import app.logdate.ui.foldable.FoldableHingeInfo
import app.logdate.ui.foldable.FoldableHingeOrientation
import app.logdate.ui.foldable.FoldableHingeState
import app.logdate.ui.foldable.FoldableLayoutInfo
import app.logdate.ui.foldable.FoldableOcclusionType
import app.logdate.ui.foldable.FoldablePosture
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WorkspaceCompositionTest {
    @Test fun singleReadingCollectionHonorsItsMaximumWidth() {
        val result = resolveWorkspaceComposition(1200.dp, 800.dp, PanelConstraints.Reading.copy(maximumWidth = 560.dp))
        assertEquals(560.dp, result.focus.width)
        assertEquals(16.dp, result.focus.x)
    }

    @Test fun boundedCollectionStillFillsACompactPhone() {
        val result = resolveWorkspaceComposition(411.dp, 700.dp, PanelConstraints.Reading.copy(maximumWidth = 560.dp))
        assertEquals(411.dp, result.focus.width)
    }

    @Test fun rtlBrowseStartsOnTheRightWithoutMovingThePhysicalHinge() {
        val result =
            resolveWorkspaceComposition(
                1200.dp,
                800.dp,
                browse = PanelConstraints.Browse,
                browseOnStart = true,
                layoutDirection = androidx.compose.ui.unit.LayoutDirection.Rtl,
            )
        assertTrue(result.browse!!.x > result.focus.x)
    }

    @Test fun aBookUsesBothSafeRegionsWhenOnlyTheReverseArrangementFits() {
        val result =
            resolveWorkspaceComposition(
                760.dp,
                700.dp,
                PanelConstraints.Visual,
                PanelConstraints.Browse,
                foldable = fold(FoldableHingeOrientation.Vertical, 315, 0, 335, 700),
            )
        assertTrue(result.browse != null)
        assertEquals(351.dp, result.focus.x)
    }

    @Test fun phoneKeepsFocusedContentWithoutAnEmptyBrowsePanel() {
        val result = resolveWorkspaceComposition(411.dp, 700.dp, browse = PanelConstraints.Browse)
        assertNull(result.browse)
        assertEquals(PanelBounds(0.dp, 0.dp, 411.dp, 700.dp), result.focus)
    }

    @Test fun visualFocusReceivesMoreRoomThanSupportingBrowse() {
        val result = resolveWorkspaceComposition(1200.dp, 800.dp, PanelConstraints.Visual, PanelConstraints.Browse)
        assertEquals(320.dp, result.browse?.width)
        assertEquals(832.dp, result.focus.width)
        assertEquals(16.dp, result.focus.x)
        assertEquals(864.dp, result.browse?.x)
    }

    @Test fun aPanelDoesNotAppearUntilItsMinimumFitsWithGutters() {
        assertNull(resolveWorkspaceComposition(687.dp, 700.dp, PanelConstraints.Visual, PanelConstraints.Browse).browse)
        val result = resolveWorkspaceComposition(688.dp, 700.dp, PanelConstraints.Visual, PanelConstraints.Browse)
        assertEquals(280.dp, result.browse?.width)
        assertEquals(360.dp, result.focus.width)
    }

    @Test fun inspectorDoesNotStealTheFocusMinimum() {
        val result =
            resolveWorkspaceComposition(900.dp, 700.dp, PanelConstraints.Reading, PanelConstraints.Browse, PanelConstraints.Inspector)
        assertNull(result.inspector)
        assertTrue(result.focus.width >= 320.dp)
    }

    @Test fun bookHingeIsTranslatedAfterRailAndHeader() {
        val fold = fold(FoldableHingeOrientation.Vertical, 500, 0, 520, 900)
        val result =
            resolveWorkspaceComposition(
                900.dp,
                800.dp,
                PanelConstraints.Visual,
                PanelConstraints.Browse,
                foldable = fold,
                origin = DpOffset(80.dp, 100.dp),
            )
        assertEquals(PanelBounds(16.dp, 16.dp, 388.dp, 768.dp), result.focus)
        assertEquals(456.dp, result.browse?.x)
        assertEquals(360.dp, result.browse?.width)
    }

    @Test fun tabletopPutsFocusAboveControlsWithoutCrossingTheHinge() {
        val result =
            resolveWorkspaceComposition(
                800.dp,
                800.dp,
                PanelConstraints.Visual,
                PanelConstraints.Browse,
                foldable = fold(FoldableHingeOrientation.Horizontal, 0, 400, 800, 420),
            )
        assertEquals(368.dp, result.focus.height)
        assertEquals(436.dp, result.browse?.y)
    }

    @Test fun smallHingeRegionUsesOneSafeRegion() {
        val result =
            resolveWorkspaceComposition(
                650.dp,
                700.dp,
                PanelConstraints.Visual,
                PanelConstraints.Browse,
                foldable = fold(FoldableHingeOrientation.Vertical, 220, 0, 240, 700),
            )
        assertNull(result.browse)
        assertEquals(256.dp, result.focus.x)
        assertEquals(378.dp, result.focus.width)
    }

    @Test fun readingPlacesTheBrowsePanelBeforeTheFocus() {
        val result = resolveWorkspaceComposition(1200.dp, 800.dp, PanelConstraints.Reading, PanelConstraints.Browse, browseOnStart = true)
        assertEquals(16.dp, result.browse?.x)
        assertEquals(352.dp, result.focus.x)
    }

    @Test fun singleWidePanelKeepsTheSharedWorkspaceGutter() {
        val result = resolveWorkspaceComposition(900.dp, 700.dp)
        assertEquals(PanelBounds(16.dp, 16.dp, 868.dp, 668.dp), result.focus)
    }

    @Test fun shortWorkspaceDoesNotExposeAnUnusableSupportingPanel() {
        assertNull(resolveWorkspaceComposition(1000.dp, 160.dp, browse = PanelConstraints.Browse).browse)
    }

    private fun fold(
        orientation: FoldableHingeOrientation,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
    ) = FoldableLayoutInfo(
        true,
        if (orientation == FoldableHingeOrientation.Vertical) FoldablePosture.Book else FoldablePosture.Tabletop,
        FoldableHingeInfo(
            orientation,
            FoldableHingeState.HalfOpened,
            FoldableOcclusionType.Full,
            FoldableHingeBounds(left.dp, top.dp, right.dp, bottom.dp, (right - left).dp, (bottom - top).dp),
            true,
        ),
    )
}
