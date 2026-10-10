package app.logdate.client.domain.restore

import app.logdate.client.domain.export.support.RoundTripJournalContentRepository
import app.logdate.client.domain.export.support.RoundTripJournalNotesRepository
import app.logdate.client.domain.export.support.RoundTripJournalRepository
import app.logdate.client.domain.export.support.RoundTripLocationHistoryRepository
import app.logdate.client.domain.export.support.RoundTripProfileRepository
import app.logdate.client.domain.export.support.RoundTripUserPlacesRepository
import app.logdate.client.repository.journals.CaptureDevice
import app.logdate.client.repository.journals.JournalNote
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.uuid.Uuid

/**
 * A version 1 archive stores each note as written by the app that exported it. Archives from before
 * capture devices have no such field, and an archive from a newer version may name a device this
 * version does not know. Either way the note restores, with no device unless the archive names one.
 */
class CapturedOnVersionOneRestoreTest {
    private val noteId = Uuid.parse("00000000-0000-0000-0000-000000000202")

    @Test
    fun `a version one archive written before capture devices restores its note with none`() =
        runTest {
            val note = restoreNote(noteJson(device = null))

            assertEquals("Still readable", (note as JournalNote.Text).content)
            assertNull(note.capturedOn)
        }

    @Test
    fun `a version one archive that names the watch restores the watch`() =
        runTest {
            assertEquals(CaptureDevice.WATCH, restoreNote(noteJson(device = "watch")).capturedOn)
        }

    @Test
    fun `a version one archive that names the phone restores the phone`() =
        runTest {
            assertEquals(CaptureDevice.PHONE, restoreNote(noteJson(device = "phone")).capturedOn)
        }

    @Test
    fun `a device a newer version names restores the note with none`() =
        runTest {
            val note = restoreNote(noteJson(device = "tablet"))

            assertEquals("Still readable", (note as JournalNote.Text).content)
            assertNull(note.capturedOn)
        }

    private fun noteJson(device: String?): String {
        val capturedOn = device?.let { ""","capturedOn":"$it"""" }.orEmpty()
        val timestamps = """"createdAt":"2025-01-02T00:00:00Z","updatedAt":"2025-01-02T00:00:00Z""""
        return """{"notes":[{"id":"$noteId","type":"text","content":"Still readable",$timestamps$capturedOn}]}"""
    }

    private suspend fun restoreNote(notesJson: String): JournalNote {
        val files =
            mapOf(
                "LogDate Backup/metadata.json" to
                    """{"version":"1.0","exportDate":"2026-01-01T00:00:00Z","userId":"frozen-user","deviceId":"frozen-device","appVersion":"1.0.0","stats":{"journalCount":0,"noteCount":1,"draftCount":0,"mediaCount":0}}""",
                "LogDate Backup/journals.json" to """{"journals":[]}""",
                "LogDate Backup/notes.json" to notesJson,
                "LogDate Backup/journal_notes.json" to """{"journal_notes":[]}""",
                "LogDate Backup/drafts.json" to """{"drafts":[]}""",
            )
        val archive = RestoreArchiveReader.read(files.keys, files::get)
        assertIs<RestoreArchiveBundle.V1>(archive)
        val notes = RoundTripJournalNotesRepository()
        RestoreUserDataUseCase(
            RoundTripJournalRepository(),
            notes,
            RoundTripJournalContentRepository(),
            RoundTripProfileRepository(),
            RoundTripUserPlacesRepository(),
            RoundTripLocationHistoryRepository(),
            RestoreTestOwnerProvider(),
            RestoreTestDeviceIdProvider(),
        ).restore(archive)
        return checkNotNull(notes.getNoteById(noteId)) { "the note was not restored" }
    }
}
