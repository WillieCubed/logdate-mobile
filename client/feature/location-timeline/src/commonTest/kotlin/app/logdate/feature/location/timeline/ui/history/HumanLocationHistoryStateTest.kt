package app.logdate.feature.location.timeline.ui.history

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HumanLocationHistoryStateTest {
    private val day =
        HumanLocationHistoryState(
            dateLabel = "Tuesday, September 29",
            items =
                listOf(
                    HistoryItemUi("home", HistoryItemKind.Visit, "Home", "8:00 AM"),
                    HistoryItemUi("walk", HistoryItemKind.Journey, "Walking", "9:00 AM"),
                    HistoryItemUi("cafe", HistoryItemKind.Visit, "Mothership Coffee", "9:15 AM"),
                ),
        )

    @Test
    fun `scrubbing includes journeys and clamps to the ends of the day`() {
        assertEquals("home", day.itemAtReplayPosition(-2f)?.id)
        assertEquals("walk", day.itemAtReplayPosition(0.5f)?.id)
        assertEquals("cafe", day.itemAtReplayPosition(2f)?.id)
        assertNull(day.copy(items = emptyList()).itemAtReplayPosition(0f))
        assertNull(day.itemAtReplayPosition(Float.NaN))
    }

    @Test
    fun `stepping starts at the first moment without wrapping or reviving stale selection`() {
        assertEquals("home", day.adjacentItem(1)?.id)
        assertNull(day.adjacentItem(-1))
        assertEquals("walk", day.copy(selectedItemId = "home").adjacentItem(1)?.id)
        assertNull(day.copy(selectedItemId = "home").adjacentItem(-1))
        assertNull(day.copy(selectedItemId = "missing").adjacentItem(1))
        assertNull(day.copy(selectedItemId = "cafe").adjacentItem(1))
    }

    @Test
    fun `place search trims whitespace and matches names without case sensitivity`() {
        val state =
            day.copy(
                places =
                    listOf(
                        HistoryPlaceUi("cafe", "Mothership Coffee", "2 visits"),
                        HistoryPlaceUi("library", "West Charleston Library", "1 visit"),
                    ),
                placesQuery = "  COFFEE ",
            )
        assertEquals(listOf("cafe"), state.filteredPlaces().map { it.id })
        assertEquals(2, state.copy(placesQuery = " ").filteredPlaces().size)
        assertEquals(emptyList(), state.copy(placesQuery = "unknown").filteredPlaces())
    }
}
