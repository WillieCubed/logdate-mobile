package app.logdate.client.domain.journals

import app.logdate.client.domain.fakes.FakeJournalRepository
import app.logdate.client.repository.journals.JournalContentRepository
import app.logdate.client.repository.journals.JournalMergeOperation
import app.logdate.client.repository.journals.JournalMergePreview
import app.logdate.client.repository.journals.JournalMergeResult
import app.logdate.client.repository.journals.JournalMergeScope
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalRepository
import app.logdate.client.sync.NoOpSyncManager
import app.logdate.client.sync.SyncManager
import app.logdate.shared.model.Journal
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class MergeJournalsUseCaseTest {
    private val preview = JournalMergePreview(Journal(title = "From"), Journal(title = "Keep"), emptySet(), emptySet())
    private val operationId = Uuid.random()
    private val operation =
        JournalMergeOperation(
            operationId,
            preview.source.id,
            preview.destination.id,
            emptySet(),
            JournalMergeScope("owner", "origin"),
            "From",
            "Keep",
        )
    private val memberships =
        object : JournalContentRepository {
            override fun observeContentForJournal(journalId: Uuid) = flowOf(emptyList<JournalNote>())

            override fun observeJournalsForContent(contentId: Uuid) = flowOf(emptyList<Journal>())

            override fun observeJournalsForContents(contentIds: Set<Uuid>) = flowOf(emptyMap<Uuid, List<Journal>>())

            override suspend fun addContentToJournal(
                contentId: Uuid,
                journalId: Uuid,
            ) = Unit

            override suspend fun removeContentFromJournal(
                contentId: Uuid,
                journalId: Uuid,
            ) = Unit

            override suspend fun addContentToJournals(
                contentId: Uuid,
                journalIds: List<Uuid>,
            ) = Unit

            override suspend fun removeContentFromAllJournals(contentId: Uuid) = Unit
        }

    @Test
    fun `use case pauses sync around existing repository transaction then schedules upload`() =
        runTest {
            var paused = false
            val events = mutableListOf<String>()
            val journals =
                object : JournalRepository by FakeJournalRepository() {
                    override suspend fun merge(
                        preview: JournalMergePreview,
                        operationId: Uuid,
                    ): JournalMergeResult {
                        assertTrue(paused)
                        assertEquals(this@MergeJournalsUseCaseTest.preview, preview)
                        assertEquals(this@MergeJournalsUseCaseTest.operationId, operationId)
                        events += "commit"
                        return JournalMergeResult.Merged(operation)
                    }
                }
            val sync =
                object : SyncManager by NoOpSyncManager {
                    override suspend fun <T> whilePaused(block: suspend () -> T): T {
                        paused = true
                        events += "pause"
                        return try {
                            block()
                        } finally {
                            paused = false
                            events += "resume"
                        }
                    }

                    override fun sync(startNow: Boolean) {
                        assertFalse(paused)
                        assertTrue(startNow)
                        events += "schedule"
                        error("Background sync unavailable")
                    }
                }
            assertIs<JournalMergeResult.Merged>(MergeJournalsUseCase(journals, memberships, sync)(preview, operationId))
            assertEquals(listOf("pause", "commit", "resume", "schedule"), events)
        }

    @Test
    fun `failed transaction cannot schedule a merge upload and cancellation propagates`() =
        runTest {
            var scheduled = false
            var failure: Exception = IllegalStateException("Disk unavailable")
            val journals =
                object : JournalRepository by FakeJournalRepository() {
                    override suspend fun merge(
                        preview: JournalMergePreview,
                        operationId: Uuid,
                    ): JournalMergeResult = throw failure
                }
            val sync =
                object : SyncManager by NoOpSyncManager {
                    override fun sync(startNow: Boolean) {
                        scheduled = true
                    }
                }
            val merge = MergeJournalsUseCase(journals, memberships, sync)
            assertIs<JournalMergeResult.Failed>(merge(preview, operationId))
            assertFalse(scheduled)
            failure = CancellationException("Interrupted")
            assertFailsWith<CancellationException> { merge(preview, operationId) }
            assertFalse(scheduled)
        }
}
