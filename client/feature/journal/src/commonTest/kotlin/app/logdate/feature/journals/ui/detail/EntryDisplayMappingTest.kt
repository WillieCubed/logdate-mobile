package app.logdate.feature.journals.ui.detail

import app.logdate.client.repository.journals.CaptureDevice
import app.logdate.client.repository.journals.JournalNote
import app.logdate.shared.model.PhotoPresentation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

/** Tests [toEntryDisplayData], which turns a note into what a journal shows for it. */
class EntryDisplayMappingTest {
    private val at = Instant.fromEpochMilliseconds(1_000)

    @Test
    fun `a note captured on the watch is marked`() {
        val audio = JournalNote.Audio(mediaRef = "voice.m4a", creationTimestamp = at, lastUpdated = at, capturedOn = CaptureDevice.WATCH)

        assertTrue(audio.toEntryDisplayData().capturedOnWatch)
    }

    @Test
    fun `a note from the phone or from before devices were recorded is not marked`() {
        val fromPhone = JournalNote.Text(creationTimestamp = at, lastUpdated = at, content = "Typed", capturedOn = CaptureDevice.PHONE)
        val unrecorded = JournalNote.Text(creationTimestamp = at, lastUpdated = at, content = "Older")

        assertFalse(fromPhone.toEntryDisplayData().capturedOnWatch)
        assertFalse(unrecorded.toEntryDisplayData().capturedOnWatch)
    }

    @Test
    fun `media references captions and framing reach the entry`() {
        val image =
            JournalNote.Image(
                creationTimestamp = at,
                lastUpdated = at,
                mediaRef = "photo.jpg",
                caption = "Sunset",
                presentation = PhotoPresentation.Framed,
            )

        val entry = image.toEntryDisplayData(listOf(JournalReference(image.uid, "Travel"))) as EntryDisplayData.ImageEntry

        assertEquals("photo.jpg", entry.mediaRef)
        assertEquals("Sunset", entry.caption)
        assertEquals(PhotoPresentation.Framed, entry.presentation)
        assertEquals(listOf(JournalReference(image.uid, "Travel")), entry.otherJournals)
    }
}
