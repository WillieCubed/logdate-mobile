package app.logdate.client.data.journals

import app.logdate.client.data.fakes.FakeJournalRepository
import app.logdate.shared.model.Journal
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class OfflineFirstJournalUserDataRepositoryTest {
    private val journalRepository = FakeJournalRepository()
    private val repository = OfflineFirstJournalUserDataRepository(journalRepository)

    @Test
    fun changeFavoritedStatus_updatesExistingJournal() =
        runTest {
            val journal = Journal(title = "Family")
            journalRepository.addJournal(journal)

            repository.changeFavoritedStatus(journal.id.toString(), isFavorite = true)

            assertEquals(true, journalRepository.getJournalById(journal.id)?.isFavorited)
        }

    @Test
    fun changeFavoritedStatus_ignoresMissingJournal() =
        runTest {
            val journal = Journal(title = "Family")

            repository.changeFavoritedStatus(journal.id.toString(), isFavorite = true)

            assertNull(journalRepository.getJournalById(journal.id))
        }
}
