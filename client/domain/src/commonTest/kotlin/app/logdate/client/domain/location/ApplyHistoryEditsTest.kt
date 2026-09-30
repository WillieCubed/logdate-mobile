package app.logdate.client.domain.location

import app.logdate.client.domain.location.history.ApplyHistoryEdits
import app.logdate.shared.model.location.HistoryCorrection
import app.logdate.shared.model.location.HistoryField
import app.logdate.shared.model.location.PlaceVisit
import app.logdate.shared.model.location.SemanticPlace
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

class ApplyHistoryEditsTest {
    private val start = Instant.parse("2026-09-27T10:00:00Z")
    private val visit = PlaceVisit("visit", start, start, listOf("a", "b"), 36.0, -115.0, false)

    @Test
    fun `deleting any evidence suppresses a recomputed visit`() {
        val edit = HistoryCorrection("delete", "b", HistoryField.DELETE, "true")
        assertTrue(ApplyHistoryEdits()(listOf(visit.copy(id = "recomputed")), listOf(edit), emptyList()).isEmpty())
    }

    @Test
    fun `confirmed place survives recomputation and superseded edits`() {
        val place = SemanticPlace("home", "Home", 36.0, -115.0, userConfirmed = true)
        val old = HistoryCorrection("old", "a", HistoryField.PLACE, "wrong")
        val edit = HistoryCorrection("new", "a", HistoryField.PLACE, "home", listOf("old"))
        val result = ApplyHistoryEdits()(listOf(visit), listOf(old, edit), listOf(place)).single() as PlaceVisit
        assertEquals("Home", result.place?.name)
    }

    @Test
    fun `concurrent edits do not silently pick one place`() {
        val edits =
            listOf(HistoryCorrection("one", "a", HistoryField.PLACE, "home"), HistoryCorrection("two", "a", HistoryField.PLACE, "work"))
        val result = ApplyHistoryEdits()(listOf(visit), edits, listOf(SemanticPlace("home", "Home", 36.0, -115.0))).single() as PlaceVisit
        assertEquals(null, result.place)
    }
}
