package app.logdate.client.domain.restore

import app.logdate.client.domain.export.archive.ArchiveCounts
import app.logdate.client.domain.export.archive.ArchiveJournalFile
import app.logdate.client.domain.export.archive.ArchiveJson
import app.logdate.client.domain.export.archive.ArchiveManifest
import app.logdate.client.domain.export.archive.ArchiveMediaFile
import app.logdate.client.domain.export.archive.ArchiveMediaInventoryEntry
import app.logdate.client.domain.export.archive.ArchiveNoteCoordinates
import app.logdate.client.domain.export.archive.ArchiveNoteFile
import app.logdate.client.domain.export.archive.ArchiveNoteLocation
import app.logdate.client.domain.export.archive.ArchiveNoteType
import app.logdate.client.domain.export.archive.ArchivePlaceReference
import app.logdate.client.domain.export.archive.support.ArchiveSamples
import app.logdate.client.repository.journals.JournalNote
import app.logdate.shared.model.Journal
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

internal class CanonicalArchiveLocationValidationTest : RestoreArchiveTestFixture() {
    @Test
    fun invalidSemanticIdentityFailsBeforeAnyDestinationChanges() =
        runTest {
            assertRejectedWithoutChanges(ArchiveNoteLocation(place = ArchivePlaceReference("not-a-uuid")))
        }

    @Test
    fun invalidCoordinatesFailBeforeAnyDestinationChanges() =
        runTest {
            assertRejectedWithoutChanges(ArchiveNoteLocation(coordinates = ArchiveNoteCoordinates(91.0, 0.0)))
        }

    @Test
    fun overflowingAccuracyFailsBeforeAnyDestinationChanges() =
        runTest {
            assertRejectedWithoutChanges(
                ArchiveNoteLocation(coordinates = ArchiveNoteCoordinates(36.1, -115.2, accuracyMeters = Double.MAX_VALUE)),
            )
        }

    @Test
    fun invalidPlaceCenterFailsBeforeAnyDestinationChanges() =
        runTest {
            assertRejectedWithoutChanges(
                ArchiveNoteLocation(place = ArchivePlaceReference(Uuid.random().toString(), latitude = 0.0, longitude = 181.0)),
            )
        }

    @Test
    fun negativeAccuracyFailsBeforeAnyDestinationChanges() =
        runTest {
            assertRejectedWithoutChanges(
                ArchiveNoteLocation(coordinates = ArchiveNoteCoordinates(36.1, -115.2, accuracyMeters = -1.0)),
            )
        }

    @Test
    fun legacyEnvelopeCannotBypassCanonicalLocationValidation() =
        runTest {
            assertRejectedWithoutChanges(ArchiveNoteLocation(place = ArchivePlaceReference("not-a-uuid")), legacyEnvelope = true)
        }

    private suspend fun assertRejectedWithoutChanges(
        location: ArchiveNoteLocation,
        legacyEnvelope: Boolean = false,
    ) {
        val sentinelJournal = Journal(id = Uuid.random(), title = "Keep this journal")
        val sentinelNote = JournalNote.Text(creationTimestamp = now, lastUpdated = now, content = "Keep this local edit")
        journalRepo.existingJournals[sentinelJournal.id] = sentinelJournal
        notesRepo.existingNotes[sentinelNote.uid] = sentinelNote
        val first =
            ArchiveSamples.note.copy(
                id = Uuid.random().toString(),
                type = ArchiveNoteType.TEXT,
                media = null,
                transcript = null,
                transcription = null,
                location = null,
                locationDetails = null,
            )
        val second = ArchiveSamples.note.copy(id = Uuid.random().toString(), locationDetails = location)
        val media = assertNotNull(second.media)
        val path = assertNotNull(media.path)
        val manifest = ArchiveSamples.manifest.copy(counts = ArchiveCounts(1, 2, 0, 1, 0, 0, false))
        val bundle =
            V2RestoreBundle(
                manifestJson = ArchiveJson.document.encodeToString(ArchiveManifest.serializer(), manifest),
                journalsJson = ArchiveJson.document.encodeToString(ArchiveJournalFile.serializer(), ArchiveSamples.journalFile),
                notesJson = ArchiveJson.document.encodeToString(ArchiveNoteFile.serializer(), ArchiveNoteFile(listOf(first, second))),
                mediaInventoryJson =
                    ArchiveJson.document.encodeToString(
                        ArchiveMediaFile.serializer(),
                        ArchiveMediaFile(listOf(ArchiveMediaInventoryEntry(path, assertNotNull(media.mediaType), 1, "a".repeat(64)))),
                    ),
            )
        var importedMedia = 0
        var pendingMedia = 0
        val archive =
            if (legacyEnvelope) {
                RestoreArchiveBundle.V1("", bundle.adaptForRestore("synthetic-owner", "synthetic-device").legacy)
            } else {
                RestoreArchiveBundle.V2("", bundle)
            }
        assertFailsWith<IllegalArgumentException> { ArchivePayloadValidator.validate(archive) }
        val outcome =
            runCatching {
                useCase.restore(
                    archive,
                    mediaImporter =
                        object : MediaImporter {
                            override suspend fun importMedia(exportPath: String): String {
                                importedMedia++
                                return "content://synthetic/restored"
                            }
                        },
                    onPendingMedia = { pendingMedia++ },
                )
            }
        assertEquals(0, journalRepo.created.size)
        assertEquals(0, journalRepo.updated.size)
        assertEquals(0, journalRepo.deleted.size)
        assertEquals(0, notesRepo.created.size)
        assertEquals(0, notesRepo.removed.size)
        assertEquals(0, importedMedia)
        assertEquals(0, pendingMedia)
        assertEquals(mapOf(sentinelJournal.id to sentinelJournal), journalRepo.existingJournals)
        assertEquals<Map<Uuid, JournalNote>>(mapOf(sentinelNote.uid to sentinelNote), notesRepo.existingNotes)
        assertTrue(outcome.isFailure)
    }
}
