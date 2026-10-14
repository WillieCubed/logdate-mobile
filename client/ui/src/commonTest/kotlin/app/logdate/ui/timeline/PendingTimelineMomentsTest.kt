package app.logdate.ui.timeline

import app.logdate.client.repository.moments.MarkState
import app.logdate.client.repository.moments.MarkedMoment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

class PendingTimelineMomentsTest {
    private val morning = Instant.parse("2026-05-04T09:00:00Z")
    private val noon = Instant.parse("2026-05-04T12:00:00Z")
    private val evening = Instant.parse("2026-05-04T18:00:00Z")
    private val mark = MarkedMoment(Uuid.random(), noon, "UTC", null, null, MarkState.OPEN, "", emptySet(), "watch", noon, 0)

    @Test
    fun `pending anchor occupies its actual place between ordinary moments`() {
        val ordinary =
            listOf(
                MomentUiState("evening", "Evening", capturedAt = evening),
                MomentUiState("morning", "Morning", capturedAt = morning),
            )
        val result = integratePendingMoments(ordinary, listOf(mark))
        assertEquals(listOf(morning, noon, evening), result.map { it.capturedAt })
        assertEquals(mark.id, result[1].pendingMark?.id)
    }

    @Test
    fun `mark-only day has a real semantic moment without invented note content`() {
        val result = integratePendingMoments(emptyList(), listOf(mark))
        assertEquals(mark, result.single().pendingMark)
        assertTrue(result.single().media.isEmpty())
        assertEquals(null, result.single().textSnippet)
    }

    @Test
    fun `resolved anchor leaves only resulting content without duplicate pending moment`() {
        val saved = MomentUiState("saved", "By the lake", capturedAt = noon, textSnippet = "A quiet morning")
        val result = integratePendingMoments(listOf(saved), listOf(mark.copy(state = MarkState.FILLED)))
        assertEquals(listOf(saved), result)
    }

    @Test
    fun `flag-off empty mark source leaves ordinary presentation untouched`() {
        val original =
            listOf(MomentUiState("later", "Later", capturedAt = evening), MomentUiState("earlier", "Earlier", capturedAt = morning))
        assertEquals(original, integratePendingMoments(original, emptyList()))
    }

    @Test
    fun `adding pending mark does not suppress fallback content from ordinary notes`() {
        val saved = TextNoteUiState(noteId = Uuid.random(), text = "Existing journal content", timestamp = morning)
        val day =
            createSemanticTimelineDayUiState(
                summary = "",
                date = kotlinx.datetime.LocalDate(2026, 5, 4),
                moments = emptyList(),
                notes = listOf(saved),
                pendingMarks = listOf(mark),
            )
        assertEquals(2, day.moments.size)
        assertTrue(day.moments.any { it.textSnippet == "Existing journal content" })
        assertTrue(day.moments.any { it.pendingMark?.id == mark.id })
    }
}
