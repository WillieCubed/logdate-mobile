package app.logdate.client.domain.moments

import app.logdate.client.repository.journals.CaptureActivity
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.moments.MarkState
import app.logdate.client.repository.moments.MarkedMoment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant
import kotlin.uuid.Uuid

class MarkedMomentEditorContextTest {
    @Test
    fun `writing more keeps saved content on the original marked day`() {
        val captured = Instant.parse("2026-10-10T21:00:00Z")
        val written = Instant.parse("2026-10-12T21:00:00Z")
        val mark =
            MarkedMoment(Uuid.random(), captured, "America/Los_Angeles", null, null, MarkState.OPEN, "", emptySet(), "watch", captured, 0)
        val note = JournalNote.Text(creationTimestamp = written, lastUpdated = written, content = "Remembered later")
        val saved = listOf(note).withMarkedCapture(mark).single()
        assertEquals(captured, saved.creationTimestamp)
        assertEquals(mark.timeZoneId, saved.timeZoneId)
        assertEquals(note.uid, saved.uid)
        assertEquals(written, saved.lastUpdated)
    }

    @Test
    fun `handwriting fills the original marked day without changing original ink`() {
        val captured = Instant.parse("2026-10-10T21:00:00Z")
        val written = Instant.parse("2026-10-12T21:00:00Z")
        val mark =
            MarkedMoment(
                Uuid.random(),
                captured,
                "UTC",
                null,
                null,
                MarkState.OPEN,
                "",
                emptySet(),
                "watch",
                captured,
                0,
                capturedDuring = CaptureActivity.MOVING,
            )
        val note =
            JournalNote.Handwriting(
                creationTimestamp = written,
                lastUpdated = written,
                document =
                    app.logdate.shared.model
                        .HandwritingDocument(),
                unreadableContent = "preserved ink payload",
            )
        val saved = listOf(note).withMarkedCapture(mark).single() as JournalNote.Handwriting

        assertEquals(captured, saved.creationTimestamp)
        assertEquals(mark.timeZoneId, saved.timeZoneId)
        assertEquals(CaptureActivity.MOVING, saved.capturedDuring)
        assertEquals(note.uid, saved.uid)
        assertEquals(note.document, saved.document)
        assertEquals(note.unreadableContent, saved.unreadableContent)
        assertEquals(written, saved.lastUpdated)
    }

    @Test
    fun `notes that fill a mark record the activity the mark was made during`() {
        val captured = Instant.parse("2026-10-10T21:00:00Z")
        val mark =
            MarkedMoment(
                Uuid.random(),
                captured,
                "UTC",
                null,
                null,
                MarkState.OPEN,
                "",
                emptySet(),
                "watch",
                captured,
                0,
                capturedDuring = CaptureActivity.MOVING,
            )
        val note = JournalNote.Text(creationTimestamp = captured, lastUpdated = captured, content = "On the trail")

        assertEquals(CaptureActivity.MOVING, listOf(note).withMarkedCapture(mark).single().capturedDuring)
    }
}
