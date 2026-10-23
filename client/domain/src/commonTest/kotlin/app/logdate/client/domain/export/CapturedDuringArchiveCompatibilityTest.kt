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
import app.logdate.client.domain.restore.withExistingCaptureMetadata
import app.logdate.client.repository.journals.CaptureActivity
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
 * Every note in an archive restores what the person was doing when they captured it: an archive written
 * before the activity existed, or by a newer version that names an activity this one does not know,
 * restores the note with none.
 */
class CapturedDuringArchiveCompatibilityTest : ArchiveExportFixture() {
    @Test
    fun `a note captured while moving restores as captured while moving`() =
        runTest {
            notesRepository.testNotes = listOf(audioNote.copy(capturedDuring = CaptureActivity.MOVING))

            val restored = exportAndRestore { it }

            assertEquals(CaptureActivity.MOVING, restored.getNoteById(audioNote.uid)?.capturedDuring)
        }

    @Test
    fun `an archive written before capture activities restores its notes with none`() =
        runTest {
            notesRepository.testNotes = listOf(audioNote.copy(capturedDuring = CaptureActivity.MOVING))

            val restored = exportAndRestore { note -> JsonObject(note - "capturedDuring") }

            assertEquals(audioNote.uid, restored.getNoteById(audioNote.uid)?.uid)
            assertNull(restored.getNoteById(audioNote.uid)?.capturedDuring)
        }

    @Test
    fun `an activity a newer version names restores as unknown`() =
        runTest {
            notesRepository.testNotes = listOf(audioNote.copy(capturedDuring = CaptureActivity.MOVING))

            val restored = exportAndRestore { note -> JsonObject(note + ("capturedDuring" to JsonPrimitive("swimming"))) }

            assertEquals(audioNote.uid, restored.getNoteById(audioNote.uid)?.uid)
            assertNull(restored.getNoteById(audioNote.uid)?.capturedDuring)
        }

    @Test
    fun `restoring over a note that already has an activity keeps it`() {
        val existing = audioNote.copy(capturedDuring = CaptureActivity.IN_VEHICLE)

        assertEquals(CaptureActivity.IN_VEHICLE, audioNote.withExistingCaptureMetadata(existing).capturedDuring)
        assertEquals(
            CaptureActivity.MOVING,
            audioNote.copy(capturedDuring = CaptureActivity.MOVING).withExistingCaptureMetadata(null).capturedDuring,
        )
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
