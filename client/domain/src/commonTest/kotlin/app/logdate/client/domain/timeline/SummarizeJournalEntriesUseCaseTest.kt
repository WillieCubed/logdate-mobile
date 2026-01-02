package app.logdate.client.domain.timeline

import app.logdate.client.intelligence.EntrySummarizer
import app.logdate.client.intelligence.cache.AICacheKeyInput
import app.logdate.client.intelligence.cache.DefaultAICacheKeyStrategy
import app.logdate.client.intelligence.cache.GenerativeAICache
import app.logdate.client.intelligence.cache.GenerativeAICacheEntry
import app.logdate.client.intelligence.cache.GenerativeAICacheEntryMetadata
import app.logdate.client.intelligence.cache.GenerativeAICacheRequest
import app.logdate.client.intelligence.AIError
import app.logdate.client.intelligence.AIResult
import app.logdate.client.intelligence.generativeai.GenerativeAIChatClient
import app.logdate.client.intelligence.generativeai.GenerativeAIChatMessage
import app.logdate.client.intelligence.generativeai.GenerativeAIRequest
import app.logdate.client.intelligence.generativeai.GenerativeAIResponse
import app.logdate.client.networking.NetworkAvailabilityMonitor
import app.logdate.client.networking.NetworkState
import app.logdate.client.repository.journals.JournalNote
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.Duration.Companion.seconds
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class SummarizeJournalEntriesUseCaseTest {

    private lateinit var fakeCache: FakeGenerativeAICache
    private lateinit var fakeChatClient: FakeGenerativeAIChatClient
    private lateinit var mockNetworkMonitor: MockNetworkAvailabilityMonitor
    private lateinit var useCase: SummarizeJournalEntriesUseCase

    @BeforeTest
    fun setUp() {
        fakeCache = FakeGenerativeAICache()
        fakeChatClient = FakeGenerativeAIChatClient()
        mockNetworkMonitor = MockNetworkAvailabilityMonitor()
        useCase = SummarizeJournalEntriesUseCase(
            summarizer = EntrySummarizer(
                generativeAICache = fakeCache,
                genAIClient = fakeChatClient,
                networkAvailabilityMonitor = mockNetworkMonitor
            )
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
        assertEquals(0, fakeChatClient.prompts.size)
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
        assertEquals(0, fakeChatClient.prompts.size)
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
        fakeChatClient.response = expectedSummary
        
        // When
        val result = useCase(testEntries)
        
        // Then
        assertTrue(result is SummarizeJournalEntriesResult.Success)
        assertEquals(expectedSummary, (result as SummarizeJournalEntriesResult.Success).summary)
        assertEquals(1, fakeChatClient.prompts.size)
    }

    @Test
    fun `invoke should return SummaryUnavailable when summarizer returns null`() = runTest {
        // Given
        val testEntries = listOf(createTestNote("Test content"))
        mockNetworkMonitor.isAvailable = true
        fakeChatClient.response = null
        
        // When
        val result = useCase(testEntries)
        
        // Then
        assertEquals(SummarizeJournalEntriesResult.SummaryUnavailable, result)
        assertEquals(1, fakeChatClient.prompts.size)
    }

    @Test
    fun `invoke should return SummaryUnavailable when summarizer throws exception`() = runTest {
        // Given
        val testEntries = listOf(createTestNote("Test content"))
        mockNetworkMonitor.isAvailable = true
        fakeChatClient.shouldThrowException = true
        
        // When
        val result = useCase(testEntries)
        
        // Then
        assertEquals(SummarizeJournalEntriesResult.SummaryUnavailable, result)
        assertEquals(1, fakeChatClient.prompts.size)
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
        fakeChatClient.response = "Test summary"
        
        // When
        val result = useCase(testEntries)
        
        // Then
        assertTrue(result is SummarizeJournalEntriesResult.Success)
        assertEquals(1, fakeChatClient.prompts.size)
        
        // Verify that the prompt contains text notes in chronological order
        val prompt = fakeChatClient.prompts.first().last().content
        assertTrue(prompt.contains("Older note"))
        assertTrue(prompt.contains("Newer note"))
        // Image note should not be included in text summary
        assertFalse(prompt.contains("test_image.jpg"))
    }

    @Test
    fun `invoke should generate consistent cache request for same entries`() = runTest {
        // Given
        val testEntries = listOf(
            createTestNote("Content 1"),
            createTestNote("Content 2")
        )
        mockNetworkMonitor.isAvailable = true
        fakeChatClient.response = "Test summary"
        
        // When
        useCase(testEntries)
        useCase(testEntries) // Call twice with same entries
        
        // Then
        assertEquals(2, fakeCache.getCalls.size)
        // Both calls should use the same cache request
        assertEquals(
            fakeCache.getCalls[0],
            fakeCache.getCalls[1]
        )
    }

    @Test
    fun `invoke should handle single text note`() = runTest {
        // Given
        val singleNote = createTestNote("Single note content")
        mockNetworkMonitor.isAvailable = true
        fakeChatClient.response = "Single note summary"
        
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

    private class FakeGenerativeAICache : GenerativeAICache {
        private val store = mutableMapOf<String, GenerativeAICacheEntry>()
        private val keyStrategy = DefaultAICacheKeyStrategy()
        val getCalls = mutableListOf<GenerativeAICacheRequest>()
        val putCalls = mutableListOf<Pair<GenerativeAICacheRequest, String>>()

        override suspend fun getEntry(request: GenerativeAICacheRequest): GenerativeAICacheEntry? {
            getCalls.add(request)
            val key = keyStrategy.createKey(request.toKeyInput())
            return store[key.value]
        }

        override suspend fun putEntry(request: GenerativeAICacheRequest, content: String) {
            putCalls.add(request to content)
            val key = keyStrategy.createKey(request.toKeyInput())
            val now = Clock.System.now()
            val expiresAt = now + request.policy.ttlSeconds.seconds
            store[key.value] = GenerativeAICacheEntry(
                key = key.value,
                content = content,
                lastUpdated = now,
                metadata = GenerativeAICacheEntryMetadata(
                    contentTypeId = request.contentType.id,
                    providerId = request.providerId,
                    model = request.model,
                    promptVersion = request.promptVersion,
                    schemaVersion = request.schemaVersion,
                    templateId = request.templateId,
                    ttlSeconds = request.policy.ttlSeconds,
                    expiresAt = expiresAt,
                    sourceHash = key.sourceHash,
                    debugPrefix = key.debugPrefix,
                    contentBytes = content.encodeToByteArray().size.toLong()
                )
            )
        }

        override suspend fun purge() {
            store.clear()
        }

        private fun GenerativeAICacheRequest.toKeyInput() = AICacheKeyInput(
            contentType = contentType,
            inputText = inputText,
            providerId = providerId,
            model = model,
            promptVersion = promptVersion,
            schemaVersion = schemaVersion,
            templateId = templateId,
            policy = policy
        )
    }

    private class FakeGenerativeAIChatClient : GenerativeAIChatClient {
        override val providerId: String = "fake"
        override val defaultModel: String? = "fake-model"
        val prompts = mutableListOf<List<GenerativeAIChatMessage>>()
        var response: String? = "Default summary"
        var shouldThrowException = false

        override suspend fun submit(request: GenerativeAIRequest): AIResult<GenerativeAIResponse> {
            prompts.add(request.messages)
            if (shouldThrowException) {
                return AIResult.Error(AIError.Unknown, RuntimeException("Summarizer error"))
            }
            return if (response == null) {
                AIResult.Error(AIError.InvalidResponse)
            } else {
                AIResult.Success(GenerativeAIResponse(response ?: ""))
            }
        }
    }

    private class MockNetworkAvailabilityMonitor : NetworkAvailabilityMonitor {
        var isAvailable = true
        private val networkFlow = MutableSharedFlow<NetworkState>(replay = 1)

        override fun isNetworkAvailable(): Boolean = isAvailable

        override fun observeNetwork(): SharedFlow<NetworkState> = networkFlow
    }
}
