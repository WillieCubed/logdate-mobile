package app.logdate.client.domain.notes.drafts

import app.logdate.client.media.storage.StoredMediaReferences
import app.logdate.client.repository.journals.EntryDraft
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.PendingMediaRecord
import app.logdate.client.repository.journals.PendingMediaType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.uuid.Uuid

/**
 * Unit tests for [DeleteEntryDraftUseCase].
 *
 * Validates that the use case correctly triggers the deletion of entry drafts
 * by ID and handles multiple deletion requests or repository errors as expected.
 */
class DeleteEntryDraftUseCaseTest : DeleteEntryDraftTestFixture() {
    @Test
    fun `invoke should delete draft by ID`() =
        runTest {
            // Given
            val draftId = Uuid.random()

            // When
            useCase(draftId)

            // Then
            assertEquals(1, mockRepository.deletedDraftIds.size)
            assertEquals(draftId, mockRepository.deletedDraftIds.first())
        }

    @Test
    fun `invoke should handle multiple deletions`() =
        runTest {
            // Given
            val draftId1 = Uuid.random()
            val draftId2 = Uuid.random()
            val draftId3 = Uuid.random()

            // When
            useCase(draftId1)
            useCase(draftId2)
            useCase(draftId3)

            // Then
            assertEquals(3, mockRepository.deletedDraftIds.size)
            assertTrue(mockRepository.deletedDraftIds.contains(draftId1))
            assertTrue(mockRepository.deletedDraftIds.contains(draftId2))
            assertTrue(mockRepository.deletedDraftIds.contains(draftId3))
        }

    @Test
    fun `invoke removes the draft before cleaning unreferenced audio paths`() =
        runTest {
            val now = Clock.System.now()
            val draftId = Uuid.random()
            val readyAudioPath = "file:///audio_notes/ready.m4a"
            val pendingPath = "file:///audio_notes/pending.m4a"
            mockRepository.seedDraft(
                EntryDraft(
                    id = draftId,
                    notes =
                        listOf(
                            JournalNote.Audio(
                                uid = Uuid.random(),
                                creationTimestamp = now,
                                lastUpdated = now,
                                mediaRef = readyAudioPath,
                            ),
                            JournalNote.Text(
                                uid = Uuid.random(),
                                creationTimestamp = now,
                                lastUpdated = now,
                                content = "no file here",
                            ),
                        ),
                    createdAt = now,
                    updatedAt = now,
                    pendingMedia =
                        listOf(
                            PendingMediaRecord(
                                blockId = Uuid.random(),
                                mediaType = PendingMediaType.AUDIO,
                                createdAt = now,
                                filePath = pendingPath,
                            ),
                            PendingMediaRecord(
                                blockId = Uuid.random(),
                                mediaType = PendingMediaType.AUDIO,
                                createdAt = now,
                                filePath = null,
                            ),
                        ),
                ),
            )

            useCase(draftId)

            assertEquals(setOf(readyAudioPath, pendingPath), mediaCleaner.deletedPaths.toSet())
            assertEquals(listOf(draftId), mockRepository.deletedDraftIds)
            assertEquals(listOf("draft", "media", "media"), operations)
        }

    @Test
    fun `invoke preserves media referenced by another editor draft`() =
        runTest {
            val now = Clock.System.now()
            val sharedPath = "file:///audio_notes/shared.m4a"
            val discarded =
                EntryDraft(
                    id = Uuid.random(),
                    notes = listOf(JournalNote.Audio(mediaRef = sharedPath, creationTimestamp = now, lastUpdated = now)),
                    createdAt = now,
                    updatedAt = now,
                )
            val retained = discarded.copy(id = Uuid.random())
            mockRepository.seedDraft(discarded)
            mockRepository.seedDraft(retained)

            useCase(discarded.id)

            assertEquals(listOf(discarded.id), mockRepository.deletedDraftIds)
            assertFalse(sharedPath in mediaCleaner.deletedPaths)
        }

