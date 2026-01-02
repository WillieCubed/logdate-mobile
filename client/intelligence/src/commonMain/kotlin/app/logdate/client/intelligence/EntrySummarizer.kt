package app.logdate.client.intelligence

import app.logdate.client.intelligence.cache.GenerativeAICache
import app.logdate.client.intelligence.generativeai.GenerativeAIChatClient
import app.logdate.client.intelligence.generativeai.GenerativeAIChatMessage
import app.logdate.client.intelligence.generativeai.GenerativeAIRequest
import app.logdate.client.networking.NetworkAvailabilityMonitor
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext

/**
 * A utility that summarizes journal entries.
 *
 * This currently only supports text entries.
 *
 * TODO: Incorporate other information into summarizes, such as people and places.
 */
class EntrySummarizer(
    private val generativeAICache: GenerativeAICache,
    private val genAIClient: GenerativeAIChatClient,
    private val networkAvailabilityMonitor: NetworkAvailabilityMonitor,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private companion object {
        private const val SYSTEM_PROMPT =
            "You are a helpful journal summarizer. Only speak in the second person to the user, and in the past tense. Do not respond with anything other than a one or two-sentence summary of the following journal entries:"
    }

    /**
     * Summarizes a journal entry.
     *
     * @param summaryId An ID used to cache the response.
     * @param text The text of the journal entry to summarize.
     * @param useCached Whether to use a cached response if available. If false, the response will
     * be generated from scratch.
     */
    suspend fun summarize(
        summaryId: String,
        text: String,
        useCached: Boolean = true
    ): AIResult<String> =
        withContext(ioDispatcher) {
            if (useCached) {
                val cachedResponse = generativeAICache.getEntry(summaryId)
                if (cachedResponse != null) {
                    Napier.d(
                        tag = "EntrySummarizer",
                        message = "Using cached response for $summaryId"
                    )
                    return@withContext AIResult.Success(cachedResponse.content, fromCache = true)
                }
            }
            Napier.d(
                tag = "EntrySummarizer",
                message = "No cached response for $summaryId, generating new response"
            )
            val unavailableReason = networkAvailabilityMonitor.unavailableReason()
            if (unavailableReason != null) {
                return@withContext AIResult.Unavailable(unavailableReason)
            }
            val response = genAIClient.submit(
                GenerativeAIRequest(
                    messages = listOf(
                        GenerativeAIChatMessage("system", SYSTEM_PROMPT),
                        GenerativeAIChatMessage("user", text),
                    )
                )
            )
            when (response) {
                is AIResult.Success -> {
                    Napier.d(tag = "EntrySummarizer", message = "Caching response for entry $summaryId")
                    generativeAICache.putEntry(summaryId, response.value.content)
                    AIResult.Success(response.value.content, fromCache = false)
                }
                is AIResult.Unavailable -> response
                is AIResult.Error -> {
                    Napier.e(
                        tag = "EntrySummarizer",
                        message = "Failed to summarize entry",
                        throwable = response.throwable
                    )
                    response
                }
            }

        }
}
