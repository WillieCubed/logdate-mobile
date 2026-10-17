package app.logdate.client.data.notes

import app.logdate.client.repository.journals.CaptureActivity
import app.logdate.client.repository.journals.JournalNote
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

class CapturedDuringMappingTest {
    private val now = Instant.fromEpochMilliseconds(1_000)

    @Test
    fun `database mapping keeps the activity of every note type`() {
        val text = JournalNote.Text(creationTimestamp = now, lastUpdated = now, content = "Walk", capturedDuring = CaptureActivity.MOVING)
        val image =
            JournalNote.Image(
                creationTimestamp = now,
                lastUpdated = now,
                mediaRef = "photo.jpg",
                capturedDuring = CaptureActivity.IN_VEHICLE,
            )
        val video =
            JournalNote.Video(
                creationTimestamp = now,
                lastUpdated = now,
                mediaRef = "clip.mp4",
                capturedDuring = CaptureActivity.MOVING,
            )
        val audio =
            JournalNote.Audio(
                mediaRef = "voice.m4a",
                creationTimestamp = now,
                lastUpdated = now,
                capturedDuring = CaptureActivity.IN_VEHICLE,
            )

        assertEquals("moving", text.toEntity().capturedDuring)
        assertEquals(CaptureActivity.MOVING, text.toEntity().toModel().capturedDuring)
        assertEquals(CaptureActivity.IN_VEHICLE, image.toEntity().toModel().capturedDuring)
        assertEquals(CaptureActivity.MOVING, video.toEntity().toModel().capturedDuring)
        assertEquals(CaptureActivity.IN_VEHICLE, audio.toEntity().toModel().capturedDuring)
    }

    @Test
    fun `a note without an activity stores none`() {
        assertNull(JournalNote.Text(creationTimestamp = now, lastUpdated = now, content = "Walk").toEntity().capturedDuring)
    }

    @Test
    fun `a stored activity this version does not know reads as unknown`() {
        val entity =
            JournalNote
                .Text(
                    creationTimestamp = now,
                    lastUpdated = now,
                    content = "Walk",
                ).toEntity()
                .copy(capturedDuring = "swimming")

        assertNull(entity.toModel().capturedDuring)
    }
}
