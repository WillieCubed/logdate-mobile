package app.logdate.client.domain.restore

import app.logdate.client.domain.export.archive.ArchiveBlockType
import app.logdate.client.domain.export.archive.ArchiveCounts
import app.logdate.client.domain.export.archive.ArchiveDraft
import app.logdate.client.domain.export.archive.ArchiveDraftBlock
import app.logdate.client.domain.export.archive.ArchiveDraftFile
import app.logdate.client.domain.export.archive.ArchiveGenerator
import app.logdate.client.domain.export.archive.ArchiveJson
import app.logdate.client.domain.export.archive.ArchiveManifest
import app.logdate.client.domain.export.archive.ArchiveMediaFile
import app.logdate.client.domain.export.archive.ArchiveMediaInventoryEntry
import app.logdate.client.domain.export.archive.ArchiveMediaRef
import app.logdate.client.domain.export.archive.ArchiveMediaStatus
import app.logdate.client.domain.export.archive.ArchiveNote
import app.logdate.client.domain.export.archive.ArchiveNoteFile
import app.logdate.client.domain.export.archive.ArchiveNoteType
import app.logdate.client.domain.export.archive.ArchiveOmissionReason
import app.logdate.client.domain.export.archive.ArchiveOwner
import app.logdate.client.domain.export.archive.ArchivePath
import app.logdate.client.domain.export.archive.ArchiveScope
import app.logdate.client.domain.restore.RestoreArchiveTestFixtures.FakeMediaImporter
import app.logdate.client.repository.journals.JournalNote
import app.logdate.shared.model.SerializableVideoBlock
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

internal class V2PartialMediaRestoreTest : RestoreArchiveTestFixture() {
    @Test
    fun `v2 omissions preserve notes and video metadata while a preview imports independently`() =
        runTest {
            val noteId = Uuid.random()
            val blockId = Uuid.random()
            val draftId = Uuid.random()
            val omitted = ArchiveMediaRef(ArchiveMediaStatus.OMITTED, omittedReason = ArchiveOmissionReason.UNREADABLE)
            val path = ArchivePath.of("media/preview.jpg")
            val preview = ArchiveMediaRef(ArchiveMediaStatus.INCLUDED, path, "image/jpeg")
            val manifest =
                ArchiveManifest(
                    exportedAt = now,
                    exportTimeZone = "UTC",
                    generator = ArchiveGenerator(version = "2.0"),
                    owner = ArchiveOwner("Synthetic"),
                    scope = ArchiveScope(false),
                    counts = ArchiveCounts(0, 1, 1, 1, 0, 0, false),
                    contents = emptyList(),
                )
            val bundle =
                V2RestoreBundle(
                    manifestJson = ArchiveJson.document.encodeToString(manifest),
                    notesJson =
                        ArchiveJson.document.encodeToString(
                            ArchiveNoteFile(
                                listOf(
                                    ArchiveNote(
                                        noteId.toString(),
                                        ArchiveNoteType.IMAGE,
                                        now,
                                        now,
                                        caption = "Retained caption",
                                        media = omitted,
                                    ),
                                ),
                            ),
                        ),
                    draftsJson =
                        ArchiveJson.document.encodeToString(
                            ArchiveDraftFile(
                                listOf(
                                    ArchiveDraft(
                                        draftId.toString(),
                                        createdAt = now,
                                        updatedAt = now,
                                        blocks =
                                            listOf(
                                                ArchiveDraftBlock(
                                                    blockId.toString(),
                                                    ArchiveBlockType.VIDEO,
                                                    now,
                                                    media = omitted,
                                                    thumbnail = preview,
                                                    caption = "Retained video",
                                                ),
                                            ),
                                    ),
                                ),
                            ),
                        ),
                    mediaInventoryJson =
                        ArchiveJson.document.encodeToString(
                            ArchiveMediaFile(
                                listOf(
                                    ArchiveMediaInventoryEntry(path, "image/jpeg", 1, "a".repeat(64)),
                                ),
                            ),
                        ),
                )
            val result = useCase.restore(bundle, mediaImporter = FakeMediaImporter(mapOf(path.value to "file:///new/preview.jpg")))
            assertEquals("Retained caption", assertIs<JournalNote.Image>(notesRepo.created.single()).caption)
            val video =
                assertIs<SerializableVideoBlock>(
                    journalRepo.savedDrafts
                        .single()
                        .blocks
                        .single(),
                )
            assertEquals(blockId, video.id)
            assertEquals("Retained video", video.caption)
            assertTrue(assertNotNull(video.uri).startsWith("logdate-unavailable://archive/"))
            assertEquals("file:///new/preview.jpg", video.thumbnailUri)
            assertEquals(2, result.unavailableMedia.size)
            assertTrue(result.unavailableMedia.all { it.role == RecoveryMediaRole.ORIGINAL })
            assertEquals(1, result.mediaImported)
        }
}
