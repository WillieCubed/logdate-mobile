package app.logdate.client.domain.location

import app.logdate.client.domain.location.history.ApplyHistoryEdits
import app.logdate.shared.model.location.HistoryCorrection
import app.logdate.shared.model.location.HistoryField
import app.logdate.shared.model.location.JourneyLeg
import app.logdate.shared.model.location.PlaceVisit
import app.logdate.shared.model.location.SemanticPlace
import app.logdate.shared.model.location.TravelMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
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

    @Test
    fun `a time edit from an inner sample does not cut a joined visit short`() {
        val joined = visit.copy(end = start + 8.hours, evidenceIds = listOf("a", "b", "c"))
        val edit = HistoryCorrection("end", "b", HistoryField.END, (start + 30.minutes).toString())
        assertEquals(start + 8.hours, ApplyHistoryEdits()(listOf(joined), listOf(edit), emptyList()).single().end)
        val anchored = edit.copy(targetEvidenceId = "a")
        assertEquals(start + 30.minutes, ApplyHistoryEdits()(listOf(joined), listOf(anchored), emptyList()).single().end)
    }

    @Test
    fun `a deleted connector stays deleted when its edge samples change`() {
        val home = visit.copy(id = "home", evidenceIds = listOf("h1", "h2", "h3"))
        val work = visit.copy(id = "work", start = start + 1.hours, end = start + 1.hours, evidenceIds = listOf("w1", "w2"))
        val connector =
            JourneyLeg("journey:o:d:h3:w1", start, start + 1.hours, listOf("derived:journey:o:d:h3:w1"), TravelMode.UNKNOWN, emptyList())
        val oldDelete = HistoryCorrection("delete", "derived:journey:o:d:h2:w2", HistoryField.DELETE, "true")
        val result = ApplyHistoryEdits()(listOf(home, connector, work), listOf(oldDelete), emptyList())
        assertEquals(listOf("home", "work"), result.map { it.id })
    }
}
