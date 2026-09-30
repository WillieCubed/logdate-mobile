package app.logdate.feature.location.timeline.ui.history

import app.logdate.client.domain.location.history.LocationHistorySnapshot
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.NoteCoordinates
import app.logdate.client.repository.journals.NoteLocation
import app.logdate.client.repository.journals.NotePlace
import app.logdate.shared.model.location.PlaceVisit
import app.logdate.shared.model.location.SemanticPlace
import app.logdate.util.toReadableDateShort
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import kotlin.uuid.Uuid

class HumanHistoryMapperTest {
    private val time = Instant.parse("2026-09-29T10:00:00Z")
    private val placeId = Uuid.parse("00000000-0000-0000-0000-000000000001")
    private val place = SemanticPlace(placeId.toString(), "Library", 36.0, -115.0)
    private val memory =
        JournalNote.Text(
            creationTimestamp = time,
            lastUpdated = time,
            content = "A good book",
            location = NoteLocation(place = NotePlace(placeId, "Library", 36.0, -115.0)),
        )

    @Test
    fun `a visit spanning days shows both dates rather than implying one day's times`() {
        val end = time + 2.days
        val label = PlaceVisit("long-stay", time, end, listOf("anchor"), 36.0, -115.0, true, place).toHistoryUi(emptyList()).timeLabel
        assertTrue(label.contains(time.toReadableDateShort()))
        assertTrue(label.contains(end.toReadableDateShort()))
    }

    @Test
    fun `late observations preserve selection by shared evidence instead of choosing a nearby visit`() {
        val before = PlaceVisit("old", time, time, listOf("original"), 36.0, -115.0, true, place)
        val after = before.copy(id = "new", evidenceIds = listOf("late-earlier", "original"))
        val unrelated = before.copy(id = "other", evidenceIds = listOf("return-visit"))
        assertEquals("new", remapHistorySelection("old", listOf(before), listOf(unrelated, after)))
        assertEquals(null, remapHistorySelection("old", listOf(before), listOf(unrelated)))
        assertEquals("new", remapHistorySelection("old", emptyList(), listOf(after), "original"))
    }

    @Test
    fun `a geotagged memory supplies a place without claiming a recorded visit`() {
        val result = snapshot(notes = listOf(memory)).placeRows().single()
        assertEquals("Library", result.title)
        assertEquals(memory.uid.toString(), result.memories.single().id)
        assertTrue(result.supportingText.contains("Latest memory"))
        assertFalse(result.supportingText.contains("Last visited"))
    }

    @Test
    fun `unlabelled memories remain accessible without coordinate labels`() {
        val note = memory.copy(location = NoteLocation(coordinates = NoteCoordinates(36.0, -115.0)))
        val result = snapshot(notes = listOf(note)).placeRows().single()
        assertFalse(result.title.contains("36.0"))
        assertEquals(note.uid.toString(), result.memories.single().id)
    }

    @Test
    fun `place collection combines memories and separate visits without duplicating places`() {
        val visits = listOf(visit("morning"), visit("afternoon"))
        val result = snapshot(visits, listOf(memory)).placeRows().single()
        assertEquals(place.id, result.id)
        assertTrue(result.supportingText.contains("2 visits"))
        assertTrue(result.supportingText.contains("Latest memory"))
        assertEquals(1, result.memories.size)
    }

    @Test
    fun `explicitly linked memory does not also appear under its older geotag`() {
        val cafe = SemanticPlace("cafe", "Cafe", 36.001, -115.0)
        val cafeVisit = visit("coffee").copy(place = cafe, memoryIds = listOf(memory.uid.toString()))
        val rows = snapshot(listOf(visit("library"), cafeVisit), listOf(memory)).placeRows()
        assertEquals(emptyList(), rows.first { it.id == place.id }.memories)
        assertEquals(1, rows.first { it.id == cafe.id }.memories.size)
    }

    @Test
    fun `place rows keep linked memories with their visit across many other places`() {
        val otherVisits =
            (0 until 250).map { index ->
                val other = SemanticPlace("other-$index", "Place $index", 36.0, -115.0)
                visit("visit-$index").copy(place = other)
            }
        val cafe = SemanticPlace("cafe", "Cafe", 36.001, -115.0)
        val cafeVisit = visit("coffee").copy(place = cafe, memoryIds = listOf(memory.uid.toString()))
        val rows = snapshot(otherVisits + cafeVisit, listOf(memory)).placeRows()

        assertEquals(251, rows.size)
        assertEquals(listOf(memory.uid.toString()), rows.first { it.id == cafe.id }.memories.map { it.id })
        assertTrue(rows.filter { it.id != cafe.id }.all { it.memories.isEmpty() })
    }

    @Test
    fun `one memory linked to visits at two places remains visible at both`() {
        val cafe = SemanticPlace("cafe", "Cafe", 36.001, -115.0)
        val linkedId = memory.uid.toString()
        val rows =
            snapshot(
                listOf(
                    visit("library").copy(memoryIds = listOf(linkedId)),
                    visit("coffee").copy(place = cafe, memoryIds = listOf(linkedId)),
                ),
                listOf(memory),
            ).placeRows()

        assertEquals(listOf(linkedId), rows.first { it.id == place.id }.memories.map { it.id })
        assertEquals(listOf(linkedId), rows.first { it.id == cafe.id }.memories.map { it.id })
    }

    @Test
    fun `sparse evidence is described as a location clue rather than a visit`() {
        val clue = visit("clue").copy(confirmedStay = false, place = null)
        val row = clue.toHistoryUi(emptyList())
        assertEquals("Location recorded", row.title)
        assertTrue(row.timeLabel.startsWith("Around "))
        assertTrue(row.isApproximate)
    }

    @Test
    fun `nearby automatic labels group without losing visits and newest places come first`() {
        val older = SemanticPlace("road-a", "Near West Spring Mountain Road", 36.0, -115.0)
        val newer = SemanticPlace("road-b", "Near West Spring Mountain Road", 36.0002, -115.0)
        val distant = SemanticPlace("road-c", "Near West Spring Mountain Road", 36.01, -115.0)
        val rows =
            snapshot(
                listOf(
                    visit("older").copy(place = older, start = time - 2.days),
                    visit("newer").copy(place = newer, start = time),
                    visit("distant").copy(place = distant, start = time - 1.days),
                ),
                emptyList(),
            ).placeRows()

        assertEquals(2, rows.size)
        assertEquals(setOf("road-a", "road-b"), rows.first().sourceIds)
        assertTrue(rows.first().supportingText.contains("2 visits"))
        assertEquals(setOf("road-c"), rows.last().sourceIds)
    }

    @Test
    fun `user named places stay separate even when they share coordinates and a name`() {
        val one = SemanticPlace("one", "Home", 36.0, -115.0, userConfirmed = true)
        val two = SemanticPlace("two", "Home", 36.0, -115.0, userConfirmed = true)
        val rows = snapshot(listOf(visit("one").copy(place = one), visit("two").copy(place = two)), emptyList()).placeRows()
        assertEquals(2, rows.size)
    }

    private fun visit(id: String) = PlaceVisit(id, time, time, listOf(id), 36.0, -115.0, true, place)

    private fun snapshot(
        visits: List<PlaceVisit> = emptyList(),
        notes: List<JournalNote>,
    ) = LocationHistorySnapshot(visits, listOf(place), notes, emptyList(), "", emptyList())
}
