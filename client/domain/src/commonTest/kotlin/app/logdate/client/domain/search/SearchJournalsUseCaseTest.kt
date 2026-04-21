package app.logdate.client.domain.search

import app.logdate.client.domain.fakes.FakeJournalRepository
import app.logdate.shared.model.Journal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/**
 * Tests the filtering logic of [SearchJournalsUseCase] for journal-based searches.
 *
 * Ensures that search queries are applied correctly against journal metadata, including
 * case-insensitive title and description matching, handling of empty queries, and
 * partial string matching.
 */
class SearchJournalsUseCaseTest {
    private val tripJournal =
        Journal(
            id = Uuid.random(),
            title = "Summer Trip 2025",
            description = "Our family vacation to the coast",
        )
    private val workJournal =
        Journal(
            id = Uuid.random(),
            title = "Work Notes",
            description = "Daily standup summaries and project ideas",
        )
    private val gardenJournal =
        Journal(
            id = Uuid.random(),
            title = "Garden Log",
            description = "Tracking the summer vegetables",
        )
    private val allJournals = listOf(tripJournal, workJournal, gardenJournal)

    private val repository = FakeJournalRepository(allJournals)
    private val useCase = SearchJournalsUseCase(repository)

    @Test
    fun `blank query returns all journals`() {
        val result = useCase.filterJournals(allJournals, SearchQuery(""))
        assertEquals(allJournals, result)
    }

    @Test
    fun `whitespace-only query returns all journals`() {
        val result = useCase.filterJournals(allJournals, SearchQuery("   "))
        assertEquals(allJournals, result)
    }

    @Test
    fun `matches title case-insensitively`() {
        val result = useCase.filterJournals(allJournals, SearchQuery("summer"))
        assertEquals(2, result.size)
        assertTrue(result.contains(tripJournal))
        assertTrue(result.contains(gardenJournal))
    }

    @Test
    fun `matches description`() {
        val result = useCase.filterJournals(allJournals, SearchQuery("standup"))
        assertEquals(listOf(workJournal), result)
    }

    @Test
    fun `matches partial substring`() {
        val result = useCase.filterJournals(allJournals, SearchQuery("trip"))
        assertEquals(listOf(tripJournal), result)
    }

    @Test
    fun `no matches returns empty list`() {
        val result = useCase.filterJournals(allJournals, SearchQuery("zzz_no_match"))
        assertTrue(result.isEmpty())
    }

    @Test
    fun `matches are case-insensitive for uppercase query`() {
        val result = useCase.filterJournals(allJournals, SearchQuery("WORK"))
        assertEquals(listOf(workJournal), result)
    }

    @Test
    fun `empty journal list returns empty for any query`() {
        val result = useCase.filterJournals(emptyList(), SearchQuery("trip"))
        assertTrue(result.isEmpty())
    }
}
