package app.logdate.ui.workspace

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SupportingSheetTest {
    @Test fun browsingKeepsTheMapVisible() {
        val anchors = supportingSheetAnchors(700.dp)
        assertEquals(315.dp, anchors.browsing)
        assertEquals(520.dp, anchors.expanded)
        assertTrue(anchors.canOverlay)
    }

    @Test fun shortWorkspaceOffersFocusedContentRatherThanClipping() {
        assertFalse(supportingSheetAnchors(290.dp).canOverlay)
    }

    @Test fun largeTextPeekStillPreservesMapMinimum() {
        val anchors = supportingSheetAnchors(700.dp, 220.dp)
        assertEquals(220.dp, anchors.peek)
        assertTrue(anchors.expanded <= 520.dp)
    }
}
