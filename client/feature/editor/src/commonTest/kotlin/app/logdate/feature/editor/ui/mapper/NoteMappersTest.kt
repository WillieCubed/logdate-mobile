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
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Pins the contract of [toJournalNote] and [toDomainBlock] mappers.
 *
 * These tests are the spec for the editor save path. They guard against silent
 * data loss during the upcoming refactor that introduces an explicit capture-state
 * machine for audio blocks: any mapper change that drops user content must show up here.
 */
class NoteMappersTest {
    private val fixedTimestamp: Instant = Clock.System.now()

    // --- Audio: the bug-class focus ---

    @Test
    fun `audio block in empty capture state maps to null`() {
        val block =
            AudioBlockUiState(
                id = Uuid.random(),
                timestamp = fixedTimestamp,
                captureState = AudioCaptureState.Empty,
            )

        assertNull(block.toJournalNote(), "Audio block without a finalized recording must not be persisted")
    }

    @Test
    fun `audio block in recording capture state maps to null`() {
        val block =
            AudioBlockUiState(
                id = Uuid.random(),
                timestamp = fixedTimestamp,
                captureState = AudioCaptureState.Recording,
            )

        assertNull(block.toJournalNote(), "Recording-in-progress audio must not be persisted as a journal note")
    }

    @Test
    fun `audio block in stopping capture state maps to null`() {
        val block =
            AudioBlockUiState(
                id = Uuid.random(),
                timestamp = fixedTimestamp,
                captureState = AudioCaptureState.Stopping,
            )

        assertNull(block.toJournalNote(), "Audio waiting for finalization must not be persisted yet")
    }

    @Test
    fun `audio block in ready capture state maps to journal note audio`() {
        val id = Uuid.random()
        val block =
            AudioBlockUiState(
                id = id,
                timestamp = fixedTimestamp,
                captureState =
                    AudioCaptureState.Ready(
                        uri = "file:///audio_notes/recording_$id.m4a",
                        durationMs = 4_200L,
                    ),
            )

        val note = block.toJournalNote()

        assertNotNull(note, "Audio block in Ready state must persist")
        val audio = assertIs<JournalNote.Audio>(note)
        assertEquals(id, audio.uid)
        assertEquals(fixedTimestamp, audio.creationTimestamp)
        assertEquals("file:///audio_notes/recording_$id.m4a", audio.mediaRef)
        assertEquals(4_200L, audio.durationMs)
    }

    @Test
    fun `audio block uid is preserved through mapping`() {
        val id = Uuid.random()
        val block =
            AudioBlockUiState(
                id = id,
                timestamp = fixedTimestamp,
                captureState = AudioCaptureState.Ready("file:///audio_notes/x.m4a", 0L),
            )

        val note = assertIs<JournalNote.Audio>(block.toJournalNote())

        assertEquals(id, note.uid, "uid must be preserved so transcription rows linked by noteId remain valid")
    }

    @Test
    fun `audio note round trip via toDomainBlock preserves uri and duration`() {
        val id = Uuid.random()
        val note =
            JournalNote.Audio(
                uid = id,
                creationTimestamp = fixedTimestamp,
                lastUpdated = fixedTimestamp,
                mediaRef = "file:///audio_notes/round_trip.m4a",
                durationMs = 9_001L,
            )

        val block = note.toDomainBlock()

        val audio = assertIs<AudioBlockUiState>(block)
        assertEquals(id, audio.id)
        val ready = assertIs<AudioCaptureState.Ready>(audio.captureState)
        assertEquals("file:///audio_notes/round_trip.m4a", ready.uri)
        assertEquals(9_001L, ready.durationMs)
        assertEquals(true, audio.isPersistable())
    }

    // --- Text ---

    @Test
    fun `text block with blank content maps to null`() {
        val block = TextBlockUiState(timestamp = fixedTimestamp, content = "   ")

        assertNull(block.toJournalNote())
    }

    @Test
    fun `text block with content maps to journal note text`() {
        val id = Uuid.random()
        val block =
            TextBlockUiState(
                id = id,
                timestamp = fixedTimestamp,
                content = "today was good",
            )

        val note = assertIs<JournalNote.Text>(block.toJournalNote())

        assertEquals(id, note.uid)
        assertEquals("today was good", note.content)
    }

    // --- Image / Video / Camera (parallel media types) ---

    @Test
    fun `image block without uri maps to null`() {
        val block = ImageBlockUiState(timestamp = fixedTimestamp, uri = null)

        assertNull(block.toJournalNote())
    }

    @Test
    fun `image block with uri maps to journal note image`() {
        val block =
            ImageBlockUiState(
                timestamp = fixedTimestamp,
                uri = "content://photo/1",
                caption = "look at the sky",
            )

        val note = assertIs<JournalNote.Image>(block.toJournalNote())

        assertEquals("content://photo/1", note.mediaRef)
        assertEquals("look at the sky", note.caption)
    }

    @Test
    fun `video block without uri maps to null`() {
        val block = VideoBlockUiState(timestamp = fixedTimestamp, uri = null)

        assertNull(block.toJournalNote())
    }

    @Test
    fun `video block with uri maps to journal note video`() {
        val block =
            VideoBlockUiState(
                timestamp = fixedTimestamp,
                uri = "content://video/1",
                caption = "the lake",
            )

        val note = assertIs<JournalNote.Video>(block.toJournalNote())

        assertEquals("content://video/1", note.mediaRef)
        assertEquals("the lake", note.caption)
    }

    @Test
    fun `camera block as photo maps to journal note image`() {
        val block =
            CameraBlockUiState(
                timestamp = fixedTimestamp,
                uri = "file:///captured/photo.jpg",
                mediaType = CapturedMediaType.PHOTO,
            )

        val note = assertIs<JournalNote.Image>(block.toJournalNote())

        assertEquals("file:///captured/photo.jpg", note.mediaRef)
    }

    @Test
    fun `camera block as video maps to journal note video`() {
        val block =
            CameraBlockUiState(
                timestamp = fixedTimestamp,
                uri = "file:///captured/clip.mp4",
                mediaType = CapturedMediaType.VIDEO,
                durationMs = 3_000L,
            )

        val note = assertIs<JournalNote.Video>(block.toJournalNote())

        assertEquals("file:///captured/clip.mp4", note.mediaRef)
    }

    @Test
    fun `camera block without uri maps to null`() {
        val block = CameraBlockUiState(timestamp = fixedTimestamp, uri = null)

        assertNull(block.toJournalNote())
    }
}
