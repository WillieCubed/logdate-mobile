package app.logdate.client.sync.conflict

import app.logdate.client.repository.journals.CaptureDevice
import app.logdate.client.repository.journals.JournalNote
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * A note's capture device is written once, where the note was captured. A copy that lacks it (an
 * older app version, or a server that predates the field) must never erase it.
 */
class JournalNoteCaptureDeviceConflictTest {
    private val resolver = JournalNoteConflictResolver()
    private val noteId = Uuid.random()
    private val created = Instant.fromEpochMilliseconds(1_710_000_000_000)
    private val later = Instant.fromEpochMilliseconds(1_710_000_100_000)

    private fun text(
        content: String,
        capturedOn: CaptureDevice?,
    ) = JournalNote.Text(
        uid = noteId,
        creationTimestamp = created,
        lastUpdated = created,
        content = content,
        capturedOn = capturedOn,
    )

    private fun resolve(
        local: JournalNote,
        remote: JournalNote,
    ) = resolver.resolve(local, remote, created, later)

    @Test
    fun `a remote copy without a device keeps the local device when the content matches`() {
        val resolved = assertIs<ConflictResolution.KeepRemote<JournalNote>>(resolve(text("Walk", CaptureDevice.WATCH), text("Walk", null)))

        assertEquals(CaptureDevice.WATCH, resolved.value.capturedOn)
    }

    @Test
    fun `a remote copy without a device keeps the local device when the remote content wins`() {
        val resolution = resolve(text("Walk", CaptureDevice.WATCH), text("Walk by the river", null))

        val resolved = assertIs<ConflictResolution.KeepRemote<JournalNote>>(resolution)
        assertEquals("Walk by the river", (resolved.value as JournalNote.Text).content)
        assertEquals(CaptureDevice.WATCH, resolved.value.capturedOn)
    }

    @Test
    fun `a local copy without a device takes the remote device when the local content wins`() {
        val resolution = resolve(text("Walk by the river", null), text("Walk", CaptureDevice.WATCH))

        val resolved = assertIs<ConflictResolution.KeepLocal<JournalNote>>(resolution)
        assertEquals(CaptureDevice.WATCH, resolved.value.capturedOn)
    }

    @Test
    fun `a merge keeps a device from either copy`() {
        val withLocal = resolve(text("Morning", CaptureDevice.WATCH), text("Evening", null))
        val withRemote = resolve(text("Morning", null), text("Evening", CaptureDevice.WATCH))

        assertEquals(CaptureDevice.WATCH, assertIs<ConflictResolution.Merge<JournalNote>>(withLocal).merged.capturedOn)
        assertEquals(CaptureDevice.WATCH, assertIs<ConflictResolution.Merge<JournalNote>>(withRemote).merged.capturedOn)
    }

    @Test
    fun `an audio note keeps its local device when the remote copy has none`() {
        val local =
            JournalNote.Audio(
                uid = noteId,
                mediaRef = "voice.m4a",
                creationTimestamp = created,
                lastUpdated = created,
                capturedOn = CaptureDevice.WATCH,
            )

        val resolved = assertIs<ConflictResolution.KeepRemote<JournalNote>>(resolve(local, local.copy(capturedOn = null)))
        assertEquals(CaptureDevice.WATCH, resolved.value.capturedOn)
    }
}
