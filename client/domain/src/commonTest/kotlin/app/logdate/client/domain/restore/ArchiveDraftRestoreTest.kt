package app.logdate.client.domain.restore

import app.logdate.client.domain.export.ExportDraft
import app.logdate.client.domain.export.ExportMediaManifest
import app.logdate.client.domain.restore.IntegrityCategory
import app.logdate.client.domain.restore.RestoreArchiveTestFixtures.FakeMediaImporter
import app.logdate.shared.model.SerializableAudioBlock
import app.logdate.shared.model.SerializableCameraBlock
import app.logdate.shared.model.SerializableImageBlock
import app.logdate.shared.model.SerializableTextBlock
import app.logdate.shared.model.SerializableVideoBlock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

internal class ArchiveDraftRestoreTest : RestoreArchiveTestFixture() {
    @Test
    fun `restore imports drafts with text and media blocks`() =
        runTest {
            val draftId = Uuid.random()
            val bundle =
                buildBundle(
                    drafts =
                        listOf(
                            testDraft(
                                draftId,
                                content = "Draft text",
                                mediaReferences = listOf("file:///photo.jpg"),
                            ),
                        ),
                )

            val result = useCase.restore(bundle)

            assertEquals(1, result.draftsImported)
            val saved = journalRepo.savedDrafts.first()
            assertTrue(saved.blocks.any { it is SerializableTextBlock })
            assertTrue(saved.blocks.any { it is SerializableCameraBlock })
        }

    @Test
    fun `restore uses full draft blocks payload when present`() =
        runTest {
            val draftId = Uuid.random()
            val bundle =
                buildBundle(
                    drafts =
                        listOf(
                            ExportDraft(
                                id = draftId.toString(),
                                content = "Legacy fallback",
                                createdAt = now,
                                updatedAt = now,
                                blocks =
                                    listOf(
                                        SerializableTextBlock(
                                            id = Uuid.random(),
                                            timestamp = now,
                                            content = "Rich text",
                                        ),
                                        SerializableImageBlock(
                                            id = Uuid.random(),
                                            timestamp = now,
                                            uri = "file:///photo.jpg",
                                            caption = "Caption",
                                        ),
                                        SerializableVideoBlock(
                                            id = Uuid.random(),
                                            timestamp = now,
                                            uri = "file:///clip.mp4",
                                            thumbnailUri = "file:///thumb.jpg",
                                            caption = "Clip",
                                        ),
                                        SerializableAudioBlock(
                                            id = Uuid.random(),
                                            timestamp = now,
                                            uri = "file:///memo.m4a",
                                            duration = 1234L,
                                            transcription = "Memo",
                                        ),
                                    ),
                            ),
                        ),
                )

            useCase.restore(bundle)

            val saved = journalRepo.savedDrafts.first()
            assertEquals(4, saved.blocks.size)
            assertTrue(saved.blocks.any { it is SerializableImageBlock })
            assertTrue(saved.blocks.any { it is SerializableVideoBlock })
            assertTrue(saved.blocks.any { it is SerializableAudioBlock })
        }

    @Test
    fun `restore drops draft audio block and warns when the manifest exists but excludes this media`() =
        runTest {
            // Mirrors "restore drops note and warns when the manifest exists but excludes this
            // media" for the draft-blocks path: restoreDraftBlock used to always keep the block,
            // just with uri set to whatever resolveMediaReference returned (including null),
            // silently persisting a draft that looks normal but can never play.
            val draftId = Uuid.random()
            val blockId = Uuid.random()
            val sourceUri = "/data/user/0/studio.hypertext.logdate/files/audio_notes/recording_abc.m4a"
            val bundle =
                buildBundle(
                    drafts =
                        listOf(
                            ExportDraft(
                                id = draftId.toString(),
                                content = "",
                                createdAt = now,
                                updatedAt = now,
                                blocks =
                                    listOf(
                                        SerializableAudioBlock(
                                            id = blockId,
                                            timestamp = now,
                                            uri = sourceUri,
                                        ),
                                    ),
                            ),
                        ),
                    mediaManifest = ExportMediaManifest(files = emptyList()),
                )

            val result = useCase.restore(bundle, mediaImporter = FakeMediaImporter())

            assertEquals(1, result.draftsImported)
            val saved = journalRepo.savedDrafts.first()
            assertTrue(
                saved.blocks.none { it is SerializableAudioBlock },
                "Unresolvable audio block must be dropped, not persisted with uri=null",
            )
            assertTrue(
                result.warnings.any { it.contains(blockId.toString()) && it.contains("not found in archive") },
                "Expected a warning naming the dropped draft block and the missing media, got: ${result.warnings}",
            )
        }