    @Test
    fun `invoke preserves media when a permanent note uses its portable spelling`() =
        runTest {
            val now = Clock.System.now()
            val rawPath = "file:///audio_notes/shared-alias.m4a"
            val portable = "logdate-media://recordings/shared-alias.m4a"
            val draft =
                EntryDraft(
                    id = Uuid.random(),
                    notes = listOf(JournalNote.Audio(mediaRef = rawPath, creationTimestamp = now, lastUpdated = now)),
                    createdAt = now,
                    updatedAt = now,
                )
            mockRepository.seedDraft(draft)
            notesRepository.seed(JournalNote.Audio(mediaRef = portable, creationTimestamp = now, lastUpdated = now))
            val useCase =
                DeleteEntryDraftUseCase(
                    mockRepository,
                    notesRepository,
                    mediaCleaner,
                    journalCoverUris = { emptyList() },
                    mediaReferences = StoredMediaReferences { if (it == rawPath) portable else it },
                )

            useCase(draft.id)

            assertFalse(rawPath in mediaCleaner.deletedPaths)
        }

    @Test
    fun `invoke removes a draft without media cleanup when its content cannot be read`() =
        runTest {
            val draftId = Uuid.random()
            mockRepository.shouldThrowOnRead = true

            useCase(draftId)

            assertEquals(listOf(draftId), mockRepository.deletedDraftIds)
            assertTrue(mediaCleaner.deletedPaths.isEmpty())
        }

    @Test
    fun `invoke preserves media referenced by a journal cover`() =
        runTest {
            val now = Clock.System.now()
            val sharedPath = "file:///media/cover.jpg"
            val draft =
                EntryDraft(
                    id = Uuid.random(),
                    notes = listOf(JournalNote.Image(mediaRef = sharedPath, creationTimestamp = now, lastUpdated = now)),
                    createdAt = now,
                    updatedAt = now,
                )
            mockRepository.seedDraft(draft)
            coverUris = listOf(sharedPath)

            useCase(draft.id)

            assertEquals(listOf(draft.id), mockRepository.deletedDraftIds)
            assertFalse(sharedPath in mediaCleaner.deletedPaths)
        }

    @Test
    fun `invoke checks fallback recording path before cleanup`() =
        runTest {
            val now = Clock.System.now()
            val draft =
                EntryDraft(
                    id = Uuid.random(),
                    notes = emptyList(),
                    createdAt = now,
                    updatedAt = now,
                    pendingMedia =
                        listOf(
                            PendingMediaRecord(
                                blockId = Uuid.random(),
                                mediaType = PendingMediaType.AUDIO,
                                createdAt = now,
                                fallbackFilePath = "file:///audio_notes/fallback.m4a",
                            ),
                        ),
                )
            mockRepository.seedDraft(draft)

            useCase(draft.id)

            assertEquals(listOf("file:///audio_notes/fallback.m4a"), mediaCleaner.deletedPaths)
        }

    @Test
    fun `invoke does not delete a media file a permanent note still references`() =
        runTest {
            // This is the exact shape of the regression that lost real recordings for four
            // months: SaveEntryUseCase used to call this class's plain discard path right after
            // publishing the draft's notes, deleting the file the brand-new permanent note now
            // depended on. That call site is fixed, but nothing stopped it from happening again
            // at some other call site -- until now: the deletion path itself refuses to touch a
            // path any live note still owns.
            val now = Clock.System.now()
            val draftId = Uuid.random()
            val publishedPath = "file:///audio_notes/published.m4a"
            val orphanedPath = "file:///audio_notes/orphaned.m4a"
            notesRepository.seed(
                JournalNote.Audio(
                    uid = Uuid.random(),
                    creationTimestamp = now,
                    lastUpdated = now,
                    mediaRef = publishedPath,
                ),
            )
            mockRepository.seedDraft(
                EntryDraft(
                    id = draftId,
                    notes =
                        listOf(
                            JournalNote.Audio(
                                uid = Uuid.random(),
                                creationTimestamp = now,
                                lastUpdated = now,
                                mediaRef = publishedPath,
                            ),
                        ),
                    createdAt = now,
                    updatedAt = now,
                    pendingMedia =
                        listOf(
                            PendingMediaRecord(
                                blockId = Uuid.random(),
                                mediaType = PendingMediaType.AUDIO,
                                createdAt = now,
                                filePath = orphanedPath,
                            ),
                        ),
                ),
            )

            useCase(draftId)

            assertFalse(
                publishedPath in mediaCleaner.deletedPaths,
                "a path a permanent note still references must not be deleted",
            )
            assertTrue(orphanedPath in mediaCleaner.deletedPaths, "a genuinely orphaned path should still be cleaned up")
            assertEquals(listOf(draftId), mockRepository.deletedDraftIds, "the draft record itself is still removed")
        }

