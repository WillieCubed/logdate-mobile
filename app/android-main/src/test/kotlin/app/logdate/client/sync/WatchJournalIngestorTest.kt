package app.logdate.client.sync

import app.logdate.client.database.dao.journals.JournalContentDao
import app.logdate.client.repository.journals.JournalContentRepository
import app.logdate.client.repository.journals.JournalRepository
import app.logdate.shared.model.Journal
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Tests [WatchJournalIngestor], which applies journals and journal links from the watch through the
 * phone's own repository calls, so each one is queued for cloud backup.
 */
class WatchJournalIngestorTest {
    private val journalRepository = mockk<JournalRepository>(relaxed = true)
    private val contentRepository = mockk<JournalContentRepository>(relaxed = true)
    private val journalContentDao = mockk<JournalContentDao>(relaxed = true)
    private val ingestor = WatchJournalIngestor(journalRepository, contentRepository, journalContentDao)

    private val journalId = Uuid.parse("770e8400-e29b-41d4-a716-446655440000")
    private val noteId = Uuid.parse("550e8400-e29b-41d4-a716-446655440000")
    private val earlier = Instant.fromEpochMilliseconds(1_710_000_000_000)
    private val later = Instant.fromEpochMilliseconds(1_710_000_060_000)

    @Test
    fun `a new journal from the watch is created and queued for backup`() =
        runTest {
            val journal = Journal(id = journalId, title = "Walks", created = earlier, lastUpdated = earlier)
            coEvery { journalRepository.getJournalById(journalId) } returns null

            ingestor.onJournal(journal)

            coVerify(exactly = 1) { journalRepository.create(journal) }
        }

    @Test
    fun `a newer copy of a journal updates it and queues the update`() =
        runTest {
            coEvery { journalRepository.getJournalById(journalId) } returns
                Journal(id = journalId, title = "Walks", created = earlier, lastUpdated = earlier, syncVersion = 7)
            val incoming = Journal(id = journalId, title = "Evening walks", created = earlier, lastUpdated = later)

            ingestor.onJournal(incoming)

            coVerify(exactly = 1) { journalRepository.update(incoming.copy(syncVersion = 7)) }
        }

    @Test
    fun `a repeated or older copy of a journal changes nothing`() =
        runTest {
            val existing = Journal(id = journalId, title = "Walks", created = earlier, lastUpdated = later)
            coEvery { journalRepository.getJournalById(journalId) } returns existing

            ingestor.onJournal(existing)
            ingestor.onJournal(existing.copy(title = "Old title", lastUpdated = earlier))

            coVerify(exactly = 0) { journalRepository.create(any()) }
            coVerify(exactly = 0) { journalRepository.update(any()) }
        }

    @Test
    fun `a journal deleted on the watch is deleted and the deletion queued`() =
        runTest {
            coEvery { journalRepository.getJournalById(journalId) } returns
                Journal(id = journalId, created = earlier, lastUpdated = earlier)

            ingestor.onJournalDeleted(journalId)

            coVerify(exactly = 1) { journalRepository.delete(journalId) }
        }

    @Test
    fun `deleting a journal the phone does not have changes nothing`() =
        runTest {
            coEvery { journalRepository.getJournalById(journalId) } returns null

            ingestor.onJournalDeleted(journalId)

            coVerify(exactly = 0) { journalRepository.delete(any()) }
        }

    @Test
    fun `a new link from the watch is added and queued for backup`() =
        runTest {
            coEvery { journalContentDao.isContentInJournal(journalId, noteId) } returns false

            ingestor.onAssociation(journalId, noteId)

            coVerify(exactly = 1) { contentRepository.addContentToJournal(noteId, journalId) }
        }

    @Test
    fun `a link the phone already has changes nothing`() =
        runTest {
            coEvery { journalContentDao.isContentInJournal(journalId, noteId) } returns true

            ingestor.onAssociation(journalId, noteId)

            coVerify(exactly = 0) { contentRepository.addContentToJournal(any(), any()) }
        }

    @Test
    fun `a link removed on the watch is removed and the removal queued`() =
        runTest {
            coEvery { journalContentDao.isContentInJournal(journalId, noteId) } returns true

            ingestor.onAssociationDeleted(journalId, noteId)

            coVerify(exactly = 1) { contentRepository.removeContentFromJournal(noteId, journalId) }
        }

    @Test
    fun `removing a link the phone does not have changes nothing`() =
        runTest {
            coEvery { journalContentDao.isContentInJournal(journalId, noteId) } returns false

            ingestor.onAssociationDeleted(journalId, noteId)

            coVerify(exactly = 0) { contentRepository.removeContentFromJournal(any(), any()) }
        }
}
