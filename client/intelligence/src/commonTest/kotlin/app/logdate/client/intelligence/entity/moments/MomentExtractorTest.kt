package app.logdate.client.intelligence.entity.moments

import app.logdate.client.intelligence.AIResult
import app.logdate.client.intelligence.cache.AICachePolicy
import app.logdate.client.intelligence.cache.GenerativeAICacheContentType
import app.logdate.client.intelligence.cache.GenerativeAICacheRequest
import app.logdate.client.intelligence.fakes.FakeDataUsagePolicy
import app.logdate.client.intelligence.fakes.FakeGenerativeAICache
import app.logdate.client.intelligence.fakes.FakeGenerativeAIChatClient
import app.logdate.client.networking.NetworkAvailabilityMonitor
import app.logdate.client.networking.NetworkState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Validates the [MomentExtractor]'s ability to identify significant time-based events
 * from journal entries using AI.
 *
 * This suite tests the extraction of "moments"—including their labels, estimated time
 * ranges, and associated evidence—while ensuring robust handling of network states,
 * caching, and various AI response qualities.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MomentExtractorTest {
    private val testDispatcher = StandardTestDispatcher()
    private val fakeCache = FakeGenerativeAICache()
    private val fakeAIClient = FakeGenerativeAIChatClient()
    private val fakeNetworkMonitor = TestNetworkAvailabilityMonitor()

    private val fakeDataUsagePolicy = FakeDataUsagePolicy()

    private val momentExtractor =
        MomentExtractor(
            generativeAICache = fakeCache,
            generativeAIChatClient = fakeAIClient,
            networkAvailabilityMonitor = fakeNetworkMonitor,
            dataUsagePolicy = fakeDataUsagePolicy,
            ioDispatcher = testDispatcher,
        )

    private fun setup() {
        fakeCache.clear()
        fakeAIClient.clear()
        fakeNetworkMonitor.setAvailable(true)
    }

    @Test
    fun extractMoments_withValidResponse_parsesCorrectly() =
        runTest(testDispatcher) {
            setup()
            val entriesText = "Entry [abc-123] at 2025-03-15T08:00:00Z: Morning coffee"
            val aiResponse =
                """
                {
                  "moments": [
                    {
                      "label": "Morning coffee",
                      "estimatedStartHour": 8,
                      "estimatedStartMinute": 0,
                      "estimatedEndHour": 9,
                      "estimatedEndMinute": 0,
                      "sourceNoteIds": ["abc-123"],
                      "textFragments": [{"text": "Morning coffee", "sourceNoteId": "abc-123"}],
                      "peopleMentioned": []
                    }
                  ]
                }
                """.trimIndent()

            fakeAIClient.defaultResponse = aiResponse

            val result = momentExtractor.extractMoments("test-doc", entriesText)
            assertTrue(result is AIResult.Success)
            val moments = result.value

            assertEquals(1, moments.size)
            assertEquals("Morning coffee", moments[0].label)
            assertEquals(8, moments[0].estimatedStartHour)
            assertEquals(9, moments[0].estimatedEndHour)
            assertEquals(listOf("abc-123"), moments[0].sourceNoteIds)
            assertEquals(1, moments[0].textFragments.size)
            assertEquals("Morning coffee", moments[0].textFragments[0].text)
        }

    @Test
    fun extractMoments_withMultipleMoments_parsesAll() =
        runTest(testDispatcher) {
            setup()
            val entriesText = "Full day journal dump"
            val aiResponse =
                """
                {
                  "moments": [
                    {
                      "label": "At Blue Bottle",
                      "estimatedStartHour": 9,
                      "estimatedStartMinute": 30,
                      "estimatedEndHour": 10,
                      "estimatedEndMinute": 30,
                      "sourceNoteIds": ["note-1"],
                      "textFragments": [{"text": "Had coffee", "sourceNoteId": "note-1"}],
                      "peopleMentioned": ["Jamie"]
                    },
                    {
                      "label": "Zilker Park",
                      "estimatedStartHour": 14,
                      "estimatedStartMinute": 0,
                      "estimatedEndHour": 16,
                      "estimatedEndMinute": 0,
                      "sourceNoteIds": ["note-1", "note-2"],
                      "textFragments": [],
                      "peopleMentioned": []
                    }
                  ]
                }
                """.trimIndent()

            fakeAIClient.defaultResponse = aiResponse

            val result = momentExtractor.extractMoments("test-doc", entriesText)
            assertTrue(result is AIResult.Success)
            val moments = result.value

            assertEquals(2, moments.size)
            assertEquals("At Blue Bottle", moments[0].label)
            assertEquals(listOf("Jamie"), moments[0].people)
            assertEquals("Zilker Park", moments[1].label)
            assertEquals(listOf("note-1", "note-2"), moments[1].sourceNoteIds)
        }

    @Test
    fun extractMoments_withCachedResponse_returnsCached() =
        runTest(testDispatcher) {
            setup()
            val entriesText = "Cached journal entry"
            val cachedResponse =
                """
                {"moments": [{"label": "Cached moment", "estimatedStartHour": 10, "estimatedStartMinute": 0, "estimatedEndHour": 11, "estimatedEndMinute": 0, "sourceNoteIds": ["x"], "textFragments": [], "peopleMentioned": []}]}
                """.trimIndent()

            val cacheRequest = cacheRequestFor(entriesText)
            fakeCache.setEntry(cacheRequest, cachedResponse)

            val result = momentExtractor.extractMoments("test-doc", entriesText, useCached = true)
            assertTrue(result is AIResult.Success)
            assertTrue(result.fromCache)
            assertEquals("Cached moment", result.value[0].label)

            // AI client should not have been called
            assertEquals(0, fakeAIClient.submissions.size)
        }

    @Test
    fun extractMoments_networkUnavailable_returnsUnavailable() =
        runTest(testDispatcher) {
            setup()
            fakeNetworkMonitor.setAvailable(false)

            val result = momentExtractor.extractMoments("test-doc", "Some entries")
            assertTrue(result is AIResult.Unavailable)
        }

    @Test
    fun extractMoments_aiError_returnsError() =
        runTest(testDispatcher) {
            setup()
            fakeAIClient.shouldThrowError = true

            val result = momentExtractor.extractMoments("test-doc", "Some entries", useCached = false)
            assertTrue(result is AIResult.Error)
        }

    @Test
    fun extractMoments_invalidJson_returnsError() =
        runTest(testDispatcher) {
            setup()
            fakeAIClient.defaultResponse = "not valid json at all"

            val result = momentExtractor.extractMoments("test-doc", "Some entries", useCached = false)
            assertTrue(result is AIResult.Error)
        }

    @Test
    fun extractMoments_emptyMoments_returnsEmptyList() =
        runTest(testDispatcher) {
            setup()
            fakeAIClient.defaultResponse = """{"moments": []}"""

            val result = momentExtractor.extractMoments("test-doc", "Some entries")
            assertTrue(result is AIResult.Success)
            assertTrue(result.value.isEmpty())
        }

    @Test
    fun extractMoments_cachesSuccessfulResponse() =
        runTest(testDispatcher) {
            setup()
            val entriesText = "Journal entry to cache"
            fakeAIClient.defaultResponse =
                """{"moments": [{"label": "Test", "estimatedStartHour": 8, "estimatedStartMinute": 0, "estimatedEndHour": 9, "estimatedEndMinute": 0, "sourceNoteIds": [], "textFragments": [], "peopleMentioned": []}]}"""

            momentExtractor.extractMoments("test-doc", entriesText, useCached = false)

            // Verify response was cached
            assertEquals(1, fakeCache.putEntryCalls.size)
        }

    @Test
    fun extractMoments_sendsSystemPromptWithMomentInstructions() =
        runTest(testDispatcher) {
            setup()
            fakeAIClient.defaultResponse = """{"moments": []}"""

            momentExtractor.extractMoments("test-doc", "Entry content")

            val systemMessage = fakeAIClient.getLastSystemMessage()
            assertTrue(systemMessage != null)
            assertTrue(systemMessage.contains("moment extraction"))
            assertTrue(systemMessage.contains("NOT necessarily when the described event occurred"))
            assertTrue(systemMessage.contains("contextual label"))
        }

    private fun cacheRequestFor(text: String) =
        GenerativeAICacheRequest(
            contentType = GenerativeAICacheContentType.Moments,
            inputText = text,
            providerId = fakeAIClient.providerId,
            model = fakeAIClient.defaultModel,
            promptVersion = "moments-v1",
            schemaVersion = "moments-json-v1",
            templateId = "moment-extractor",
            policy = AICachePolicy(ttlSeconds = 60L * 60L * 24L * 7L),
        )

    /**
     * A test implementation of [NetworkAvailabilityMonitor] for testing.
     */
    private class TestNetworkAvailabilityMonitor(
        private var available: Boolean = true,
    ) : NetworkAvailabilityMonitor {
        override fun isNetworkAvailable(): Boolean = available

        override fun observeNetwork(): SharedFlow<NetworkState> = throw UnsupportedOperationException("Not used in tests")

        fun setAvailable(isAvailable: Boolean) {
            available = isAvailable
        }
    }
}
