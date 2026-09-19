package app.logdate.feature.editor.ui.mapper

import app.logdate.client.repository.journals.JournalNote
import app.logdate.feature.editor.ui.camera.CapturedMediaType
import app.logdate.feature.editor.ui.editor.AudioBlockUiState
import app.logdate.feature.editor.ui.editor.AudioCaptureState
import app.logdate.feature.editor.ui.editor.CameraBlockUiState
import app.logdate.feature.editor.ui.editor.ImageBlockUiState
import app.logdate.feature.editor.ui.editor.TextBlockUiState
import app.logdate.feature.editor.ui.editor.VideoBlockUiState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Instant

/**
 * A block records the zone it was created in, and that zone is the one the saved note carries. The
 * zone must survive a draft round trip unchanged: reopening a draft in another zone must not
 * restamp it.
 */
class NoteMappersTimeZoneTest {
    private val created = Instant.fromEpochMilliseconds(1_710_000_000_000)
    private val zone = "Asia/Tokyo"

    @Test
    fun `every block type saves its recorded zone on the note`() {
        val notes =
            listOf(
                TextBlockUiState(timestamp = created, content = "Walk", timeZoneId = zone),
                ImageBlockUiState(timestamp = created, uri = "media/a.jpg", timeZoneId = zone),
                VideoBlockUiState(timestamp = created, uri = "media/a.mp4", timeZoneId = zone),
                CameraBlockUiState(timestamp = created, uri = "media/b.jpg", mediaType = CapturedMediaType.PHOTO, timeZoneId = zone),
                CameraBlockUiState(timestamp = created, uri = "media/b.mp4", mediaType = CapturedMediaType.VIDEO, timeZoneId = zone),
                AudioBlockUiState(
                    timestamp = created,
                    captureState = AudioCaptureState.Ready(uri = "media/a.m4a", durationMs = 1_000),
                    timeZoneId = zone,
                ),
            ).map { assertNotNull(it.toJournalNote()) }

        assertEquals(List(notes.size) { zone }, notes.map { it.timeZoneId })
    }

    @Test
    fun `a note that has no zone reopens as a block that has none`() {
        val note = JournalNote.Text(creationTimestamp = created, lastUpdated = created, content = "Older draft")

        assertNull(note.toDomainBlock().timeZoneId)
    }

    @Test
    fun `a recorded zone survives the trip from note to block and back`() {
        val note = JournalNote.Text(creationTimestamp = created, lastUpdated = created, content = "Walk", timeZoneId = zone)

        assertEquals(zone, note.toDomainBlock().toJournalNote()?.timeZoneId)
    }

    @Test
    fun `a new block records the zone the device is in`() {
        assertNotNull(TextBlockUiState(content = "Walk").timeZoneId)
    }
}
