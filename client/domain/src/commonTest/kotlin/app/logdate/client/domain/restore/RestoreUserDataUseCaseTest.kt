package app.logdate.client.domain.restore

import app.logdate.client.domain.export.ExportDraft
import app.logdate.client.domain.export.ExportJournalNoteRelation
import app.logdate.client.domain.export.ExportNote
import app.logdate.client.domain.restore.IntegrityCategory
import app.logdate.client.domain.restore.RestoreArchiveTestFixtures.FakeSyncableJournalContentRepository
import app.logdate.client.repository.journals.JournalNote
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

internal class RestoreUserDataUseCaseTest : RestoreArchiveTestFixture() {
    @Test
    fun `restore imports journals notes and relations from valid JSON`() =
        runTest {
            val journalId = Uuid.random()
            val noteId = Uuid.random()

            val bundle =
                buildBundle(
                    journals = listOf(testJournal(journalId)),
                    notes = listOf(testTextNote(noteId)),
                    relations = listOf(ExportJournalNoteRelation(journalId.toString(), noteId.toString(), now)),
                )

            val result = useCase.restore(bundle)

            assertEquals(1, result.journalsImported)
            assertEquals(1, result.notesImported)
            assertEquals(1, result.journalLinksImported)
            assertTrue(result.warnings.isEmpty())
            assertEquals(1, journalRepo.created.size)
            assertEquals(1, notesRepo.created.size)
            assertEquals(1, contentRepo.links.size)
        }

    @Test
    fun `restore preserves text note content`() =
        runTest {
            val noteId = Uuid.random()
            val bundle =
                buildBundle(
                    notes = listOf(testTextNote(noteId, content = "Hello from the backup")),
                )

            useCase.restore(bundle)

            val restored = notesRepo.created.first() as JournalNote.Text
            assertEquals("Hello from the backup", restored.content)
            assertEquals(noteId, restored.uid)
        }

    @Test
    fun `restore handles image video and audio notes`() =
        runTest {
            val imageId = Uuid.random()
            val videoId = Uuid.random()
            val audioId = Uuid.random()

            val bundle =
                buildBundle(
                    notes =
                        listOf(
                            ExportNote(
                                id = imageId.toString(),
                                type = "image",
                                mediaPath = "file:///photo.jpg",
                                createdAt = now,
                                updatedAt = now,
                            ),
                            ExportNote(
                                id = videoId.toString(),
                                type = "video",
                                mediaPath = "file:///clip.mp4",
                                createdAt = now,
                                updatedAt = now,
                            ),
                            ExportNote(
                                id = audioId.toString(),
                                type = "audio",
                                mediaPath = "file:///memo.m4a",
                                createdAt = now,
                                updatedAt = now,
                            ),
                        ),
                )

            val result = useCase.restore(bundle)

            assertEquals(3, result.notesImported)
            assertTrue(result.warnings.isEmpty())

            val types = notesRepo.created.map { it::class }
            assertTrue(types.contains(JournalNote.Image::class))
            assertTrue(types.contains(JournalNote.Video::class))
            assertTrue(types.contains(JournalNote.Audio::class))
        }

    @Test
    fun `restore creates journal-note relations`() =
        runTest {
            val j1 = Uuid.random()
            val j2 = Uuid.random()
            val noteId = Uuid.random()

            val bundle =
                buildBundle(
                    journals = listOf(testJournal(j1), testJournal(j2, title = "Second")),
                    notes = listOf(testTextNote(noteId)),
                    relations =
                        listOf(
                            ExportJournalNoteRelation(j1.toString(), noteId.toString(), now),
                            ExportJournalNoteRelation(j2.toString(), noteId.toString(), now),
                        ),
                )

            val result = useCase.restore(bundle)

            assertEquals(2, result.journalLinksImported)
            assertEquals(2, contentRepo.links.size)
            assertTrue(contentRepo.links.any { it.first == noteId && it.second == j1 })
            assertTrue(contentRepo.links.any { it.first == noteId && it.second == j2 })
        }

    @Test
    fun `links from a restored archive are queued for upload rather than left on the device`() =
        runTest {
            // Restore wrote links through the from-sync path, which records the row and queues
            // nothing, because that path is for data arriving *from* the server. An imported
            // archive is the opposite case: the server has never seen it. The links stayed on the
            // device, so entries reached other devices attached to no journal at all. The other
            // tests here cannot catch it - their content repository is not syncable, so they take
            // the branch that does queue.
            val journalId = Uuid.random()
            val noteId = Uuid.random()
            val syncableContent = FakeSyncableJournalContentRepository()
            val syncingUseCase =
                RestoreUserDataUseCase(
                    journalRepo,
                    notesRepo,
                    syncableContent,
                    profileRepo,
                    placesRepo,
                    locationHistoryRepo,
                    RestoreTestOwnerProvider(),
                    RestoreTestDeviceIdProvider(),
                )

            syncingUseCase.restore(
                buildBundle(
                    journals = listOf(testJournal(journalId)),
                    notes = listOf(testTextNote(noteId)),
                    relations = listOf(ExportJournalNoteRelation(journalId.toString(), noteId.toString(), now)),
                ),
            )

            assertTrue(
                syncableContent.unqueuedLinks.isEmpty(),
                "restore must not use the from-sync path for data the server has never seen",
            )
            assertEquals(listOf(noteId to journalId), syncableContent.queuedLinks)
        }

    @Test
    fun `restore preserves sync version on notes`() =
        runTest {
            val noteId = Uuid.random()
            val bundle =
                buildBundle(
                    notes =
                        listOf(
                            ExportNote(
                                id = noteId.toString(),
                                type = "text",
                                content = "Sync aware note",
                                createdAt = now,
                                updatedAt = now,
                                syncVersion = 99,
                            ),
                        ),
                )

            useCase.restore(bundle)

            val restored = notesRepo.created.first()
            assertIs<JournalNote.Text>(restored)
            assertEquals(99, restored.syncVersion)
        }

    @Test
    fun `restore falls back to journalId when journalIds is empty`() =
        runTest {
            val journalId = Uuid.random()
            val draftId = Uuid.random()

            val bundle =
                buildBundle(
                    journals = listOf(testJournal(journalId)),
                    drafts =
                        listOf(
                            ExportDraft(
                                id = draftId.toString(),
                                journalId = journalId.toString(),
                                content = "Legacy draft",
                                createdAt = now,
                                updatedAt = now,
                            ),
                        ),
                )

            val result = useCase.restore(bundle)

            assertEquals(1, result.draftsImported)
            val saved = journalRepo.savedDrafts.first()
            assertEquals(1, saved.selectedJournalIds.size)
            assertTrue(saved.selectedJournalIds.contains(journalId))
        }
}
