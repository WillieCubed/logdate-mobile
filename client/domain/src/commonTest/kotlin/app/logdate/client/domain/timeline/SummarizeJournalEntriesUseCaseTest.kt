package app.logdate.client.domain.timeline

import app.logdate.client.intelligence.EntrySummarizer
import app.logdate.client.networking.NetworkAvailabilityMonitor
import app.logdate.client.repository.journals.JournalNote
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class SummarizeJournalEntriesUseCaseTest {

    private lateinit var mockSummarizer: MockEntrySummarizer
    private lateinit var mockNetworkMonitor: MockNetworkAvailabilityMonitor
    private lateinit var useCase: SummarizeJournalEntriesUseCase

    @BeforeTest
    fun setUp() {
        mockSummarizer = MockEntrySummarizer()
        mockNetworkMonitor = MockNetworkAvailabilityMonitor()
        useCase = SummarizeJournalEntriesUseCase(
            summarizer = mockSummarizer,
            networkAvailabilityMonitor = mockNetworkMonitor
        )
    }

    @Test
    fun `invoke should return SummaryUnavailable when entries are empty`() = runTest {
        // Given
        val emptyEntries = emptyList<JournalNote>()
        
        // When
        val result = useCase(emptyEntries)
        
        // Then
        assertEquals(SummarizeJournalEntriesResult.SummaryUnavailable, result)
        assertEquals(0, mockSummarizer.summarizeCalls.size)
    }

    @Test
    fun `invoke should return NetworkUnavailable when network is not available`() = runTest {
        // Given
        val testEntries = listOf(createTestNote("Test content"))
        mockNetworkMonitor.isAvailable = false
        
        // When
        val result = useCase(testEntries)
        
        // Then
        assertEquals(SummarizeJournalEntriesResult.NetworkUnavailable, result)
        assertEquals(0, mockSummarizer.summarizeCalls.size)
    }

    @Test
    fun `invoke should return Success when summarization succeeds`() = runTest {
        // Given
        val testEntries = listOf(
            createTestNote("First entry content"),
            createTestNote("Second entry content")
        )
        val expectedSummary = "This is a test summary"
        mockNetworkMonitor.isAvailable = true
        mockSummarizer.summaryResult = expectedSummary
        
        // When
        val result = useCase(testEntries)
        
        // Then
        assertTrue(result is SummarizeJournalEntriesResult.Success)
        assertEquals(expectedSummary, (result as SummarizeJournalEntriesResult.Success).summary)
        assertEquals(1, mockSummarizer.summarizeCalls.size)
    }

    @Test
    fun `invoke should return SummaryUnavailable when summarizer returns null`() = runTest {
        // Given
        val testEntries = listOf(createTestNote("Test content"))
        mockNetworkMonitor.isAvailable = true
        mockSummarizer.summaryResult = null
        
        // When
        val result = useCase(testEntries)
        
        // Then
        assertEquals(SummarizeJournalEntriesResult.SummaryUnavailable, result)
        assertEquals(1, mockSummarizer.summarizeCalls.size)
    }

    @Test
    fun `invoke should return SummaryUnavailable when summarizer throws exception`() = runTest {
        // Given
        val testEntries = listOf(createTestNote("Test content"))
        mockNetworkMonitor.isAvailable = true
        mockSummarizer.shouldThrowException = true
        
        // When
        val result = useCase(testEntries)
        
        // Then
        assertEquals(SummarizeJournalEntriesResult.SummaryUnavailable, result)
        assertEquals(1, mockSummarizer.summarizeCalls.size)
    }

    @Test
    fun `invoke should filter and sort text notes chronologically`() = runTest {
        // Given
        val olderNote = createTestNote("Older note", Clock.System.now())
        val newerNote = createTestNote("Newer note", Clock.System.now())
        val imageNote = JournalNote.Image(
            uid = Uuid.random(),
            creationTimestamp = Clock.System.now(),
            lastUpdated = Clock.System.now(),
            mediaRef = "test_image.jpg"
        )
        
        val testEntries = listOf(newerNote, imageNote, olderNote) // Mixed order and types
        mockNetworkMonitor.isAvailable = true
        mockSummarizer.summaryResult = "Test summary"
        
        // When
        val result = useCase(testEntries)
        
        // Then
        assertTrue(result is SummarizeJournalEntriesResult.Success)
        assertEquals(1, mockSummarizer.summarizeCalls.size)
        
        // Verify that the prompt contains text notes in chronological order
        val summarizeCall = mockSummarizer.summarizeCalls.first()
        assertTrue(summarizeCall.second.contains("Older note"))
        assertTrue(summarizeCall.second.contains("Newer note"))
        // Image note should not be included in text summary
        assertFalse(summarizeCall.second.contains("test_image.jpg"))
    }

    @Test
    fun `invoke should generate consistent hash key for same entries`() = runTest {
        // Given
        val testEntries = listOf(
            createTestNote("Content 1"),
            createTestNote("Content 2")
        )
        mockNetworkMonitor.isAvailable = true
        mockSummarizer.summaryResult = "Test summary"
        
        // When
        useCase(testEntries)
        useCase(testEntries) // Call twice with same entries
        
        // Then
        assertEquals(2, mockSummarizer.summarizeCalls.size)
        // Both calls should have the same summary key
        assertEquals(
            mockSummarizer.summarizeCalls[0].first,
            mockSummarizer.summarizeCalls[1].first
        )
    }

    @Test
    fun `invoke should handle single text note`() = runTest {
        // Given
        val singleNote = createTestNote("Single note content")
        mockNetworkMonitor.isAvailable = true
        mockSummarizer.summaryResult = "Single note summary"
        
        // When
        val result = useCase(listOf(singleNote))
        
        // Then
        assertTrue(result is SummarizeJournalEntriesResult.Success)
        assertEquals("Single note summary", (result as SummarizeJournalEntriesResult.Success).summary)
    }

    private fun createTestNote(content: String, timestamp: kotlinx.datetime.Instant = Clock.System.now()) = JournalNote.Text(
        uid = Uuid.random(),
        content = content,
        creationTimestamp = timestamp,
        lastUpdated = timestamp
    )

    private fun assertFalse(condition: Boolean) {
        kotlin.test.assertFalse(condition)
    }

    private class MockEntrySummarizer : EntrySummarizer {
        val summarizeCalls = mutableListOf<Pair<String, String>>()
        var summaryResult: String? = "Default summary"
        var shouldThrowException = false

        override suspend fun summarize(summaryKey: String, prompt: String): String? {
            summarizeCalls.add(Pair(summaryKey, prompt))
            if (shouldThrowException) {
                throw Exception("Summarizer error")
            }
            return summaryResult
        }
    }

    private class MockNetworkAvailabilityMonitor : NetworkAvailabilityMonitor {
        var isAvailable = true

        override fun isNetworkAvailable(): Boolean = isAvailable
    }
}