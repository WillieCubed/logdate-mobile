package app.logdate.client.data.notes

import app.logdate.client.repository.journals.JournalNote
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

class NoteObjectMappersTest {
    private val created = Instant.fromEpochMilliseconds(1_710_000_000_000)

    private val notes: List<JournalNote> =
        listOf(
            JournalNote.Text(creationTimestamp = created, lastUpdated = created, content = "Walk", timeZoneId = "America/Denver"),
            JournalNote.Image(creationTimestamp = created, lastUpdated = created, mediaRef = "media/a.jpg", timeZoneId = "Europe/Paris"),
            JournalNote.Video(creationTimestamp = created, lastUpdated = created, mediaRef = "media/a.mp4", timeZoneId = "Asia/Kolkata"),
            JournalNote.Audio(creationTimestamp = created, lastUpdated = created, mediaRef = "media/a.m4a", timeZoneId = "UTC"),
        )

    @Test
    fun everyNoteTypeKeepsItsCaptureTimeZoneThroughTheEntity() {
        val roundTripped =
            notes.map { note ->
                when (note) {
                    is JournalNote.Text -> note.toEntity().toModel()
                    is JournalNote.Image -> note.toEntity().toModel()
                    is JournalNote.Video -> note.toEntity().toModel()
                    is JournalNote.Audio -> note.toEntity().toModel()
                }
            }

        assertEquals(notes.map { it.timeZoneId }, roundTripped.map { it.timeZoneId })
    }

    @Test
    fun aNoteWithoutARecordedZoneStaysWithoutOne() {
        val text = JournalNote.Text(creationTimestamp = created, lastUpdated = created, content = "Older note")

        assertNull(text.toEntity().timeZoneId)
        assertNull(text.toEntity().toModel().timeZoneId)
    }

    @Test
    fun anUnrecognisedZoneIdIsStoredAsWritten() {
        val text =
            JournalNote.Text(
                creationTimestamp = created,
                lastUpdated = created,
                content = "From a newer tz database",
                timeZoneId = "Nowhere/Newly_Created",
            )

        assertEquals("Nowhere/Newly_Created", text.toEntity().timeZoneId)
    }
}
