package app.logdate.client.domain.restore

import app.logdate.client.domain.export.ExportMediaFile
import app.logdate.client.domain.export.ExportMediaManifest
import app.logdate.client.domain.export.ExportNote
import app.logdate.client.domain.restore.IntegrityCategory
import app.logdate.client.domain.restore.RestoreArchiveTestFixtures.FakeMediaImporter
import app.logdate.client.repository.journals.JournalNote
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

internal class ArchiveMediaRestoreTest : RestoreArchiveTestFixture() {
    @Test
    fun `media note without an original retains its metadata and unavailable state`() =
        runTest {
            val bundle =
                buildBundle(
                    notes =
                        listOf(
                            ExportNote(
                                id = Uuid.random().toString(),
                                type = "image",
                                mediaPath = null,
                                createdAt = now,
                                updatedAt = now,
                            ),
                        ),
                )

            val result = useCase.restore(bundle)

            assertEquals(1, result.notesImported)
            assertTrue(assertIs<JournalNote.Image>(notesRepo.created.single()).mediaRef.startsWith("logdate-unavailable://archive/"))
            assertEquals(UnavailableMediaReason.NOT_IN_ARCHIVE, result.unavailableMedia.single().reason)
        }

    // endregion

    // region Media import

    @Test
    fun `MediaImporter is invoked for media notes`() =
        runTest {
            val mediaImporter = FakeMediaImporter(mapOf("file:///photo.jpg" to "content://imported/1"))

            val bundle =
                buildBundle(
                    notes =
                        listOf(
                            ExportNote(
                                id = Uuid.random().toString(),
                                type = "image",
                                mediaPath = "file:///photo.jpg",
                                createdAt = now,
                                updatedAt = now,
                            ),
                        ),
                )

            val result = useCase.restore(bundle, mediaImporter = mediaImporter)

            assertEquals(1, result.notesImported)
            assertEquals(1, result.mediaImported)
            val restored = notesRepo.created.first() as JournalNote.Image
            assertEquals("content://imported/1", restored.mediaRef)
        }

    @Test
    fun `media manifest maps source URIs to export paths`() =
        runTest {
            val sourceUri = "content://media/external/images/123"
            val exportPath = "media/2026/photo_abc123.jpg"
            val importedUri = "content://imported/resolved"

            val mediaImporter = FakeMediaImporter(mapOf(exportPath to importedUri))

            val bundle =
                buildBundle(
                    notes =
                        listOf(
                            ExportNote(
                                id = Uuid.random().toString(),
                                type = "image",
                                mediaPath = sourceUri,
                                createdAt = now,
                                updatedAt = now,
                            ),
                        ),
                    mediaManifest =
                        ExportMediaManifest(
                            files =
                                listOf(
                                    ExportMediaFile(
                                        sourceUri = sourceUri,
                                        exportPath = exportPath,
                                    ),
                                ),
                        ),
                )

            val result = useCase.restore(bundle, mediaImporter = mediaImporter)

            assertEquals(1, result.mediaImported)
            val restored = notesRepo.created.first() as JournalNote.Image
            assertEquals(importedUri, restored.mediaRef)
        }

    @Test
    fun `restore warns when archived media cannot be imported`() =
        runTest {
            val sourceUri = "content://media/external/images/123"
            val exportPath = "media/2026/photo_abc123.jpg"
            val bundle =
                buildBundle(
                    notes =
                        listOf(
                            ExportNote(
                                id = Uuid.random().toString(),
                                type = "image",
                                mediaPath = sourceUri,
                                createdAt = now,
                                updatedAt = now,
                            ),
                        ),
                    mediaManifest =
                        ExportMediaManifest(
                            files =
                                listOf(
                                    ExportMediaFile(
                                        sourceUri = sourceUri,
                                        exportPath = exportPath,
                                    ),
                                ),
                        ),
                )

            val result = useCase.restore(bundle, mediaImporter = FakeMediaImporter())

            assertEquals(1, result.notesImported)
            assertEquals(UnavailableMediaReason.IMPORT_FAILED, result.unavailableMedia.single().reason)
        }

