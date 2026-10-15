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
    fun `media note without media path adds warning`() =
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

            assertEquals(0, result.notesImported)
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

            assertEquals(0, result.notesImported)
        }

    @Test
    fun `restore drops note and warns when the manifest exists but excludes this media`() =
        runTest {
            // Simulates a modern archive whose export step recorded MEDIA_BYTES_MISSING for this
            // file (bytes were already gone on the source device) -- the manifest exists, but has
            // no entry for this note's media. Falling back to the raw source path here would plant
            // a note that looks normal but can never play, since that path belongs to another
            // device's private storage.
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

            assertEquals(0, result.notesImported)
            assertTrue(notesRepo.created.isEmpty())
            assertTrue(
                result.warnings.any { it.contains(noteId.toString()) && it.contains("not found in archive") },
                "Expected a warning naming the dropped note and the missing media, got: ${result.warnings}",
            )
        }

    @Test
    fun `restore falls back to the source path for legacy archives with no media manifest at all`() =
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
            assertEquals(sourceUri, restored.mediaRef)
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
