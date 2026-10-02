package app.logdate.ui.workspace

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SupportingWorkspaceCompositionTest {
    @Test fun portraitKeepsAFullWidthVisualFocusInsteadOfTwoNarrowColumns() {
        val result = resolveSupportingWorkspaceComposition(720.dp, 1000.dp)
        assertNull(result.browse)
        assertTrue(result.focus.width >= 680.dp)
    }

    @Test fun merelyFittingTheMinimumWidthsDoesNotMakeAUsefulVisualSplit() {
        assertNull(resolveSupportingWorkspaceComposition(720.dp, 500.dp).browse)
    }

    @Test fun landscapeKeepsTheVisualPanelDominant() {
        val result = resolveSupportingWorkspaceComposition(1200.dp, 700.dp)
        assertTrue(result.browse != null)
        assertTrue(result.focus.width >= result.browse!!.width * 1.6f)
        assertTrue(result.focus.width >= 360.dp)
    }
}
