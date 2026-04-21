package app.logdate.client.domain.notes.drafts

import app.logdate.client.media.MediaCleaner
import app.logdate.client.repository.journals.EntryDraft
import app.logdate.client.repository.journals.EntryDraftRepository
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.PendingMediaRecord
import app.logdate.client.repository.journals.PendingMediaType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.uuid.Uuid

/**
 * Unit tests for [DeleteEntryDraftUseCase].
 *
 * Validates that the use case correctly triggers the deletion of entry drafts
 * by ID and handles multiple deletion requests or repository errors as expected.
 */
class DeleteEntryDraftUseCaseTest {
    private lateinit var mockRepository: MockEntryDraftRepository
    private lateinit var useCase: DeleteEntryDraftUseCase

    private lateinit var mediaCleaner: RecordingMediaCleaner

    @BeforeTest
    fun setUp() {
        mockRepository = MockEntryDraftRepository()
        mediaCleaner = RecordingMediaCleaner()
        useCase =
            DeleteEntryDraftUseCase(
                entryDraftRepository = mockRepository,
                mediaCleaner = mediaCleaner,
            )
    }

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
    fun `invoke deletes audio mediaRefs and pendingMedia paths before removing draft`() =
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
        }

    @Test
    fun `invoke proceeds with draft deletion even if media cleanup fails`() =
        runTest {
            val draftId = Uuid.random()
            mockRepository.seedDraft(
                EntryDraft(
                    id = draftId,
                    notes = emptyList(),
                    createdAt = Clock.System.now(),
                    updatedAt = Clock.System.now(),
                ),
            )
            mediaCleaner.shouldThrow = true

            useCase(draftId)

            assertEquals(listOf(draftId), mockRepository.deletedDraftIds)
        }

    @Test
    fun `invoke should handle repository errors gracefully`() =
        runTest {
            // Given
            val draftId = Uuid.random()
            mockRepository.shouldThrowException = true

            // When/Then
            try {
                useCase(draftId)
                kotlin.test.fail("Expected exception was not thrown")
            } catch (e: Exception) {
                assertEquals("Repository error", e.message)
            }
        }

    private class MockEntryDraftRepository : EntryDraftRepository {
        val deletedDraftIds = mutableListOf<Uuid>()
        var shouldThrowException = false
        private val seeded = mutableMapOf<Uuid, EntryDraft>()

        fun seedDraft(draft: EntryDraft) {
            seeded[draft.id] = draft
        }

        override suspend fun setPendingMedia(
            uid: Uuid,
            pendingMedia: List<PendingMediaRecord>,
        ) = Unit

        override suspend fun deleteDraft(uid: Uuid) {
            if (shouldThrowException) {
                throw Exception("Repository error")
            }
            deletedDraftIds.add(uid)
        }

        override fun getDrafts(): Flow<List<EntryDraft>> = flowOf(seeded.values.toList())

        override fun getDraft(uid: Uuid): Flow<Result<EntryDraft>> {
            val draft = seeded[uid]
            return if (draft != null) {
                flowOf(Result.success(draft))
            } else {
                flowOf(Result.failure(NoSuchElementException()))
            }
        }

        override suspend fun createDraft(notes: List<JournalNote>): Uuid = Uuid.random()

        override suspend fun updateDraft(
            uid: Uuid,
            notes: List<JournalNote>,
        ): Uuid = uid

        override suspend fun deleteAllDrafts() {}

        override suspend fun deleteExpiredDrafts(maxAge: kotlin.time.Duration): Int = 0
    }

    private class RecordingMediaCleaner : MediaCleaner {
        val deletedPaths = mutableListOf<String>()
        var shouldThrow = false

        override suspend fun delete(path: String) {
            if (shouldThrow) throw IllegalStateException("simulated cleaner failure")
            deletedPaths.add(path)
        }
    }
}
