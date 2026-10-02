package app.logdate.feature.location.timeline.ui.history

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HistoryCameraPolicyTest {
    @Test fun aSelectionDuringLoadingRemainsPendingUntilReady() {
        val beforeReady = advanceHistorySelection(false, "home", "cafe")
        kotlin.test.assertEquals("home", beforeReady)
        assertTrue(shouldMoveHistoryCamera(true, beforeReady, "cafe"))
        kotlin.test.assertEquals("cafe", advanceHistorySelection(true, beforeReady, "cafe"))
    }

    @Test fun updatingEvidenceDoesNotRecenterAnAlreadyFittedDay() {
        assertFalse(shouldFitHistoryDay(fitted = true, hasCoordinates = true))
        assertTrue(shouldFitHistoryDay(fitted = false, hasCoordinates = true))
        assertFalse(shouldFitHistoryDay(fitted = false, hasCoordinates = false))
    }

    @Test fun initialSelectionAndRepeatedSelectionDoNotMoveTheCamera() {
        assertFalse(shouldMoveHistoryCamera(false, null, "cafe"))
        assertFalse(shouldMoveHistoryCamera(true, "cafe", "cafe"))
        assertTrue(shouldMoveHistoryCamera(true, "cafe", "library"))
    }
}
