package app.logdate.client.sync.conflict

import app.logdate.client.repository.journals.CaptureActivity
import app.logdate.client.repository.journals.JournalNote
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * What the person was doing at capture is written once, where the note was captured. A copy that lacks
 * it (an older app version, or a server that predates the field) must never erase it.
 */
class JournalNoteCaptureActivityConflictTest {
    private val resolver = JournalNoteConflictResolver()
    private val noteId = Uuid.random()
    private val created = Instant.fromEpochMilliseconds(1_710_000_000_000)
    private val later = Instant.fromEpochMilliseconds(1_710_000_100_000)

    private fun text(
        content: String,
        capturedDuring: CaptureActivity?,
    ) = JournalNote.Text(
        uid = noteId,
        creationTimestamp = created,
        lastUpdated = created,
        content = content,
        capturedDuring = capturedDuring,
    )

    private fun resolve(
        local: JournalNote,
        remote: JournalNote,
    ) = resolver.resolve(local, remote, created, later)

    @Test
    fun `a remote copy without an activity keeps the local activity when the content matches`() {
        val resolved =
            assertIs<ConflictResolution.KeepRemote<JournalNote>>(resolve(text("Walk", CaptureActivity.IN_VEHICLE), text("Walk", null)))

        assertEquals(CaptureActivity.IN_VEHICLE, resolved.value.capturedDuring)
    }

    @Test
    fun `a remote copy without an activity keeps the local activity when the remote content wins`() {
        val resolution = resolve(text("Walk", CaptureActivity.IN_VEHICLE), text("Walk by the river", null))

        val resolved = assertIs<ConflictResolution.KeepRemote<JournalNote>>(resolution)
        assertEquals("Walk by the river", (resolved.value as JournalNote.Text).content)
        assertEquals(CaptureActivity.IN_VEHICLE, resolved.value.capturedDuring)
    }

    @Test
    fun `a local copy without an activity takes the remote activity when the local content wins`() {
        val resolution = resolve(text("Walk by the river", null), text("Walk", CaptureActivity.IN_VEHICLE))

        val resolved = assertIs<ConflictResolution.KeepLocal<JournalNote>>(resolution)
        assertEquals(CaptureActivity.IN_VEHICLE, resolved.value.capturedDuring)
    }

    @Test
    fun `a merge keeps an activity from either copy`() {
        val withLocal = resolve(text("Morning", CaptureActivity.IN_VEHICLE), text("Evening", null))
        val withRemote = resolve(text("Morning", null), text("Evening", CaptureActivity.IN_VEHICLE))

        assertEquals(CaptureActivity.IN_VEHICLE, assertIs<ConflictResolution.Merge<JournalNote>>(withLocal).merged.capturedDuring)
        assertEquals(CaptureActivity.IN_VEHICLE, assertIs<ConflictResolution.Merge<JournalNote>>(withRemote).merged.capturedDuring)
    }

    @Test
    fun `an audio note keeps its local activity when the remote copy has none`() {
        val local =
            JournalNote.Audio(
                uid = noteId,
                mediaRef = "voice.m4a",
                creationTimestamp = created,
                lastUpdated = created,
                capturedDuring = CaptureActivity.IN_VEHICLE,
            )

        val resolved = assertIs<ConflictResolution.KeepRemote<JournalNote>>(resolve(local, local.copy(capturedDuring = null)))
        assertEquals(CaptureActivity.IN_VEHICLE, resolved.value.capturedDuring)
    }
}
