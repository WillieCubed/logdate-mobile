package app.logdate.client.repository.journals

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/** Tests [CaptureDevice] and the note helpers built on it. */
class CaptureDeviceTest {
    private val at = Instant.fromEpochMilliseconds(1_000)
    private val notes =
        listOf(
            JournalNote.Text(creationTimestamp = at, lastUpdated = at, content = "Walk"),
            JournalNote.Image(creationTimestamp = at, lastUpdated = at, mediaRef = "photo.jpg"),
            JournalNote.Video(creationTimestamp = at, lastUpdated = at, mediaRef = "clip.mp4"),
            JournalNote.Audio(mediaRef = "voice.m4a", creationTimestamp = at, lastUpdated = at),
        )

    @Test
    fun `a wire name reads back as its device`() {
        assertEquals(CaptureDevice.WATCH, CaptureDevice.fromWireName("watch"))
        assertEquals(CaptureDevice.PHONE, CaptureDevice.fromWireName("phone"))
    }

    @Test
    fun `an unknown or missing wire name is unknown`() {
        assertNull(CaptureDevice.fromWireName("tablet"))
        assertNull(CaptureDevice.fromWireName("WATCH"))
        assertNull(CaptureDevice.fromWireName(null))
    }

    @Test
    fun `a note has no capture device until one is recorded`() {
        notes.forEach { note ->
            assertNull(note.capturedOn)
            assertFalse(note.isCapturedOnWatch)
        }
    }

    @Test
    fun `recording the capture device changes nothing else`() {
        notes.forEach { note ->
            val stamped = note.withCapturedOn(CaptureDevice.WATCH)

            assertEquals(CaptureDevice.WATCH, stamped.capturedOn)
            assertTrue(stamped.isCapturedOnWatch)
            assertEquals(note, stamped.withCapturedOn(null))
        }
    }
}
