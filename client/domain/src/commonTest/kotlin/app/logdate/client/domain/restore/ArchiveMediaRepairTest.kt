package app.logdate.client.domain.restore

import app.logdate.client.domain.restore.RestoreArchiveTestFixtures.FakeMediaImporter
import app.logdate.client.repository.journals.JournalNote
import app.logdate.shared.model.EditorDraft
import app.logdate.shared.model.SerializableTextBlock
import app.logdate.shared.model.SerializableVideoBlock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

internal class ArchiveMediaRepairTest : RestoreArchiveTestFixture() {
    @Test
    fun `a transferred original repairs its placeholder without reopening an archive`() =
        runTest {
            val note = JournalNote.Image(creationTimestamp = now, lastUpdated = now, mediaRef = unavailableArchiveReference("original"))
            notesRepo.existingNotes[note.uid] = note
            val original = "file:///transferred/original.png"
            val pending =
                UnavailableMediaAsset(
                    note.uid.toString(),
                    reference = note.mediaRef,
                    reason = UnavailableMediaReason.NOT_IN_ARCHIVE,
                    sourceReference = original,
                )
            val result =
                RepairArchiveMediaUseCase(notesRepo, journalRepo).repair(
                    listOf(pending),
                    object : MediaImporter {
                        override suspend fun importMedia(exportPath: String): String? = error("No archive is required")

                        override suspend fun isAvailable(reference: String) = reference == original
                    },
                )
            assertEquals(note.copy(mediaRef = original), notesRepo.existingNotes[note.uid])
            assertTrue(result.pending.isEmpty())
        }

    @Test
    fun `attachment retry preserves newer note metadata`() =
        runTest {
            val id = Uuid.random()
            val ref = unavailableArchiveReference(id.toString())
            val note =
                JournalNote.Audio(
                    uid = id,
                    creationTimestamp = now,
                    lastUpdated = now,
                    mediaRef = ref,
                    caption = "Edited caption",
                    durationMs = 1234,
                )
            notesRepo.existingNotes[id] = note
            val asset =
                UnavailableMediaAsset(
                    id.toString(),
                    reference = ref,
                    archivePath = "media/audio.m4a",
                    reason = UnavailableMediaReason.IMPORT_FAILED,
                )
            val result =
                RepairArchiveMediaUseCase(notesRepo, journalRepo).repair(
                    listOf(asset),
                    FakeMediaImporter(
                        mapOf(
                            "media/audio.m4a" to "file:///recovered/audio.m4a",
                        ),
                    ),
                )
            assertEquals(note.copy(mediaRef = "file:///recovered/audio.m4a"), notesRepo.existingNotes[id])
            assertEquals(1, result.imported)
            assertTrue(result.pending.isEmpty())
            assertTrue(notesRepo.removed.isEmpty())
        }

    @Test
    fun `retry patches only the pending role in a reordered edited draft`() =
        runTest {
            val id = Uuid.random()
            val blockId = Uuid.random()
            val ref = unavailableArchiveReference(id.toString(), blockId.toString())
            val video =
                SerializableVideoBlock(blockId, now, uri = ref, thumbnailUri = "file:///accepted/preview.jpg", caption = "Edited caption")
            val text = SerializableTextBlock(Uuid.random(), now, content = "New text")
            val draft = EditorDraft(id, listOf(text, video), listOf(Uuid.random()), now, now)
            journalRepo.savedDrafts.add(draft)
            val asset =
                UnavailableMediaAsset(
                    id.toString(),
                    blockId.toString(),
                    reference = ref,
                    archivePath = "media/video.mp4",
                    reason = UnavailableMediaReason.IMPORT_FAILED,
                )
            val result =
                RepairArchiveMediaUseCase(notesRepo, journalRepo).repair(
                    listOf(asset),
                    FakeMediaImporter(
                        mapOf(
                            "media/video.mp4" to "file:///recovered/video.mp4",
                        ),
                    ),
                )
            assertEquals(
                draft.copy(blocks = listOf(text, video.copy(uri = "file:///recovered/video.mp4"))),
                journalRepo.savedDrafts.single(),
            )
            assertTrue(result.pending.isEmpty())
        }

    @Test
    fun `retry cannot recreate deleted entries or replace a newly attached original`() =
        runTest {
            val deletedId = Uuid.random()
            val existing =
                JournalNote.Image(
                    creationTimestamp = now,
                    lastUpdated = now,
                    mediaRef = "file:///accepted/photo.jpg",
                    caption = "Local",
                )
            notesRepo.existingNotes[existing.uid] = existing
            val pending =
                listOf(deletedId, existing.uid).map {
                    UnavailableMediaAsset(
                        it.toString(),
                        reference = unavailableArchiveReference(it.toString()),
                        archivePath = "media/photo.jpg",
                        reason = UnavailableMediaReason.IMPORT_FAILED,
                    )
                }
            val result =
                RepairArchiveMediaUseCase(notesRepo, journalRepo).repair(
                    pending,
                    object : MediaImporter {
                        override suspend fun importMedia(exportPath: String): String? = error("Obsolete attachment must not be imported")
                    },
                )
            assertEquals<Map<Uuid, JournalNote>>(mapOf(existing.uid to existing), notesRepo.existingNotes)
            assertTrue(result.pending.isEmpty())
            assertEquals(0, result.imported)
        }
}
