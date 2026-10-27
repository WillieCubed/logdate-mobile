package app.logdate.client.domain.notes.drafts

import app.logdate.client.repository.journals.EntryDraft
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.uuid.Uuid

class DeleteEntryDraftFailureTest : DeleteEntryDraftTestFixture() {
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
}
