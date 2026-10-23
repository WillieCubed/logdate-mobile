package app.logdate.client.domain.export

import app.logdate.client.domain.export.archive.ArchiveExportOptions
import app.logdate.client.domain.export.archive.ArchiveLayout
import app.logdate.client.domain.export.archive.support.ArchiveExportFixture
import app.logdate.client.domain.export.archive.support.InMemoryArchiveContainer
import app.logdate.client.domain.export.support.RoundTripJournalContentRepository
import app.logdate.client.domain.export.support.RoundTripJournalNotesRepository
import app.logdate.client.domain.export.support.RoundTripJournalRepository
import app.logdate.client.domain.export.support.RoundTripLocationHistoryRepository
import app.logdate.client.domain.export.support.RoundTripProfileRepository
import app.logdate.client.domain.export.support.RoundTripUserPlacesRepository
import app.logdate.client.domain.restore.MediaImporter
import app.logdate.client.domain.restore.RestoreArchiveReader
import app.logdate.client.domain.restore.RestoreTestDeviceIdProvider
import app.logdate.client.domain.restore.RestoreTestOwnerProvider
import app.logdate.client.domain.restore.RestoreUserDataUseCase
import app.logdate.client.domain.restore.sealArchiveIntegrityFixture
import app.logdate.client.repository.journals.CaptureDevice
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Every note in an archive restores whatever its capture device says: an archive written before the
 * device existed, or by a newer version that names a device this one does not know, restores the note
 * with no device.
 */
class CapturedOnArchiveCompatibilityTest : ArchiveExportFixture() {
    @Test
    fun `a note captured on the watch restores as captured on the watch`() =
        runTest {
            notesRepository.testNotes = listOf(audioNote.copy(capturedOn = CaptureDevice.WATCH))

            val restored = exportAndRestore { it }

            assertEquals(CaptureDevice.WATCH, restored.getNoteById(audioNote.uid)?.capturedOn)
        }

    @Test
    fun `an archive written before capture devices restores its notes with none`() =
        runTest {
            notesRepository.testNotes = listOf(audioNote.copy(capturedOn = CaptureDevice.WATCH))

            val restored = exportAndRestore { note -> JsonObject(note - "capturedOn") }

            assertEquals(audioNote.uid, restored.getNoteById(audioNote.uid)?.uid)
            assertNull(restored.getNoteById(audioNote.uid)?.capturedOn)
        }

    @Test
    fun `a device a newer version names restores as unknown`() =
        runTest {
            notesRepository.testNotes = listOf(audioNote.copy(capturedOn = CaptureDevice.WATCH))

            val restored = exportAndRestore { note -> JsonObject(note + ("capturedOn" to JsonPrimitive("tablet"))) }

            assertEquals(audioNote.uid, restored.getNoteById(audioNote.uid)?.uid)
            assertNull(restored.getNoteById(audioNote.uid)?.capturedOn)
        }

    private suspend fun exportAndRestore(editNote: (JsonObject) -> JsonObject): RoundTripJournalNotesRepository {
        val archive = InMemoryArchiveContainer()
        useCase().export(options = ArchiveExportOptions(), container = archive).toList()
        val files = archive.paths.associateWith(archive::bytes).toMutableMap()
        files[ArchiveLayout.NOTES.value] = editNotes(archive.text(ArchiveLayout.NOTES.value), editNote).encodeToByteArray()
        sealArchiveIntegrityFixture(files)
        val bundle = RestoreArchiveReader.readBytes(files.keys, files::get)
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
        ).restore(
            bundle,
            mediaImporter =
                object : MediaImporter {
                    override suspend fun importMedia(exportPath: String): String? =
                        if (archive.has(exportPath)) "file:///restored/${exportPath.substringAfterLast('/')}" else null
                },
        )
        return notes
    }

    private fun editNotes(
        text: String,
        editNote: (JsonObject) -> JsonObject,
    ): String {
        val file = Json.parseToJsonElement(text).jsonObject
        val notes = file.getValue("notes").jsonArray.map { editNote(it.jsonObject) }
        return JsonObject(file + ("notes" to JsonArray(notes))).toString()
    }
}