    @Test
    fun `restore keeps a draft block that never had media to begin with`() =
        runTest {
            // A block with no uri at all (e.g. an in-progress draft) is not "missing media from
            // the archive" -- there was never anything to resolve, so it must not be dropped.
            val draftId = Uuid.random()
            val blockId = Uuid.random()
            val bundle =
                buildBundle(
                    drafts =
                        listOf(
                            ExportDraft(
                                id = draftId.toString(),
                                content = "",
                                createdAt = now,
                                updatedAt = now,
                                blocks =
                                    listOf(
                                        SerializableAudioBlock(
                                            id = blockId,
                                            timestamp = now,
                                            uri = null,
                                        ),
                                    ),
                            ),
                        ),
                    mediaManifest = ExportMediaManifest(files = emptyList()),
                )

            val result = useCase.restore(bundle, mediaImporter = FakeMediaImporter())

            assertEquals(1, result.draftsImported)
            val saved = journalRepo.savedDrafts.first()
            val audioBlock = saved.blocks.filterIsInstance<SerializableAudioBlock>().singleOrNull()
            assertEquals(blockId, audioBlock?.id, "A block with no media to begin with must be kept, not dropped")
        }

    @Test
    fun `includeDrafts false skips draft restoration`() =
        runTest {
            val bundle =
                buildBundle(
                    drafts = listOf(testDraft(Uuid.random(), content = "Should be skipped")),
                )

            val result = useCase.restore(bundle, RestoreOptions(includeDrafts = false))

            assertEquals(0, result.draftsImported)
            assertTrue(journalRepo.savedDrafts.isEmpty())
        }

    @Test
    fun `draft with journalId links to correct journal`() =
        runTest {
            val journalId = Uuid.random()
            val draftId = Uuid.random()
            val bundle =
                buildBundle(
                    drafts = listOf(testDraft(draftId, content = "Linked draft", journalId = journalId.toString())),
                )

            val result = useCase.restore(bundle)

            assertEquals(1, result.draftsImported)
            val saved = journalRepo.savedDrafts.first()
            assertTrue(saved.selectedJournalIds.contains(journalId))
        }

    // endregion

    // region Data coverage: audio duration, location precision, draft multi-journal

    @Test
    fun `restore preserves draft with multiple journal IDs`() =
        runTest {
            val journalId1 = Uuid.random()
            val journalId2 = Uuid.random()
            val draftId = Uuid.random()

            val bundle =
                buildBundle(
                    journals = listOf(testJournal(journalId1), testJournal(journalId2)),
                    drafts =
                        listOf(
                            ExportDraft(
                                id = draftId.toString(),
                                journalIds = listOf(journalId1.toString(), journalId2.toString()),
                                content = "Cross-journal draft",
                                createdAt = now,
                                updatedAt = now,
                            ),
                        ),
                )

            val result = useCase.restore(bundle)

            assertEquals(1, result.draftsImported)
            val saved = journalRepo.savedDrafts.first()
            assertEquals(2, saved.selectedJournalIds.size)
            assertTrue(saved.selectedJournalIds.contains(journalId1))
            assertTrue(saved.selectedJournalIds.contains(journalId2))
        }
}
