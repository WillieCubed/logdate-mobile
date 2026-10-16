package app.logdate.client.domain.restore

import app.logdate.client.domain.export.ExportDraft
import app.logdate.client.domain.export.ExportMediaFile
import app.logdate.client.domain.export.ExportMediaManifest
import app.logdate.client.domain.export.ExportNote
import app.logdate.client.domain.restore.RestoreArchiveTestFixtures.FakeMediaImporter
import app.logdate.client.repository.journals.JournalMergeScope
import app.logdate.client.repository.journals.JournalNote
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.uuid.Uuid

internal class ArchiveRecoveryIdentityTest : RestoreArchiveTestFixture() {
    @Test
    fun `retrying an older archive cannot attach its original to a newer missing-media generation`() =
        runTest {
            val id = Uuid.random()

            fun bundle(
                path: String,
                newer: Boolean,
            ) = buildBundle(
                notes =
                    listOf(
                        ExportNote(
                            id.toString(),
                            "image",
                            mediaPath = path,
                            caption = if (newer) "New caption" else "Old caption",
                            createdAt = now,
                            updatedAt =
                                if (newer) {
                                    now +
                                        1.days
                                } else {
                                    now
                                },
                        ),
                    ),
                mediaManifest = ExportMediaManifest(emptyList()),
            )
            val first =
                useCase.restore(
                    bundle("old-a/photo.jpg", false),
                    mediaImporter = FakeMediaImporter(),
                    mediaRecoveryId = "first-generation",
                )
            val second =
                useCase.restore(
                    bundle("old-b/photo.jpg", true),
                    mediaImporter = FakeMediaImporter(),
                    mediaRecoveryId = "second-generation",
                )
            val accepted = notesRepo.existingNotes[id]
            assertNotEquals(first.unavailableMedia.single().reference, second.unavailableMedia.single().reference)
            val repaired =
                RepairArchiveMediaUseCase(notesRepo, journalRepo).repair(
                    first.unavailableMedia,
                    object : MediaImporter {
                        override suspend fun importMedia(exportPath: String): String? = error("Obsolete generation must not extract bytes")
                    },
                )
            assertTrue(repaired.pending.isEmpty())
            assertEquals(accepted, notesRepo.existingNotes[id])
        }

    @Test
    fun `repair refuses an expected account that differs from repository scope before import`() =
        runTest {
            val id = Uuid.random()
            val ref = unavailableArchiveReference(id.toString())
            notesRepo.existingNotes[id] = JournalNote.Image(id, now, now, ref)
            val asset =
                UnavailableMediaAsset(
                    id.toString(),
                    reference = ref,
                    archivePath = "media/photo.jpg",
                    reason = UnavailableMediaReason.IMPORT_FAILED,
                )
            assertFailsWith<IllegalStateException> {
                RepairArchiveMediaUseCase(
                    notesRepo,
                    journalRepo,
                ).repair(listOf(asset), FakeMediaImporter(), expectedNoteScope = JournalMergeScope("other-owner", "https://other.example"))
            }
            assertEquals(ref, (notesRepo.existingNotes[id] as JournalNote.Image).mediaRef)
        }

    @Test
    fun `replacement archive matches a legacy draft source within its draft and media role`() {
        val id = Uuid.random()
        val source = "old/legacy-photo.jpg"
        val bundle =
            buildBundle(
                drafts =
                    listOf(
                        ExportDraft(id.toString(), content = "", createdAt = now, updatedAt = now, mediaReferences = listOf(source)),
                    ),
                mediaManifest = ExportMediaManifest(listOf(ExportMediaFile(exportPath = "media/replacement.jpg", sourceUri = source))),
            )
        val index = ArchiveRecoveryMediaIndex(RestoreArchiveBundle.V1("", bundle))
        val asset =
            UnavailableMediaAsset(
                id.toString(),
                Uuid.random().toString(),
                reference = "missing",
                reason = UnavailableMediaReason.NOT_IN_ARCHIVE,
                sourceReference = source,
                kind = "camera",
            )
        assertEquals("media/replacement.jpg", index.path(asset))
        assertNull(index.path(asset.copy(entityId = Uuid.random().toString())))
        assertNull(index.path(asset.copy(role = RecoveryMediaRole.THUMBNAIL)))
        assertNull(index.path(asset.copy(kind = "video")))
    }
}
