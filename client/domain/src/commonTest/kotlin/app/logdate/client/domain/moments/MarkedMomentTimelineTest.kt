package app.logdate.client.domain.moments

import app.logdate.client.domain.timeline.Timeline
import app.logdate.client.repository.moments.MarkState
import app.logdate.client.repository.moments.MarkedMoment
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant
import kotlin.uuid.Uuid

class MarkedMomentTimelineTest {
    @Test
    fun `a mark creates a timeline day without inventing note content`() {
        val at = Instant.parse("2026-10-12T12:00:00Z")
        val date = LocalDate(2026, 10, 12)
        val mark = MarkedMoment(Uuid.random(), at, "UTC", null, null, MarkState.OPEN, "", emptySet(), "watch", at, 0)
        val timeline = withTimelineMarks(Timeline(emptyList()), mapOf(date to listOf(mark)))
        assertEquals(listOf(mark), timeline.days.single().marks)
        assertEquals(emptyList(), timeline.days.single().entries)
        assertEquals(emptyList(), timeline.days.single().moments)
    }
}
