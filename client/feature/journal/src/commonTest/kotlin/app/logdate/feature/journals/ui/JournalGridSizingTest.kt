package app.logdate.feature.journals.ui

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertTrue

class JournalGridSizingTest {
    @Test fun expandedCollectionUsesRecognizableCoversRatherThanPhoneThumbnails() {
        assertTrue(journalGridMinimumCoverWidth(1168.dp) >= 240.dp)
        assertTrue(journalGridMinimumCoverWidth(720.dp) > journalGridMinimumCoverWidth(411.dp))
    }

    @Test fun compactAndSupportingPanelsStillFitTwoCovers() {
        assertTrue(journalGridMinimumCoverWidth(320.dp) <= 136.dp)
        assertTrue(journalGridMinimumCoverWidth(411.dp) <= 180.dp)
    }
}
