package app.logdate.client.domain.restore

import app.logdate.client.domain.export.ExportDraft
import app.logdate.client.domain.export.ExportJournalNoteRelation
import app.logdate.client.domain.export.ExportNote
import app.logdate.client.domain.export.ExportSchemaVersion
import app.logdate.client.domain.export.ExportStats
import app.logdate.client.domain.restore.IntegrityCategory
import app.logdate.shared.model.Journal
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

internal class ArchiveValidationRestoreTest : RestoreArchiveTestFixture() {
    @Test
    fun `invalid UUID in notes adds warning and skips`() =
        runTest {
            val bundle =
                buildBundle(
                    notes =
                        listOf(
                            ExportNote(
                                id = "not-a-uuid",
                                type = "text",
                                content = "Will be skipped",
                                createdAt = now,
                                updatedAt = now,
                            ),
                        ),
                )

            val result = useCase.restore(bundle)

            assertEquals(0, result.notesImported)
            assertEquals(1, result.warnings.size)
            assertTrue(result.warnings.first().contains("Invalid UUID"))
        }

    @Test
    fun `invalid UUID in relations adds warning and skips`() =
        runTest {
            val bundle =
                buildBundle(
                    relations =
                        listOf(
                            ExportJournalNoteRelation("bad-uuid", Uuid.random().toString(), now),
                        ),
                )

            val result = useCase.restore(bundle)

            assertEquals(0, result.journalLinksImported)
            assertTrue(result.warnings.any { it.contains("Invalid UUID") })
        }

    @Test
    fun `unsupported note type adds warning and continues`() =
        runTest {
            val validId = Uuid.random()
            val bundle =
                buildBundle(
                    notes =
                        listOf(
                            ExportNote(
                                id = Uuid.random().toString(),
                                type = "sketch",
                                content = "Future type",
                                createdAt = now,
                                updatedAt = now,
                            ),
                            testTextNote(validId, content = "Valid note"),
                        ),
                )

            val result = useCase.restore(bundle)

            assertEquals(1, result.notesImported)
        }

    @Test
    fun `restore rolls back created entities on failure`() =
        runTest {
            val journalId = Uuid.random()
            val noteId1 = Uuid.random()
            val noteId2 = Uuid.random()

            notesRepo.throwAfterCreates = 1

            val bundle =
                buildBundle(
                    journals = listOf(testJournal(journalId)),
                    notes =
                        listOf(
                            testTextNote(noteId1, content = "First note"),
                            testTextNote(noteId2, content = "Second note will fail"),
                        ),
                )

            val exception = runCatching { useCase.restore(bundle) }.exceptionOrNull()

            assertTrue(exception is RuntimeException, "Should propagate exception")
            assertTrue(journalRepo.deleted.contains(journalId), "Created journal should be rolled back")
            assertTrue(notesRepo.removed.contains(noteId1), "Created note should be rolled back")
        }

    // endregion

    // region Edge cases

    @Test
    fun `empty payloads restore successfully with zero counts`() =
        runTest {
            val bundle = buildBundle()

            val result = useCase.restore(bundle)

            assertEquals(0, result.journalsImported)
            assertEquals(0, result.notesImported)
            assertEquals(0, result.draftsImported)
            assertEquals(0, result.journalLinksImported)
            assertEquals(0, result.mediaImported)
            assertTrue(result.warnings.isEmpty())
        }

    @Test
    fun `restore remains compatible with V1_1 backups`() =
        runTest {
            val journalId = Uuid.random()
            val draftId = Uuid.random()
            val metadata =
                app.logdate.client.domain.export.ExportMetadata(
                    version = ExportSchemaVersion.V1_1,
                    exportDate = now,
                    userId = "test-user",
                    deviceId = "test-device",
                    appVersion = "1.0.0",
                    stats = ExportStats(journalCount = 0, noteCount = 0, draftCount = 1, mediaCount = 0),
                )
            val bundle =
                RestoreBundle(
                    metadataJson = json.encodeToString(metadata),
                    journalsJson = json.encodeToString(mapOf("journals" to emptyList<Journal>())),
                    notesJson = json.encodeToString(mapOf("notes" to emptyList<ExportNote>())),
                    journalNotesJson = json.encodeToString(mapOf("journal_notes" to emptyList<ExportJournalNoteRelation>())),
                    draftsJson =
                        json.encodeToString(
                            mapOf(
                                "drafts" to
                                    listOf(
                                        ExportDraft(
                                            id = draftId.toString(),
                                            journalId = journalId.toString(),
                                            content = "Legacy draft",
                                            createdAt = now,
                                            updatedAt = now,
                                        ),
                                    ),
                            ),
                        ),
                )

            val result = useCase.restore(bundle)

            assertEquals(1, result.draftsImported)
            assertTrue(
                journalRepo.savedDrafts
                    .first()
                    .selectedJournalIds
                    .contains(journalId),
            )
        }

    // endregion

    // region Metadata integrity validation

    @Test
    fun `restore reports mismatches when metadata stats do not match actual archive contents`() =
        runTest {
            val journalId = Uuid.random()
            val noteId = Uuid.random()

            // Metadata claims 5 journals and 10 notes, but archive only has 1 of each
            val bundle =
                buildBundleWithCustomStats(
                    journals = listOf(testJournal(journalId)),
                    notes = listOf(testTextNote(noteId)),
                    stats = ExportStats(journalCount = 5, noteCount = 10, draftCount = 0, mediaCount = 0),
                )

            val result = useCase.restore(bundle)

            assertEquals(2, result.integrityMismatches.size)
            val journalMismatch = result.integrityMismatches.first { it.category == IntegrityCategory.JOURNALS }
            assertEquals(5, journalMismatch.expected)
            assertEquals(1, journalMismatch.actual)
            val noteMismatch = result.integrityMismatches.first { it.category == IntegrityCategory.NOTES }
            assertEquals(10, noteMismatch.expected)
            assertEquals(1, noteMismatch.actual)
        }

    @Test
    fun `restore produces no integrity mismatches when stats match`() =
        runTest {
            val journalId = Uuid.random()

            val bundle =
                buildBundle(
                    journals = listOf(testJournal(journalId)),
                )

            val result = useCase.restore(bundle)

            assertTrue(result.integrityMismatches.isEmpty())
        }

    @Test
    fun `restore rejects archive with unsupported major version`() =
        runTest {
            val bundle = buildBundleWithVersion(ExportSchemaVersion(99, 0))

            val exception =
                kotlin
                    .runCatching { useCase.restore(bundle) }
                    .exceptionOrNull()

            assertTrue(exception is UnsupportedExportVersionException)
            assertEquals(99, exception.archiveVersion.major)
        }

    @Test
    fun `legacy restore rejects v2 metadata without a manifest`() =
        runTest {
            val bundle = buildBundleWithVersion(ExportSchemaVersion.V2_0)

            val exception = runCatching { useCase.restore(bundle) }.exceptionOrNull()

            assertTrue(exception is UnsupportedExportVersionException)
            assertEquals(ExportSchemaVersion.V2_0, exception.archiveVersion)
        }

    // endregion
}
