package app.logdate.client.domain.export

import app.logdate.client.domain.export.archive.ArchiveExportProgress
import app.logdate.client.domain.export.archive.support.ArchiveExportFixture
import app.logdate.client.domain.export.archive.support.InMemoryArchiveContainer
import app.logdate.client.domain.export.support.RoundTripJournalContentRepository
import app.logdate.client.domain.export.support.RoundTripJournalNotesRepository
import app.logdate.client.domain.export.support.RoundTripJournalRepository
import app.logdate.client.domain.export.support.RoundTripLocationHistoryRepository
import app.logdate.client.domain.export.support.RoundTripProfileRepository
import app.logdate.client.domain.export.support.RoundTripUserPlacesRepository
import app.logdate.client.domain.restore.MediaImporter
import app.logdate.client.domain.restore.RestoreArchiveBundle
import app.logdate.client.domain.restore.RestoreArchiveReader
import app.logdate.client.domain.restore.RestoreUserDataUseCase
import app.logdate.client.repository.journals.JournalNote
import app.logdate.shared.model.Place
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class V2ExportImportRoundTripTest : ArchiveExportFixture() {
    @Test
    fun `fresh repositories restore a newly exported v2 archive`() =
        runTest {
            val archive = InMemoryArchiveContainer()
            val progress =
                useCase()
                    .export(
                        container = archive,
                        options =
                            app.logdate.client.domain.export.archive
                                .ArchiveExportOptions(),
                    ).toList()
            assertIs<ArchiveExportProgress.Completed>(progress.last())

            val bundle =
                RestoreArchiveReader.read(archive.paths) { path ->
                    if (archive.has(path)) archive.text(path) else null
                }
            assertIs<RestoreArchiveBundle.V2>(bundle)

            val journals = RoundTripJournalRepository()
            val notes = RoundTripJournalNotesRepository()
            val links = RoundTripJournalContentRepository()
            val profile = RoundTripProfileRepository()
            val places = RoundTripUserPlacesRepository()
            val locations = RoundTripLocationHistoryRepository()
            val restore = RestoreUserDataUseCase(journals, notes, links, profile, places, locations)
            val result =
                restore.restore(
                    bundle,
                    mediaImporter =
                        object : MediaImporter {
                            override suspend fun importMedia(exportPath: String): String? =
                                if (archive.has(exportPath)) "file:///restored/${exportPath.substringAfterLast('/')}" else null
                        },
                )

            assertEquals(ExportSchemaVersion.V2_0, result.metadata.version)
            assertEquals(2, result.journalsImported)
            assertEquals(3, result.notesImported)
            assertEquals(1, result.draftsImported)
            assertEquals(3, result.journalLinksImported)
            assertEquals("Sam", profile.profile.displayName)
            assertEquals("Home", (places.places.single() as Place.UserDefined).displayName)
            assertEquals(1, locations.entries.size)
            assertEquals("Asia/Tokyo", (notes.getNoteById(textNote.uid) as JournalNote.Text).timeZoneId)
            assertEquals(
                setOf(daily.id, travel.id),
                links
                    .allLinks()
                    .filter { it.first == textNote.uid }
                    .map { it.second }
                    .toSet(),
            )
            assertTrue((notes.getNoteById(imageNote.uid) as JournalNote.Image).mediaRef.startsWith("file:///restored/"))
            assertTrue(result.warnings.any { "not restored" in it || "Skipped" in it })
        }
}
