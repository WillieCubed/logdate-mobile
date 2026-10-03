package app.logdate.client.domain.today

import app.logdate.client.repository.journals.JournalNote
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class MoodNoteTagTest {
    private val now = Instant.parse("2026-10-03T10:00:00Z")

    @Test
    fun `the tag is read from the start of the note`() {
        assertEquals("great", MoodNoteTag.tagOf("#mood:great Feeling great"))
    }

    @Test
    fun `a tag later in the text is not a check-in`() {
        assertNull(MoodNoteTag.tagOf("Felt #mood:great today"))
        assertFalse(MoodNoteTag.isFeeling(JournalNote.Text(creationTimestamp = now, lastUpdated = now, content = "Felt #mood:great")))
    }

    @Test
    fun `only text notes can be check-ins`() {
        assertTrue(MoodNoteTag.isFeeling(JournalNote.Text(creationTimestamp = now, lastUpdated = now, content = "#mood:ok Feeling ok")))
        assertFalse(MoodNoteTag.isFeeling(JournalNote.Audio(mediaRef = "audio/1.m4a", creationTimestamp = now, lastUpdated = now)))
    }
}
