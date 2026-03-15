package app.logdate.client.e2e

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.NoteType
import app.logdate.client.sync.WearSyncNotificationHelper
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests for the watch sync notification helper.
 *
 * Verifies that notification content is correct for each note type
 * and that the content-for-note mapping produces user-friendly text.
 */
@RunWith(AndroidJUnit4::class)
class WearSyncNotificationTest {

    private val fixedTime = Instant.fromEpochMilliseconds(1_710_000_000_000)

    @Test
    fun audioNoteProducesVoiceNoteTitle() {
        val note = JournalNote.Audio(
            uid = Uuid.random(),
            creationTimestamp = fixedTime,
            lastUpdated = fixedTime,
            mediaRef = "/storage/recording.aac",
            durationMs = 4200,
        )

        val (title, body) = WearSyncNotificationHelper.contentForNote(note)

        assertEquals("Voice note from your watch", title)
        assertEquals("Tap to expand in editor", body)
    }

    @Test
    fun textNoteShowsContentPreview() {
        val content = "Had an amazing lunch at the park today with the family"
        val note = JournalNote.Text(
            uid = Uuid.random(),
            creationTimestamp = fixedTime,
            lastUpdated = fixedTime,
            content = content,
        )

        val (title, body) = WearSyncNotificationHelper.contentForNote(note)

        assertEquals("Note from your watch", title)
        assertEquals(content, body)
    }

    @Test
    fun longTextNoteTruncatesTo80Chars() {
        val longContent = "A".repeat(200)
        val note = JournalNote.Text(
            uid = Uuid.random(),
            creationTimestamp = fixedTime,
            lastUpdated = fixedTime,
            content = longContent,
        )

        val (_, body) = WearSyncNotificationHelper.contentForNote(note)

        assertEquals(80, body.length)
    }

    @Test
    fun imageNoteProducesPhotoTitle() {
        val note = JournalNote.Image(
            uid = Uuid.random(),
            creationTimestamp = fixedTime,
            lastUpdated = fixedTime,
            mediaRef = "/storage/photo.jpg",
        )

        val (title, body) = WearSyncNotificationHelper.contentForNote(note)

        assertEquals("Photo from your watch", title)
        assertEquals("Tap to view", body)
    }

    @Test
    fun videoNoteProducesVideoTitle() {
        val note = JournalNote.Video(
            uid = Uuid.random(),
            creationTimestamp = fixedTime,
            lastUpdated = fixedTime,
            mediaRef = "/storage/clip.mp4",
        )

        val (title, body) = WearSyncNotificationHelper.contentForNote(note)

        assertEquals("Video from your watch", title)
        assertEquals("Tap to view", body)
    }

    @Test
    fun moodTextNoteShowsMoodContent() {
        val note = JournalNote.Text(
            uid = Uuid.random(),
            creationTimestamp = fixedTime,
            lastUpdated = fixedTime,
            content = "#mood:good Feeling great today",
        )

        val (title, body) = WearSyncNotificationHelper.contentForNote(note)

        assertEquals("Note from your watch", title)
        assertTrue(body.startsWith("#mood:"))
    }

    @Test
    fun eachNoteTypeMapsToCorrectType() {
        val types = mapOf(
            NoteType.AUDIO to JournalNote.Audio(
                uid = Uuid.random(), creationTimestamp = fixedTime, lastUpdated = fixedTime,
                mediaRef = "/a.aac",
            ),
            NoteType.TEXT to JournalNote.Text(
                uid = Uuid.random(), creationTimestamp = fixedTime, lastUpdated = fixedTime,
                content = "test",
            ),
            NoteType.IMAGE to JournalNote.Image(
                uid = Uuid.random(), creationTimestamp = fixedTime, lastUpdated = fixedTime,
                mediaRef = "/a.jpg",
            ),
            NoteType.VIDEO to JournalNote.Video(
                uid = Uuid.random(), creationTimestamp = fixedTime, lastUpdated = fixedTime,
                mediaRef = "/a.mp4",
            ),
        )

        for ((type, note) in types) {
            val (title, _) = WearSyncNotificationHelper.contentForNote(note)
            assertTrue(title.isNotEmpty(), "Title should not be empty for $type")
        }
    }
}
