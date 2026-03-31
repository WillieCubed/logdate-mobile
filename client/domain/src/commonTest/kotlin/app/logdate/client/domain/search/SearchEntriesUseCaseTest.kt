package app.logdate.client.domain.search

import app.logdate.client.domain.fakes.FakeSearchRepository
import app.logdate.client.repository.search.SearchContentType
import app.logdate.client.repository.search.SearchResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.uuid.Uuid

class SearchEntriesUseCaseTest {
    private val sampleResults =
        listOf(
            SearchResult(
                uid = Uuid.random(),
                content = "Hiked the sunset trail today.",
                created = Clock.System.now(),
                contentType = SearchContentType.TEXT_NOTE,
            ),
            SearchResult(
                uid = Uuid.random(),
                content = "Voice memo about the hiking plan.",
                created = Clock.System.now(),
                contentType = SearchContentType.TRANSCRIPTION,
            ),
        )

    private val repository = FakeSearchRepository(sampleResults)
    private val useCase = SearchEntriesUseCase(repository)

    @Test
    fun `blank query emits empty list`() =
        runTest {
            val queryFlow = MutableStateFlow(SearchQuery(""))
            val result = useCase(queryFlow).first()
            assertTrue(result.isEmpty())
        }

    @Test
    fun `non-blank query returns matching results`() =
        runTest {
            val queryFlow = MutableStateFlow(SearchQuery("hiking"))
            val result = useCase(queryFlow).first()
            assertEquals(1, result.size)
            assertEquals(sampleResults[1].uid, result.single().uid)
        }

    @Test
    fun `searchWithLimit respects limit`() =
        runTest {
            val queryFlow = MutableStateFlow(SearchQuery("hik"))
            val result = useCase.searchWithLimit(queryFlow, limit = 1).first()
            assertEquals(1, result.size)
        }

    @Test
    fun `query with no matches returns empty list`() =
        runTest {
            val queryFlow = MutableStateFlow(SearchQuery("zzz_no_match"))
            val result = useCase(queryFlow).first()
            assertTrue(result.isEmpty())
        }
}
