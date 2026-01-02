package app.logdate.client.intelligence

import app.logdate.client.intelligence.fakes.FakeGenerativeAICache
import app.logdate.client.intelligence.fakes.FakeGenerativeAIChatClient
import app.logdate.client.intelligence.generativeai.GenerativeAIChatMessage
import app.logdate.client.networking.NetworkAvailabilityMonitor
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class EntrySummarizerTest {
    
    private val testDispatcher = StandardTestDispatcher()
    private val fakeCache = FakeGenerativeAICache()
    private val fakeAIClient = FakeGenerativeAIChatClient()
    private val fakeNetworkMonitor = TestNetworkAvailabilityMonitor()
    
    private val entrySummarizer = EntrySummarizer(
        generativeAICache = fakeCache,
        genAIClient = fakeAIClient,
        networkAvailabilityMonitor = fakeNetworkMonitor,
        ioDispatcher = testDispatcher
    )
    
    private fun setup() {
        fakeCache.clear()
        fakeAIClient.clear()
    }

    @Test
    fun summarize_withValidText_generatesNewSummary() = runTest(testDispatcher) {
        setup()
        val summaryId = "test-entry-1"
        val inputText = "Today I went to the park with my dog. It was a beautiful sunny day and we played fetch for an hour."
        val expectedSummary = "You spent a wonderful day at the park with your dog, enjoying the sunshine and playing fetch."
        
        fakeAIClient.setResponseFor(inputText, expectedSummary)
        
        val result = entrySummarizer.summarize(summaryId, inputText, useCached = false)
        
        assertEquals(AIResult.Success(expectedSummary, fromCache = false), result)
        
        // Verify AI client was called with correct messages
        val lastSubmission = fakeAIClient.getLastSubmission()
        assertNotNull(lastSubmission)
        assertEquals(2, lastSubmission.size)
        
        val systemMessage = lastSubmission.find { it.role == "system" }
        assertNotNull(systemMessage)
        assertTrue(systemMessage.content.contains("journal summarizer"))
        assertTrue(systemMessage.content.contains("second person"))
        assertTrue(systemMessage.content.contains("past tense"))
        
        val userMessage = lastSubmission.find { it.role == "user" }
        assertNotNull(userMessage)
        assertEquals(inputText, userMessage.content)
        
        // Verify caching behavior
        assertTrue(fakeCache.putEntryCalls.any { it.first == summaryId && it.second == expectedSummary })
    }

    @Test
    fun summarize_withCachedResponse_returnsCachedSummary() = runTest(testDispatcher) {
        setup()
        val summaryId = "cached-entry"
        val inputText = "Some journal text"
        val cachedSummary = "You had a cached experience."
        
        // Pre-populate cache
        fakeCache.setEntry(summaryId, cachedSummary)
        
        val result = entrySummarizer.summarize(summaryId, inputText, useCached = true)
        
        assertEquals(AIResult.Success(cachedSummary, fromCache = true), result)
        
        // Verify cache was checked but AI client was not called
        assertTrue(fakeCache.getEntryCalls.contains(summaryId))
        assertEquals(0, fakeAIClient.submissions.size)
    }

    @Test
    fun summarize_withCachedDisabled_skipsCache() = runTest(testDispatcher) {
        setup()
        val summaryId = "skip-cache-entry"
        val inputText = "Text to summarize"
        val cachedSummary = "Cached summary"
        val newSummary = "Fresh summary"
        
        // Pre-populate cache
        fakeCache.setEntry(summaryId, cachedSummary)
        fakeAIClient.setResponseFor(inputText, newSummary)
        
        val result = entrySummarizer.summarize(summaryId, inputText, useCached = false)
        
        assertEquals(AIResult.Success(newSummary, fromCache = false), result)
        
        // Verify cache was not checked and AI client was called
        assertEquals(0, fakeCache.getEntryCalls.size)
        assertEquals(1, fakeAIClient.submissions.size)
    }

    @Test
    fun summarize_withNoCachedResponse_generatesNewSummary() = runTest(testDispatcher) {
        setup()
        val summaryId = "no-cache-entry"
        val inputText = "Today I learned something new about programming."
        val expectedSummary = "You discovered new programming knowledge today."
        
        fakeAIClient.setResponseFor(inputText, expectedSummary)
        
        val result = entrySummarizer.summarize(summaryId, inputText, useCached = true)
        
        assertEquals(AIResult.Success(expectedSummary, fromCache = false), result)
        
        // Verify cache was checked first, then AI client was called
        assertTrue(fakeCache.getEntryCalls.contains(summaryId))
        assertEquals(1, fakeAIClient.submissions.size)
        assertTrue(fakeCache.putEntryCalls.any { it.first == summaryId && it.second == expectedSummary })
    }

    @Test
    fun summarize_withAIClientReturningNull_returnsFallbackMessage() = runTest(testDispatcher) {
        setup()
        val summaryId = "null-response-entry"
        val inputText = "Some text"
        
        fakeAIClient.defaultResponse = null
        
        val result = entrySummarizer.summarize(summaryId, inputText, useCached = false)
        
        assertTrue(result is AIResult.Error)
        assertEquals(1, fakeAIClient.submissions.size)
    }

    @Test
    fun summarize_withAIClientError_returnsNull() = runTest(testDispatcher) {
        setup()
        val summaryId = "error-entry"
        val inputText = "Some text"
        
        fakeAIClient.shouldThrowError = true
        fakeAIClient.errorToThrow = Exception("Network error")
        
        val result = entrySummarizer.summarize(summaryId, inputText, useCached = false)
        
        assertTrue(result is AIResult.Error)
        assertEquals(1, fakeAIClient.submissions.size)
        
        // Verify error doesn't get cached
        assertEquals(0, fakeCache.putEntryCalls.size)
    }

    @Test
    fun summarize_withEmptyText_handlesGracefully() = runTest(testDispatcher) {
        setup()
        val summaryId = "empty-text-entry"
        val inputText = ""
        val expectedSummary = "You didn't write anything today."
        
        fakeAIClient.setResponseFor(inputText, expectedSummary)
        
        val result = entrySummarizer.summarize(summaryId, inputText, useCached = false)
        
        assertEquals(AIResult.Success(expectedSummary, fromCache = false), result)
        
        val userMessage = fakeAIClient.getLastUserMessage()
        assertEquals("", userMessage)
    }

    @Test
    fun summarize_withLongText_processesCorrectly() = runTest(testDispatcher) {
        setup()
        val summaryId = "long-text-entry"
        val inputText = """
            Today was an incredible day. I woke up early and went for a run in the park. 
            The weather was perfect - not too hot, not too cold. After my run, I met up 
            with Sarah and John at the coffee shop downtown. We discussed our upcoming 
            project and made some great progress. Later, I attended a virtual meeting 
            with my team where we reviewed the quarterly results. The numbers looked 
            really promising. In the evening, I cooked dinner for my family and we 
            watched a movie together. It was such a fulfilling day with good exercise, 
            productive work, and quality time with loved ones.
        """.trimIndent()
        val expectedSummary = "You had a productive and fulfilling day with exercise, work progress, and family time."
        
        fakeAIClient.setResponseFor(inputText, expectedSummary)
        
        val result = entrySummarizer.summarize(summaryId, inputText, useCached = false)
        
        assertEquals(AIResult.Success(expectedSummary, fromCache = false), result)
        
        val userMessage = fakeAIClient.getLastUserMessage()
        assertEquals(inputText, userMessage)
    }

    @Test
    fun summarize_systemPromptIsCorrect() = runTest(testDispatcher) {
        setup()
        val summaryId = "prompt-test"
        val inputText = "Test text"
        
        fakeAIClient.defaultResponse = "Test response"
        
        entrySummarizer.summarize(summaryId, inputText, useCached = false)
        
        val systemMessage = fakeAIClient.getLastSystemMessage()
        assertNotNull(systemMessage)
        
        // Verify key requirements in system prompt
        assertTrue(systemMessage.contains("journal summarizer"))
        assertTrue(systemMessage.contains("second person"))
        assertTrue(systemMessage.contains("past tense"))
        assertTrue(systemMessage.contains("one or two-sentence"))
    }

    @Test
    fun summarize_multipleEntries_cachesIndependently() = runTest(testDispatcher) {
        setup()
        val entry1Id = "entry-1"
        val entry1Text = "First entry text"
        val entry1Summary = "You wrote your first entry."
        
        val entry2Id = "entry-2"
        val entry2Text = "Second entry text"
        val entry2Summary = "You wrote your second entry."
        
        fakeAIClient.setResponseFor(entry1Text, entry1Summary)
        fakeAIClient.setResponseFor(entry2Text, entry2Summary)
        
        val result1 = entrySummarizer.summarize(entry1Id, entry1Text, useCached = false)
        val result2 = entrySummarizer.summarize(entry2Id, entry2Text, useCached = false)
        
        assertEquals(AIResult.Success(entry1Summary, fromCache = false), result1)
        assertEquals(AIResult.Success(entry2Summary, fromCache = false), result2)
        
        // Verify both entries were cached independently
        assertTrue(fakeCache.hasEntry(entry1Id))
        assertTrue(fakeCache.hasEntry(entry2Id))
        assertEquals(2, fakeAIClient.submissions.size)
    }

    private class TestNetworkAvailabilityMonitor(
        private var available: Boolean = true
    ) : NetworkAvailabilityMonitor {
        override fun isNetworkAvailable(): Boolean = available

        override fun observeNetwork() = throw UnsupportedOperationException("Not used in tests")
    }
}
