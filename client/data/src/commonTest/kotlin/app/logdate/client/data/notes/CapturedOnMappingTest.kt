package app.logdate.client.data.notes

import app.logdate.client.repository.journals.CaptureDevice
import app.logdate.client.repository.journals.JournalNote
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

class CapturedOnMappingTest {
    private val now = Instant.fromEpochMilliseconds(1_000)

    @Test
    fun `database mapping keeps the capture device of every note type`() {
        val text = JournalNote.Text(creationTimestamp = now, lastUpdated = now, content = "Walk", capturedOn = CaptureDevice.WATCH)
        val image = JournalNote.Image(creationTimestamp = now, lastUpdated = now, mediaRef = "photo.jpg", capturedOn = CaptureDevice.WATCH)
        val video = JournalNote.Video(creationTimestamp = now, lastUpdated = now, mediaRef = "clip.mp4", capturedOn = CaptureDevice.PHONE)
        val audio = JournalNote.Audio(mediaRef = "voice.m4a", creationTimestamp = now, lastUpdated = now, capturedOn = CaptureDevice.WATCH)

        assertEquals("watch", text.toEntity().capturedOn)
        assertEquals(CaptureDevice.WATCH, text.toEntity().toModel().capturedOn)
        assertEquals(CaptureDevice.WATCH, image.toEntity().toModel().capturedOn)
        assertEquals(CaptureDevice.PHONE, video.toEntity().toModel().capturedOn)
        assertEquals(CaptureDevice.WATCH, audio.toEntity().toModel().capturedOn)
    }

    @Test
    fun `a note without a device stores none`() {
        assertNull(JournalNote.Text(creationTimestamp = now, lastUpdated = now, content = "Walk").toEntity().capturedOn)
    }

    @Test
    fun `a stored device this version does not know reads as unknown`() {
        val entity = JournalNote.Text(creationTimestamp = now, lastUpdated = now, content = "Walk").toEntity().copy(capturedOn = "tablet")

        assertNull(entity.toModel().capturedOn)
    }
}
