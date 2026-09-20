package app.logdate.client.domain.restore

import app.logdate.client.domain.export.ExportSchemaVersion
import app.logdate.client.domain.export.support.RoundTripJournalContentRepository
import app.logdate.client.domain.export.support.RoundTripJournalNotesRepository
import app.logdate.client.domain.export.support.RoundTripJournalRepository
import app.logdate.client.domain.export.support.RoundTripLocationHistoryRepository
import app.logdate.client.domain.export.support.RoundTripProfileRepository
import app.logdate.client.domain.export.support.RoundTripUserPlacesRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.uuid.Uuid

class FrozenV1RestoreCompatibilityTest {
    @Test
    fun `literal wrapped v1 archive remains importable`() =
        runTest {
            val journalId = "00000000-0000-0000-0000-000000000101"
            val noteId = "00000000-0000-0000-0000-000000000102"
            val files =
                mapOf(
                    "LogDate Backup/metadata.json" to
                        """{"version":"1.0","exportDate":"2026-01-01T00:00:00Z","userId":"frozen-user","deviceId":"frozen-device","appVersion":"1.0.0","stats":{"journalCount":1,"noteCount":1,"draftCount":0,"mediaCount":0}}""",
                    "LogDate Backup/journals.json" to
                        """{"journals":[{"id":"$journalId","title":"Frozen Journal","description":"Compatibility fixture","isFavorited":false,"created":"2025-01-01T00:00:00Z","lastUpdated":"2025-01-02T00:00:00Z","syncVersion":0}]}""",
                    "LogDate Backup/notes.json" to
                        """{"notes":[{"id":"$noteId","type":"text","content":"Still readable","createdAt":"2025-01-02T00:00:00Z","updatedAt":"2025-01-02T00:00:00Z"}]}""",
                    "LogDate Backup/journal_notes.json" to
                        """{"journal_notes":[{"journalId":"$journalId","noteId":"$noteId","addedAt":"2025-01-02T00:00:00Z"}]}""",
                    "LogDate Backup/drafts.json" to """{"drafts":[]}""",
                )
            val archive = RestoreArchiveReader.read(files.keys, files::get)
            assertIs<RestoreArchiveBundle.V1>(archive)

            val journals = RoundTripJournalRepository()
            val notes = RoundTripJournalNotesRepository()
            val links = RoundTripJournalContentRepository()
            val result =
                RestoreUserDataUseCase(
                    journals,
                    notes,
                    links,
                    RoundTripProfileRepository(),
                    RoundTripUserPlacesRepository(),
                    RoundTripLocationHistoryRepository(),
                ).restore(archive)

            assertEquals(ExportSchemaVersion.V1_0, result.metadata.version)
            assertEquals("Frozen Journal", journals.getJournalById(Uuid.parse(journalId))?.title)
            assertEquals(
                "Still readable",
                (notes.getNoteById(Uuid.parse(noteId)) as app.logdate.client.repository.journals.JournalNote.Text).content,
            )
            assertEquals(1, links.allLinks().size)
        }
}
