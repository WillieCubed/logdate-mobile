package app.logdate.feature.rewind.ui

import app.logdate.feature.rewind.ui.overview.RewindPreviewUiState
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

class RewindPreviewCompositionTest {
    @Test fun recentRecapAlsoPresentInHistoryIsOnlyShownOnce() {
        val recent =
            RewindPreviewUiState(
                "Latest memory",
                Uuid.parse("00000000-0000-0000-0000-000000000051"),
                "This week",
                "A quiet week",
                LocalDate(2026, 9, 21),
                LocalDate(2026, 9, 27),
                rewindAvailable = true,
            )
        val historical = recent.copy(message = "Old cached description")
        assertEquals(listOf(recent), uniqueRewindPreviews(listOf(recent, historical)))
    }
}