    @Test
    fun `invoke propagates cleanup cancellation after removing the draft`() =
        runTest {
            val now = Clock.System.now()
            val draftId = Uuid.random()
            val path = "file:///audio_notes/cancelled.m4a"
            mockRepository.seedDraft(
                EntryDraft(
                    id = draftId,
                    notes =
                        listOf(
                            JournalNote.Audio(
                                uid = Uuid.random(),
                                creationTimestamp = now,
                                lastUpdated = now,
                                mediaRef = path,
                            ),
                        ),
                    createdAt = now,
                    updatedAt = now,
                ),
            )
            mediaCleaner.cancellationToThrow = CancellationException("cancelled mid-cleanup")

            assertFailsWith<CancellationException> {
                useCase(draftId)
            }

            assertEquals(listOf(draftId), mockRepository.deletedDraftIds)
            assertTrue(mediaCleaner.deletedPaths.isEmpty())
        }

    @Test
    fun `invoke queries only the candidate media paths instead of scanning all notes`() =
        runTest {
            // pathsStillReferencedByNotes() must ask for exactly the draft's candidate paths via
            // the repository's targeted lookup, not collect allNotesObserved (which would load
            // and map every note in the app for a routine single-draft discard).
            val now = Clock.System.now()
            val draftId = Uuid.random()
            val orphanedPath = "file:///audio_notes/targeted.m4a"
            mockRepository.seedDraft(
                EntryDraft(
                    id = draftId,
                    notes =
                        listOf(
                            JournalNote.Audio(
                                uid = Uuid.random(),
                                creationTimestamp = now,
                                lastUpdated = now,
                                mediaRef = orphanedPath,
                            ),
                        ),
                    createdAt = now,
                    updatedAt = now,
                ),
            )

            useCase(draftId)

            assertEquals(1, notesRepository.notesReferencingMediaPathsCalls)
            assertEquals(setOf(orphanedPath), notesRepository.lastQueriedPaths)
            assertEquals(
                0,
                notesRepository.allNotesObservedCollections,
                "should use the targeted media-path query, not a full scan of allNotesObserved",
            )
            assertTrue(orphanedPath in mediaCleaner.deletedPaths)
        }

    @Test
    fun `invoke deletes orphaned image and video draft media as well as audio`() =
        runTest {
            // collectMediaPaths() used to only look at JournalNote.Audio, so an Image or Video
            // draft block's media file was never added to the deletion-candidate set and leaked
            // on disk forever, even though pathsStillReferencedByNotes() already knew how to
            // protect Image/Video paths that were still owned by a permanent note.
            val now = Clock.System.now()
            val draftId = Uuid.random()
            val imagePath = "file:///media/draft-image.jpg"
            val videoPath = "file:///media/draft-video.mp4"
            mockRepository.seedDraft(
                EntryDraft(
                    id = draftId,
                    notes =
                        listOf(
                            JournalNote.Image(
                                uid = Uuid.random(),
                                creationTimestamp = now,
                                lastUpdated = now,
                                mediaRef = imagePath,
                            ),
                            JournalNote.Video(
                                uid = Uuid.random(),
                                creationTimestamp = now,
                                lastUpdated = now,
                                mediaRef = videoPath,
                            ),
                        ),
                    createdAt = now,
                    updatedAt = now,
                ),
            )

            useCase(draftId)

            assertTrue(imagePath in mediaCleaner.deletedPaths, "orphaned image draft media should be cleaned up")
            assertTrue(videoPath in mediaCleaner.deletedPaths, "orphaned video draft media should be cleaned up")
        }

    @Test
    fun `invoke does not delete image or video media a permanent note still references`() =
        runTest {
            val now = Clock.System.now()
            val draftId = Uuid.random()
            val publishedImagePath = "file:///media/published-image.jpg"
            notesRepository.seed(
                JournalNote.Image(
                    uid = Uuid.random(),
                    creationTimestamp = now,
                    lastUpdated = now,
                    mediaRef = publishedImagePath,
                ),
            )
            mockRepository.seedDraft(
                EntryDraft(
                    id = draftId,
                    notes =
                        listOf(
                            JournalNote.Image(
                                uid = Uuid.random(),
                                creationTimestamp = now,
                                lastUpdated = now,
                                mediaRef = publishedImagePath,
                            ),
                        ),
                    createdAt = now,
                    updatedAt = now,
                ),
            )

            useCase(draftId)

            assertFalse(
                publishedImagePath in mediaCleaner.deletedPaths,
                "an image path a permanent note still references must not be deleted",
            )
        }
}