    @Test
    fun `restore retains a note when the archive excludes its original`() =
        runTest {
            val noteId = Uuid.random()
            val sourceUri = "/data/user/0/studio.hypertext.logdate/files/audio_notes/recording_abc.m4a"
            val bundle =
                buildBundle(
                    notes =
                        listOf(
                            ExportNote(
                                id = noteId.toString(),
                                type = "audio",
                                mediaPath = sourceUri,
                                createdAt = now,
                                updatedAt = now,
                            ),
                        ),
                    mediaManifest = ExportMediaManifest(files = emptyList()),
                )

            val result = useCase.restore(bundle, mediaImporter = FakeMediaImporter())

            assertEquals(1, result.notesImported)
            assertEquals(result.unavailableMedia.single().reference, assertIs<JournalNote.Audio>(notesRepo.created.single()).mediaRef)
            assertEquals(noteId.toString(), result.unavailableMedia.single().entityId)
            assertEquals(UnavailableMediaReason.NOT_IN_ARCHIVE, result.unavailableMedia.single().reason)
        }

    @Test
    fun `legacy archive preserves an unavailable original when import fails`() =
        runTest {
            // Pre-manifest archive format: there is no way to know whether this media was ever
            // recorded as present, so the best-effort fallback is preserved rather than dropping
            // notes we might actually be able to resolve.
            val noteId = Uuid.random()
            val sourceUri = "file:///legacy/audio.m4a"
            val bundle =
                buildBundle(
                    notes =
                        listOf(
                            ExportNote(
                                id = noteId.toString(),
                                type = "audio",
                                mediaPath = sourceUri,
                                createdAt = now,
                                updatedAt = now,
                            ),
                        ),
                    mediaManifest = null,
                )

            val result = useCase.restore(bundle, mediaImporter = FakeMediaImporter())

            assertEquals(1, result.notesImported)
            val restored = notesRepo.created.first()
            assertIs<JournalNote.Audio>(restored)
            assertEquals(result.unavailableMedia.single().reference, restored.mediaRef)
            assertEquals(UnavailableMediaReason.IMPORT_FAILED, result.unavailableMedia.single().reason)
        }

    // endregion

    // region Caption preservation

    @Test
    fun `restore preserves caption on image note`() =
        runTest {
            val noteId = Uuid.random()
            val bundle =
                buildBundle(
                    notes =
                        listOf(
                            ExportNote(
                                id = noteId.toString(),
                                type = "image",
                                mediaPath = "file:///photo.jpg",
                                caption = "Sunset at the beach",
                                createdAt = now,
                                updatedAt = now,
                            ),
                        ),
                )

            useCase.restore(bundle)

            val restored = notesRepo.created.first() as JournalNote.Image
            assertEquals("Sunset at the beach", restored.caption)
        }

    @Test
    fun `restore preserves caption on video note`() =
        runTest {
            val noteId = Uuid.random()
            val bundle =
                buildBundle(
                    notes =
                        listOf(
                            ExportNote(
                                id = noteId.toString(),
                                type = "video",
                                mediaPath = "file:///clip.mp4",
                                caption = "Birthday party",
                                createdAt = now,
                                updatedAt = now,
                            ),
                        ),
                )

            useCase.restore(bundle)

            val restored = notesRepo.created.first() as JournalNote.Video
            assertEquals("Birthday party", restored.caption)
        }

    @Test
    fun `restore uses empty caption when export has no caption`() =
        runTest {
            val noteId = Uuid.random()
            val bundle =
                buildBundle(
                    notes =
                        listOf(
                            ExportNote(
                                id = noteId.toString(),
                                type = "image",
                                mediaPath = "file:///photo.jpg",
                                caption = null,
                                createdAt = now,
                                updatedAt = now,
                            ),
                        ),
                )

            useCase.restore(bundle)

            val restored = notesRepo.created.first() as JournalNote.Image
            assertEquals("", restored.caption)
        }

    // endregion

    // region Rollback

    @Test
    fun `restore preserves audio duration from ExportNote`() =
        runTest {
            val noteId = Uuid.random()
            val bundle =
                buildBundle(
                    notes =
                        listOf(
                            ExportNote(
                                id = noteId.toString(),
                                type = "audio",
                                mediaPath = "media/audio.m4a",
                                durationMs = 42_500L,
                                createdAt = now,
                                updatedAt = now,
                            ),
                        ),
                )

            val result = useCase.restore(bundle)

            assertEquals(1, result.notesImported)
            val restored = notesRepo.created.first()
            assertIs<JournalNote.Audio>(restored)
            assertEquals(42_500L, restored.durationMs)
        }
}
